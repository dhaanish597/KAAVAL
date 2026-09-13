package app.vaakku.npu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.BuiltinNpuAcceleratorProvider
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import java.io.Closeable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Build plan §6.5: the privacy masker, and the app's one real NPU workload.
 *
 * A selfie-segmentation model runs over every page image before it is written
 * into a session's evidence folder, and every pixel the model calls a person is
 * painted out. A benefit illustration gets photographed across a table with an
 * agent's hands, sleeve or face in frame; §6.5's promise is that the receipt
 * contains the document and never the person. §6.4 puts this *before* the
 * write, because once an unmasked file exists on disk, masking is a second file
 * rather than a fix.
 *
 * **Honesty (CLAUDE.md #8).** The ladder is NPU -> GPU -> CPU and each rung is
 * requested *alone*. LiteRT's `Options` accepts a set and resolves it
 * internally, so asking for `(NPU, GPU)` yields a working model and no way to
 * say which one is underneath — exactly the situation where a label saying
 * "NPU" would be a guess. Asked one at a time, a rung either loads or throws,
 * and [AcceleratorReport.accelerator] is the rung that loaded. Note what that
 * does and does not establish: it is which accelerator LiteRT *accepted*, which
 * is honest to print, and it is still not the same as logcat proof of Hexagon
 * dispatch — that is G4's evidence and [AcceleratorReport.proven].
 *
 * **What this cannot do.** It masks people the model recognises. It is not a
 * redactor: it does not find names, account numbers or signatures, and nothing
 * in the app should imply it does.
 */
class PersonMasker private constructor(
    private val model: CompiledModel,
    private val environment: Environment,
    val report: AcceleratorReport,
    private val dispatcher: CoroutineDispatcher,
) : Closeable {

    // Allocated once and reused for every page. On the NPU these are not
    // ordinary heap arrays — they can be hardware buffers the driver mapped —
    // so churning one per page would add an allocation and a free to a path
    // that §11.5 budgets at 10 ms.
    private val inputBuffers = model.createInputBuffers()
    private val outputBuffers = model.createOutputBuffers()

    /** Scratch for the 256x256x3 input; reused so a page costs no new float array. */
    private val inputScratch = FloatArray(MaskMath.EDGE * MaskMath.EDGE * 3)

    /** Scratch for the resized pixels; `Bitmap.getPixels` fills it. */
    private val pixelScratch = IntArray(MaskMath.EDGE * MaskMath.EDGE)

    /**
     * Returns a masked copy of [page], leaving [page] untouched.
     *
     * A copy rather than an in-place edit because `PageScanner` recognises text
     * on the unmasked capture (§6.4) and only then masks it; writing through the
     * same bitmap would mask the image OCR is still holding. The caller owns
     * both — see `PageScanner.scan`, which recycles them.
     *
     * On any failure this returns `null` rather than the unmasked bitmap. An
     * unmasked page returned from a masker is the one outcome that could put a
     * face in the receipt while the screen says a mask ran; the caller decides
     * what to do with nothing, and cannot mistake it for a masked page.
     */
    suspend fun mask(page: Bitmap): MaskedPage? = withContext(dispatcher) {
        try {
            val startedAt = SystemClock.elapsedRealtimeNanos()

            // Stretched to the model's square input, not letterboxed: the
            // distortion costs boundary accuracy on a mask (which
            // MaskMath.dilate already pays for) where letterboxing would spend
            // real model resolution on grey bars.
            val scaled = Bitmap.createScaledBitmap(page, MaskMath.EDGE, MaskMath.EDGE, true)
            try {
                scaled.getPixels(pixelScratch, 0, MaskMath.EDGE, 0, 0, MaskMath.EDGE, MaskMath.EDGE)
            } finally {
                // createScaledBitmap can return the receiver itself when the
                // size already matches; recycling that would destroy the
                // caller's page mid-scan.
                if (scaled !== page && !scaled.isRecycled) scaled.recycle()
            }

            // The [-1, 1] normalisation the model was trained with: (v - 127.5) / 127.5.
            for (i in pixelScratch.indices) {
                val pixel = pixelScratch[i]
                val base = i * 3
                inputScratch[base] = (Color.red(pixel) - INPUT_MEAN) / INPUT_STD
                inputScratch[base + 1] = (Color.green(pixel) - INPUT_MEAN) / INPUT_STD
                inputScratch[base + 2] = (Color.blue(pixel) - INPUT_MEAN) / INPUT_STD
            }

            val inferenceStartedAt = SystemClock.elapsedRealtimeNanos()
            inputBuffers[0].writeFloat(inputScratch)
            model.run(inputBuffers, outputBuffers)
            val scores = outputBuffers[0].readFloat()
            val inferenceNanos = SystemClock.elapsedRealtimeNanos() - inferenceStartedAt

            val raw = MaskMath.personMask(scores)
            val grown = MaskMath.dilate(raw)
            val masked = paint(page, grown)

            MaskedPage(
                bitmap = masked,
                coverage = MaskMath.personCoverage(grown),
                inferenceMs = inferenceNanos / 1_000_000.0,
                totalMs = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000.0,
                accelerator = report.accelerator,
            )
        } catch (e: Exception) {
            // Caught broadly on purpose: this sits between a buyer's tap and a
            // file on disk, and no failure of a segmentation model justifies
            // losing the page they just scanned. The caller falls back to not
            // writing an image at all, which is the safe direction.
            Log.w(TAG, "Masking failed on ${report.accelerator.label}: ${e.message}", e)
            null
        }
    }

    /**
     * Paints every masked pixel out of a copy of [page].
     *
     * Solid fill, not blur: a blur is a reversible-looking transform that still
     * carries the shape of a face, and a receipt that goes to a grievance
     * office should not invite anyone to try. [FILL_COLOR] is the app's paper
     * tone, so a masked region reads as part of the page rather than as a
     * redaction bar — and it is not a status colour (CLAUDE.md #9).
     */
    private fun paint(page: Bitmap, mask: BooleanArray): Bitmap {
        val width = page.width
        val height = page.height
        val out = page.copy(Bitmap.Config.ARGB_8888, /* isMutable = */ true)

        // Row at a time. A full-page IntArray would be another width*height*4
        // bytes beside two bitmaps that are already tens of MB each.
        val row = IntArray(width)
        for (y in 0 until height) {
            out.getPixels(row, 0, width, 0, y, width, 1)
            var changed = false
            for (x in 0 until width) {
                if (mask[MaskMath.maskIndexFor(x, y, width, height)]) {
                    row[x] = FILL_COLOR
                    changed = true
                }
            }
            if (changed) out.setPixels(row, 0, width, 0, y, width, 1)
        }
        return out
    }

    override fun close() {
        // Order matters: buffers may be views onto memory the model owns, and
        // the environment outlives both.
        runCatching { inputBuffers.forEach { it.close() } }
        runCatching { outputBuffers.forEach { it.close() } }
        runCatching { model.close() }
        runCatching { environment.close() }
    }

    /** A masked page and what it cost. */
    data class MaskedPage(
        /** The masked copy. The caller owns it and must recycle it. */
        val bitmap: Bitmap,
        /** Fraction of the frame painted out, 0.0..1.0. */
        val coverage: Double,
        /** `model.run` alone, in milliseconds — the number §11.5 budgets at 10 ms. */
        val inferenceMs: Double,
        /** Scale, normalise, infer and paint, in milliseconds. */
        val totalMs: Double,
        val accelerator: MaskAccelerator,
    )

    companion object {
        private const val TAG = "VaakkuNpu"

        /** Asset path of the model inside the APK. */
        const val MODEL_ASSET = "npu/selfie_multiclass_256x256.tflite"

        /** The training normalisation: (value - 127.5) / 127.5, giving [-1, 1]. */
        private const val INPUT_MEAN = 127.5f
        private const val INPUT_STD = 127.5f

        /** Paper `#FAF7F0` — §6.6's page tone, and not a status colour. */
        private const val FILL_COLOR = 0xFFFAF7F0.toInt()

        /**
         * Walks NPU -> GPU -> CPU and returns the first rung that loads.
         *
         * Returns `null` only if all three fail, which would mean LiteRT cannot
         * run this model on this phone at all.
         *
         * The dispatcher is single-threaded and kept for the masker's whole
         * life: a `CompiledModel` is a native handle with no concurrency
         * guarantee, and the NPU path in particular holds a driver context that
         * must be created and used from one thread.
         */
        fun create(context: Context): PersonMasker? {
            val dispatcher = Dispatchers.IO.limitedParallelism(1, "VaakkuMask")
            val provider = BuiltinNpuAcceleratorProvider(context)

            // Asked before any attempt, because these two answers are what
            // separate "this chip has no NPU" from "this chip has one and the
            // runtime libraries are not in the APK" — identical-looking
            // fallbacks with completely different fixes.
            val deviceSupportsNpu = runCatching { provider.isDeviceSupported() }.getOrDefault(false)
            val npuLibraryReady = runCatching { provider.isLibraryReady() }.getOrDefault(false)
            Log.i(
                TAG,
                "NPU provider: deviceSupported=$deviceSupportsNpu libraryReady=$npuLibraryReady " +
                    "soc=${socDescription()} libDir=${runCatching { provider.getLibraryDir() }.getOrNull()}",
            )

            val refusals = mutableListOf<AcceleratorReport.Refusal>()
            for (rung in MaskAccelerator.entries) {
                var environment: Environment? = null
                try {
                    environment = Environment.create(provider)
                    val available = runCatching {
                        environment.getAvailableAccelerators().map { it.name }
                    }.getOrDefault(emptyList())

                    val model = CompiledModel.create(
                        context.assets,
                        MODEL_ASSET,
                        optionsFor(rung),
                        environment,
                    )
                    Log.i(TAG, "Mask model loaded on ${rung.label}; environment reports $available")
                    return PersonMasker(
                        model = model,
                        environment = environment,
                        report = AcceleratorReport(
                            accelerator = rung,
                            refusals = refusals.toList(),
                            deviceSupportsNpu = deviceSupportsNpu,
                            npuLibraryReady = npuLibraryReady,
                            environmentAccelerators = available,
                        ),
                        dispatcher = dispatcher,
                    )
                } catch (e: Exception) {
                    // Expected, not exceptional: a phone without the Qualcomm
                    // runtime is supposed to land here and continue to GPU.
                    val reason = e.message?.takeIf { it.isNotBlank() } ?: e::class.java.simpleName
                    Log.i(TAG, "${rung.label} refused the mask model: $reason")
                    refusals += AcceleratorReport.Refusal(rung, reason)
                    runCatching { environment?.close() }
                }
            }
            Log.w(TAG, "No accelerator would load the mask model: $refusals")
            return null
        }

        /**
         * Options for one rung, and one rung only.
         *
         * [CompiledModel.Options] takes a vararg of accelerators; passing more
         * than one here would hand the choice back to LiteRT and take the label
         * with it.
         */
        private fun optionsFor(accelerator: MaskAccelerator): CompiledModel.Options =
            when (accelerator) {
                MaskAccelerator.NPU -> CompiledModel.Options(Accelerator.NPU).apply {
                    // The sample's setting. HIGH_PERFORMANCE rather than BURST:
                    // the masker runs once per scan for a few hundred
                    // milliseconds, not in a sustained loop, and §11.5 budgets
                    // thermal at <= MODERATE after fifteen minutes.
                    qualcommOptions = CompiledModel.QualcommOptions(
                        htpPerformanceMode =
                            CompiledModel.QualcommOptions.HtpPerformanceMode.HIGH_PERFORMANCE,
                    )
                }
                MaskAccelerator.GPU -> CompiledModel.Options(Accelerator.GPU)
                MaskAccelerator.CPU -> CompiledModel.Options(Accelerator.CPU)
            }

        /** `Build.SOC_MANUFACTURER`/`SOC_MODEL` — what LiteRT's own check reads (API 31+). */
        private fun socDescription(): String =
            "${Build.SOC_MANUFACTURER}/${Build.SOC_MODEL}"
    }
}
