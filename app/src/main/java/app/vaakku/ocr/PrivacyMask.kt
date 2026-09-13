package app.vaakku.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import app.vaakku.npu.MaskAccelerator
import app.vaakku.npu.PersonMasker
import java.io.Closeable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The seat P4's NPU privacy masker sits in — build plan §6.4 and §6.5.
 *
 * §6.4 requires the page image saved into a session's evidence folder to be
 * masked **before** it is written, because §6.5's whole promise is that the
 * receipt contains the document and never a person: a benefit illustration is
 * photographed across a table, and an agent's hands, sleeve or face can easily
 * be in frame. Once the evidence file exists on disk, an unmasked person is in
 * it permanently — masking afterwards would be a second file, not a fix.
 *
 * **The invariant this class exists to hold: the file on disk always matches
 * what the screen said.** That is what makes the three outcomes different, and
 * it is a stricter rule than "always mask", because masking can be unavailable:
 *
 * - [Outcome.Masked] — the masker ran. The masked copy is written.
 * - [Outcome.Unmasked] — no masker could be built on this phone at all. The
 *   original is written, and the screen says plainly that no mask is running,
 *   exactly as it did before P4. Honest, and not a regression.
 * - [Outcome.Withheld] — a masker exists, so the screen says masking is on, but
 *   *this page* failed. Nothing is written. Writing the unmasked page here would
 *   put a face in the packet while the screen claimed otherwise, which is the
 *   one outcome no amount of later explanation repairs.
 *
 * Losing a page image costs corroboration; the clause text and its provenance
 * survive, because §6.4 runs OCR on the capture in memory before any of this.
 * That asymmetry is why withholding is the safe direction (CLAUDE.md #2).
 *
 * **Lifetime.** This owns a native model handle, a driver context and a thread,
 * so it is built once per scan sheet and [close]d with it — never per page. It
 * is cheap to construct and expensive to [warmUp]; see that method for why the
 * two are separate.
 */
class PrivacyMask(context: Context) : Closeable {

    // The application context, not the Activity's: this object outlives a
    // configuration change, and holding an Activity across one leaks it.
    private val appContext = context.applicationContext

    private val mutex = Mutex()
    private var built = false
    private var masker: PersonMasker? = null
    private var unavailableReason: String? = null

    /**
     * What the screen may say about masking. Meaningful only after [warmUp].
     *
     * Read from the composition thread, written from a coroutine, so it is
     * `@Volatile` rather than protected by [mutex] — the screen must never block
     * on a model compile to draw a line of text.
     */
    @Volatile
    var availability: Availability = Availability.Unknown
        private set

    /**
     * Builds the masker, if one can be built. Safe to call more than once.
     *
     * Separate from the constructor because this is the on-device JIT compile:
     * it is measured in seconds on the NPU rung, and it must not run on the
     * composition thread. Called when the scan sheet opens, so it overlaps the
     * seconds the human spends aiming the camera; if the shutter beats it, the
     * first [apply] simply waits on the same mutex.
     */
    suspend fun warmUp() {
        ensureBuilt()
    }

    private suspend fun ensureBuilt(): PersonMasker? = mutex.withLock {
        if (!built) {
            built = true
            val created = withContext(Dispatchers.Default) {
                runCatching { PersonMasker.create(appContext) }.getOrNull()
            }
            masker = created
            availability = if (created != null) {
                Availability.Active(created.report.accelerator)
            } else {
                unavailableReason = NO_ACCELERATOR
                Availability.Unavailable(NO_ACCELERATOR)
            }
        }
        masker
    }

    /**
     * Returns what may be written to the evidence folder for [page].
     *
     * [page] is never modified and never recycled here — `PageScanner` owns it
     * and recycles it, along with any bitmap this returns.
     *
     * [label] identifies the page in the log line below; `PageScanner` passes
     * its frame id, so a logcat line can be matched to a `page_<n>.jpg` on disk.
     */
    suspend fun apply(page: Bitmap, label: String): Outcome {
        val active = ensureBuilt()
            ?: return Outcome.Unmasked(page, unavailableReason ?: NO_ACCELERATOR)
                .also { log(label, page, "not-masked: ${it.reason}") }

        val masked = active.mask(page)
            ?: return Outcome.Withheld(MASK_FAILED)
                .also { log(label, page, "withheld: ${it.reason}") }

        log(
            label,
            page,
            "masked coverage=%.4f on %s (inference %.1f ms, total %.1f ms)".format(
                masked.coverage,
                masked.accelerator.label,
                masked.inferenceMs,
                masked.totalMs,
            ),
        )

        return Outcome.Masked(
            page = masked.bitmap,
            accelerator = masked.accelerator,
            inferenceMs = masked.inferenceMs,
            totalMs = masked.totalMs,
            coverage = masked.coverage,
        )
    }

    /**
     * Records what masking did to one page, so it is still answerable afterwards.
     *
     * Open issue 28: coverage was computed, shown for a moment and then lost, so
     * a page masked at 40% and a page masked at 0% were indistinguishable once
     * the screen moved on — which is exactly the question open issue 22 asks. The
     * receipt is the wrong place for it (`DocumentScanCompleted` is a bare marker
     * by §6.4/§7.1, and adding a field changes the schema and the hash chain), so
     * this goes to logcat under the same tag as "Mask model loaded on …": one
     * grep then tells the whole masking story of a session.
     *
     * The page's pixel size travels with it because a coverage figure alone
     * cannot distinguish a mask that landed on the person from one that landed
     * beside them — the geometry is what open issue 22 needs, and `width×height`
     * is the input to [app.vaakku.npu.MaskMath.maskIndexFor]'s mapping.
     *
     * Diagnostics only. Nothing here reaches a user-facing surface, and it says
     * what was painted, never anything about the person or the document
     * (CLAUDE.md #1).
     */
    private fun log(label: String, page: Bitmap, what: String) {
        Log.i(TAG, "$label ${page.width}x${page.height}: $what")
    }

    override fun close() {
        // Not under the mutex: close() is called from onDispose, which is not a
        // coroutine, and PersonMasker.close is itself failure-tolerant.
        runCatching { masker?.close() }
        masker = null
    }

    /** Whether a masker could be built on this phone, and on what. */
    sealed interface Availability {
        /** [warmUp] has not finished. The screen shows nothing about masking yet. */
        data object Unknown : Availability

        /**
         * A masker is running on [accelerator].
         *
         * Naming the accelerator is honest — it is the rung LiteRT accepted —
         * and is still not proof of Hexagon dispatch, which is a logcat line and
         * G4's evidence (CLAUDE.md #8).
         */
        data class Active(val accelerator: MaskAccelerator) : Availability

        /** No accelerator would load the model. [reason] is for the debug screen. */
        data class Unavailable(val reason: String) : Availability
    }

    /**
     * What happened to one page, and the bitmap (if any) that may be written.
     *
     * Carries a live bitmap, so it does not outlive `PageScanner.scan`. The
     * facts worth keeping afterwards are in [summary], which holds no bitmap and
     * is what travels to the screen.
     */
    sealed interface Outcome {
        /** The bitmap safe to write, or `null` when nothing may be written. */
        val safeToWrite: Bitmap?
        val summary: MaskSummary

        /** The masker ran; [page] is a new, masked bitmap. */
        data class Masked(
            val page: Bitmap,
            val accelerator: MaskAccelerator,
            val inferenceMs: Double,
            val totalMs: Double,
            val coverage: Double,
        ) : Outcome {
            override val safeToWrite: Bitmap get() = page
            override val summary: MaskSummary
                get() = MaskSummary.Ran(accelerator, inferenceMs, totalMs, coverage)
        }

        /**
         * No masker exists on this phone; [page] is the caller's own bitmap,
         * unchanged. Written only because the screen says no mask is running.
         */
        data class Unmasked(val page: Bitmap, val reason: String) : Outcome {
            override val safeToWrite: Bitmap get() = page
            override val summary: MaskSummary get() = MaskSummary.NotRunning(reason)
        }

        /** A masker exists and this page failed it. Nothing may be written. */
        data class Withheld(val reason: String) : Outcome {
            override val safeToWrite: Bitmap? get() = null
            override val summary: MaskSummary get() = MaskSummary.Withheld(reason)
        }
    }

    /**
     * The bitmap-free record of what masking did to one page.
     *
     * Separate from [Outcome] so it can be held in `ScannedPage` and read by the
     * screen long after the bitmaps it describes have been recycled.
     */
    sealed interface MaskSummary {
        data class Ran(
            val accelerator: MaskAccelerator,
            val inferenceMs: Double,
            val totalMs: Double,
            val coverage: Double,
        ) : MaskSummary

        data class NotRunning(val reason: String) : MaskSummary

        data class Withheld(val reason: String) : MaskSummary
    }

    companion object {
        /** Shared with `PersonMasker`, so one grep covers the whole mask path. */
        private const val TAG = "VaakkuNpu"

        /**
         * Diagnostics, not user copy. These reach the debug screen and logcat;
         * what the buyer reads is a Tamil string chosen by the screen from the
         * [MaskSummary] variant, never one of these.
         */
        private const val NO_ACCELERATOR =
            "no accelerator would load the segmentation model"
        private const val MASK_FAILED =
            "the masker failed on this page"
    }
}
