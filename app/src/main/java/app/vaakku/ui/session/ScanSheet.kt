package app.vaakku.ui.session

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vaakku.R
import app.vaakku.domain.copy.ValuePhrase
import app.vaakku.domain.model.ClaimType
import app.vaakku.domain.model.LedgerEntry
import app.vaakku.domain.model.Observation
import app.vaakku.ocr.DocumentCamera
import app.vaakku.ocr.MlKitTextRecognizer
import app.vaakku.ocr.PageScanner
import app.vaakku.ocr.PrivacyMask
import app.vaakku.session.SessionEvidence
import app.vaakku.session.SessionRuntime
import app.vaakku.ui.claimTypeLabel
import app.vaakku.ui.copyText
import app.vaakku.ui.localized
import app.vaakku.ui.theme.VaakkuTheme
import app.vaakku.ui.theme.VoiceDocument
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The scan sheet — build plan §6.6 screen 3.
 *
 * Camera preview, **Scan**, **ஸ்கேன் முடிந்தது**, and the clauses read so far.
 * Every capture goes CameraX → ML Kit → `RowAssembler` + `WrittenExtractor` →
 * [SessionRuntime.documentPage], so the written half lands in the *session's*
 * reconciler and the Delta Card behind this sheet updates while the sheet is
 * still open.
 *
 * ### The clause list is a reading, not a comparison
 *
 * It names each claim type and the value read for it, in the buyer's own
 * language, and nothing else: no MATCHES / NOT_IN_DOCUMENT / DIFFERS, no colour,
 * nothing about any person. A comparison needs the spoken half, and
 * `Reconciler` is the only thing in the app that ever performs one (§5.7). The
 * `scan_reading_note` line says this on screen so the list cannot be misread as
 * a finding.
 *
 * ### One scan spans several pages
 *
 * No single page of a benefit illustration carries every clause — in the prop
 * document, printed pages 5 and 6 together carry all five. Each capture is
 * extracted on its own and the observations accumulate in the reconciler.
 * **ஸ்கேன் முடிந்தது** is what sends `DocumentScanCompleted`, and that event is
 * the only thing that lets the reconciler ever report a claim as absent from the
 * document (§5.7 rule 4) — until it arrives, an unscanned claim is PENDING and
 * silent, which is the correct default (CLAUDE.md #2).
 *
 * This screen supersedes the Dev-menu `DocumentScanScreen`, which kept a
 * *local* reconciler because P3 had no session to reconcile into. That one stays
 * where it is: it can start a scan with no session running, which is exactly what
 * is wanted for measuring OCR on a bench.
 *
 * @param onClose leave the sheet. Scanned pages are already in the session, so
 *   closing loses nothing — it is not a cancel.
 */
@Composable
fun ScanSheet(onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = VaakkuTheme.colors
    val type = VaakkuTheme.type
    val space = VaakkuTheme.space

    val session by SessionRuntime.state.collectAsStateWithLifecycle()

    // Back leaves the sheet rather than the session. Registered here, after the
    // activity's own handler, so this one wins while the sheet is open.
    BackHandler(onBack = onClose)

    // This sheet's own scope, NOT rememberCoroutineScope(). The composition scope
    // dies the instant the sheet closes, which resumes our coroutine but does not
    // stop ML Kit's native recognition — and the teardown below has to be able to
    // wait for that recognition to land before it closes the detector.
    val scanScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    val camera = remember { DocumentCamera() }

    // One native recognizer for the lifetime of the sheet, closed in onDispose.
    // Building one per scan would pay the load cost on every tap and make the
    // "OCR ms" figure describe the load rather than the recognition.
    val recognizer = remember { MlKitTextRecognizer() }

    // The evidence folder is keyed to the *session* id, so page images and crops
    // land in the same folder the receipt will be built from (§7.3). Falling back
    // to a fresh id keeps the sheet usable if it is somehow opened with no
    // session — the pages still get read, they just are not part of a receipt.
    val evidence = remember(session.sessionId) {
        SessionEvidence.forSession(
            context,
            session.sessionId.ifEmpty { SessionEvidence.newSessionId(context) },
        )
    }
    // One privacy masker for the lifetime of the sheet, for the same reason as
    // the recognizer above and more so: building it compiles the segmentation
    // model for the accelerator it lands on, which on the NPU rung is seconds.
    // Cheap to construct, expensive to warm; warmUp() below does the expensive
    // half off the composition thread while the human is still aiming.
    val privacyMask = remember { PrivacyMask(context) }
    val scanner = remember(evidence, privacyMask) { PageScanner(recognizer, evidence, privacyMask) }

    // PrivacyMask.availability is a plain @Volatile field, not Compose state —
    // it is read by PageScanner from a coroutine and must not drag the
    // composition into the model compile. So the screen mirrors it once, here,
    // after warmUp() has settled the answer.
    var maskAvailability by remember {
        mutableStateOf<PrivacyMask.Availability>(PrivacyMask.Availability.Unknown)
    }
    var lastMask by remember { mutableStateOf<PrivacyMask.MaskSummary?>(null) }

    LaunchedEffect(privacyMask) {
        privacyMask.warmUp()
        maskAvailability = privacyMask.availability
    }

    val previewView = remember {
        PreviewView(context).apply {
            // COMPATIBLE (TextureView) rather than the default PERFORMANCE
            // (SurfaceView): this preview sits inside a scrolling column, and a
            // SurfaceView punches its own hole through the window, which does not
            // move when the column scrolls.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    var bound by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var lastPageClauses by remember { mutableIntStateOf(-1) }
    var failure by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { result -> granted = result }

    LaunchedEffect(granted) {
        if (!granted || bound) return@LaunchedEffect
        runCatching { camera.bind(context, lifecycleOwner, previewView.surfaceProvider) }
            .onSuccess { bound = true }
            .onFailure { t -> failure = "${t.javaClass.simpleName}: ${t.message}" }
    }

    DisposableEffect(Unit) {
        onDispose {
            // The camera goes first and goes immediately: unbind() keeps its
            // executor alive until any outstanding capture callback has been
            // delivered, so this cannot reject one.
            camera.unbind()

            // The recognizer cannot. Cancelling the scan job would resume our
            // coroutine at once while ML Kit's process() kept running against the
            // detector — closing it there is precisely the close-under-an-
            // in-flight-recognition case. So an in-flight scan is left to finish
            // and the close rides on its completion. ML Kit's task always
            // completes, with a result or an error.
            //
            // The privacy masker closes on the same signal and for the same
            // reason: it is a native model handle plus a driver context, and an
            // in-flight scan may be inside model.run() right now.
            val running = job
            if (running == null || running.isCompleted) {
                recognizer.close()
                privacyMask.close()
                scanScope.cancel()
            } else {
                running.invokeOnCompletion {
                    recognizer.close()
                    privacyMask.close()
                    scanScope.cancel()
                }
            }
        }
    }

    fun scan() {
        if (scanning || !bound) return
        scanning = true
        failure = null
        job = scanScope.launch {
            try {
                val bitmap = camera.capturePage()
                // Off the main thread: recognition and extraction on Default, and
                // PageScanner moves its own JPEG writes to IO.
                val page = try {
                    withContext(Dispatchers.Default) {
                        scanner.scan(bitmap, SessionRuntime.state.value.pageCount + 1)
                    }
                } catch (t: Throwable) {
                    // scan() releases the bitmap on all of its own paths; this
                    // covers the gap between the capture returning and scan()
                    // taking ownership, where a cancellation would otherwise
                    // strand tens of megabytes.
                    if (!bitmap.isRecycled) bitmap.recycle()
                    throw t
                }
                SessionRuntime.documentPage(page.observations, page.ocrElapsedMs)
                lastPageClauses = page.observations.size
                lastMask = page.maskSummary
            } catch (c: CancellationException) {
                // Rethrown: swallowing it would leave the parent scope believing
                // this child is still alive.
                throw c
            } catch (t: Throwable) {
                // Not rethrown. A refused capture or an unreadable page is an
                // ordinary outcome across a table; the sheet has to stay open and
                // say which it was.
                failure = "${t.javaClass.simpleName}: ${t.message}"
            } finally {
                scanning = false
            }
        }
    }

    val insets = WindowInsets.safeDrawing.asPaddingValues()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.paper)
            .padding(
                top = insets.calculateTopPadding(),
                bottom = insets.calculateBottomPadding(),
            ),
    ) {
        // --- Header: the one action that is not in the bottom third, because
        //     "leave this sheet" is navigation rather than a session control. ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = space.gutter)
                // Min, not fixed: "ஆவணத்தை ஸ்கேன் செய்" wraps to two lines and a
                // fixed 56.dp clipped the second one on the phone. A header that
                // grows costs a few pixels; a header that truncates mid-word is
                // the app failing at the one thing it claims to do well.
                .heightIn(min = HEADER_MIN_HEIGHT)
                .padding(vertical = space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = localized(R.string.session_scan_document, R.string.session_scan_document_en),
                style = type.title,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) {
                Text(
                    text = localized(R.string.action_close, R.string.action_close_en),
                    style = type.label,
                    color = colors.ink,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.gutter),
        ) {
            if (granted) {
                // The box is the STREAM'S OWN ASPECT RATIO, and that is the whole
                // point of it.
                //
                // This was `fillMaxWidth().height(300.dp)`. PreviewView's default
                // FILL_CENTER scales a 3:4 portrait stream to match the width, so
                // on the phone (1290 px wide, 1125 px tall box) the surface came
                // out 1290/0.75 = 1720 px tall, centred: 150…1870 against a box
                // ending at 1571. Measured on a screencap, the overflow was ~298
                // px of live camera image painting over the aim text and the
                // CLAUDE.md #8 note about pages not being masked yet — the one
                // sentence on this screen that must be readable. Compose layout
                // bounds do not clip a child View's drawing, so `height` alone
                // never contained it.
                //
                // Fixing that by cropping the preview instead would be worse than
                // the overlap. FILL_CENTER shows the middle ~75% of the frame,
                // while ImageCapture saves all of it (4:3, set in DocumentCamera)
                // — so the buyer would be exporting a quarter of a photograph
                // they were never shown. Matching the box to the stream means the
                // border is an honest viewfinder: what it frames is what gets
                // written to page_<n>.jpg. Both use cases are 4:3, so there is
                // one ratio to match, not two.
                //
                // The privacy mask does not soften this. It paints out people,
                // not documents, so everything outside the preview is still
                // saved — it is simply saved with any person in it covered.
                //
                // clipToBounds is belt and braces for the day a device hands back
                // a stream at some other ratio: it bounds the damage to the box
                // rather than to the note below it. It comes before `border` so
                // the hairline itself is not clipped.
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .height(PREVIEW_HEIGHT)
                        .aspectRatio(PREVIEW_ASPECT)
                        .clipToBounds()
                        .border(space.hairline, colors.rule),
                )
                Text(
                    text = localized(R.string.scan_aim, R.string.scan_aim_en),
                    style = type.caption,
                    color = colors.inkSoft,
                    modifier = Modifier.padding(top = space.sm),
                )
            } else {
                Text(
                    text = localized(R.string.scan_camera_refused, R.string.scan_camera_refused_en),
                    style = type.bodyLg,
                    color = colors.ink,
                    modifier = Modifier.padding(vertical = space.base),
                )
                Button(
                    onClick = { cameraPermission.launch(Manifest.permission.CAMERA) },
                    shape = RoundedCornerShape(space.radiusButton),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.ink,
                        contentColor = colors.onInk,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = localized(R.string.scan_grant_camera, R.string.scan_grant_camera_en),
                        style = type.label,
                    )
                }
            }

            // CLAUDE.md #8, said to the person holding the phone: exactly which
            // of the three masking states is true right now, and nothing else.
            // The withheld line wins while it applies, because "this page was
            // not saved" is news and "the mask is running" is not.
            Text(
                text = when {
                    lastMask is PrivacyMask.MaskSummary.Withheld ->
                        localized(R.string.scan_mask_withheld, R.string.scan_mask_withheld_en)
                    maskAvailability is PrivacyMask.Availability.Active ->
                        localized(R.string.scan_mask_active, R.string.scan_mask_active_en)
                    maskAvailability is PrivacyMask.Availability.Unavailable ->
                        localized(R.string.scan_mask_note, R.string.scan_mask_note_en)
                    // Unknown: warmUp() has not finished. Saying nothing is the
                    // only honest option — the mask has neither run nor failed.
                    else -> ""
                },
                style = type.caption,
                color = colors.inkFaint,
                modifier = Modifier.padding(top = space.md),
            )

            // §6.6 screen 3: "mask 7.9 ms · NPU". Shown only after a page that
            // actually ran — never after a withheld one, where the number would
            // belong to an earlier page while the line above it talks about this
            // one.
            //
            // The accelerator name is which rung LiteRT accepted, which is what
            // §6.5's honesty rule asks the label to show. It is not a claim of
            // proof: that is a logcat line and G4's evidence, and nothing here
            // calls it verified (CLAUDE.md #8).
            //
            // Untranslated, and deliberately: this is a measurement, in the
            // Latin-digit millisecond-and-accelerator form the plan specifies.
            // A Tamil sentence around it would make a diagnostic read like a
            // promise to the buyer, and the promise is the line above.
            //
            // totalMs, not inferenceMs. Inference alone is the smaller and more
            // flattering number — it leaves out scaling and painting a
            // full-resolution page, which happen on the CPU whichever rung ran
            // the model. What this label is for is the time the person actually
            // waited, and quoting the part that makes the accelerator look good
            // is the kind of number CLAUDE.md #8 exists to stop. The split is in
            // the benchmark CSV, where a reader is equipped for it.
            val ran = lastMask as? PrivacyMask.MaskSummary.Ran
            if (ran != null) {
                Text(
                    text = String.format(Locale.ROOT, "mask %.1f ms · %s", ran.totalMs, ran.accelerator.label),
                    style = type.mono,
                    color = colors.inkFaint,
                    modifier = Modifier.padding(top = space.xs),
                )
            }

            if (failure != null) {
                Spacer(Modifier.height(space.md))
                Text(
                    text = localized(R.string.scan_failed, R.string.scan_failed_en),
                    style = type.label,
                    color = colors.ink,
                )
                Text(
                    text = failure.orEmpty(),
                    style = type.mono,
                    color = colors.inkFaint,
                )
            }

            Spacer(Modifier.height(space.base))
            HorizontalDivider(color = colors.rule)

            // --- What the document says, as read. Never a comparison. ---
            Text(
                text = localized(R.string.scan_clauses_heading, R.string.scan_clauses_heading_en),
                style = type.caption,
                color = colors.inkSoft,
                modifier = Modifier.padding(top = space.base),
            )
            Text(
                text = localized(R.string.scan_counts, R.string.scan_counts_en, session.pageCount, session.documentCount),
                style = type.monoValue,
                color = colors.ink,
                modifier = Modifier.padding(top = space.xs),
            )

            val written = writtenByType(session.ledger)
            if (written.isEmpty()) {
                Text(
                    text = if (lastPageClauses == 0) {
                        localized(R.string.scan_reading_note, R.string.scan_reading_note_en)
                    } else {
                        localized(R.string.scan_nothing_yet, R.string.scan_nothing_yet_en)
                    },
                    style = type.body,
                    color = colors.inkFaint,
                    modifier = Modifier.padding(top = space.md),
                )
            } else {
                Spacer(Modifier.height(space.sm))
                written.forEach { (claimType, observation) -> ClauseRow(claimType, observation) }
                Text(
                    text = localized(R.string.scan_reading_note, R.string.scan_reading_note_en),
                    style = type.caption,
                    color = colors.inkFaint,
                    modifier = Modifier.padding(top = space.md),
                )
            }

            if (session.scanCompleted) {
                Text(
                    text = localized(R.string.scan_done_note, R.string.scan_done_note_en),
                    style = type.caption,
                    color = colors.inkSoft,
                    modifier = Modifier.padding(top = space.md),
                )
            }

            Spacer(Modifier.height(space.xl))
        }

        // --- Bottom third: every session control lives here (§6.6). ---
        HorizontalDivider(color = colors.rule)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = space.gutter, vertical = space.base),
            horizontalArrangement = Arrangement.spacedBy(space.md),
        ) {
            Button(
                onClick = ::scan,
                enabled = bound && !scanning,
                shape = RoundedCornerShape(space.radiusButton),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.ink,
                    contentColor = colors.onInk,
                    disabledContainerColor = colors.rule,
                    disabledContentColor = colors.inkFaint,
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(space.touchTarget + space.sm),
            ) {
                Text(
                    text = if (scanning) {
                        localized(R.string.scan_scanning, R.string.scan_scanning_en)
                    } else {
                        localized(R.string.scan_button, R.string.scan_button_en)
                    },
                    style = type.label,
                )
            }
            OutlinedButton(
                onClick = { SessionRuntime.doneScanning() },
                // Enabled once at least one page is in, and it stays enabled after
                // a first tap: scanning another page and closing the scan again is
                // an ordinary thing to do, and DocumentScanCompleted is idempotent.
                enabled = session.pageCount > 0 && !scanning,
                shape = RoundedCornerShape(space.radiusButton),
                modifier = Modifier
                    .weight(1f)
                    .height(space.touchTarget + space.sm),
            ) {
                Text(
                    text = localized(R.string.session_done_scanning, R.string.session_done_scanning_en),
                    style = type.label,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * One clause as read from the document: the claim type, and the value in the
 * buyer's own language via the domain's own [ValuePhrase] keys.
 *
 * Set in [VoiceDocument] (serif) on a [VaakkuTheme]`.colors.sheet` ground,
 * because on this screen every line *is* the document — the sans/serif split is
 * how a Delta Card shows which half is which (see `Theme.kt`), and it has to mean
 * the same thing here.
 */
@Composable
private fun ClauseRow(claimType: ClaimType, observation: Observation) {
    val colors = VaakkuTheme.colors
    val type = VaakkuTheme.type
    val space = VaakkuTheme.space

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = space.sm)
            .background(colors.sheet, RoundedCornerShape(space.radiusCard))
            .border(space.hairline, colors.rule, RoundedCornerShape(space.radiusCard))
            .padding(horizontal = space.md, vertical = space.md),
    ) {
        Text(text = claimTypeLabel(claimType), style = type.caption, color = colors.inkSoft)
        Text(
            text = copyText(ValuePhrase.forAny(observation.value)),
            style = type.bodyLg.copy(fontFamily = VoiceDocument),
            color = colors.ink,
            modifier = Modifier.padding(top = space.xs),
        )
    }
}

/**
 * The written observation to show per claim type: the highest-confidence one, the
 * same one `CopyBuilder` quotes on a card.
 *
 * Confidence is used to *pick* and never shown — build plan §2.4 allows the field
 * and bans displaying it.
 */
private fun writtenByType(
    ledger: Map<ClaimType, LedgerEntry>,
): List<Pair<ClaimType, Observation>> =
    ClaimType.entries.mapNotNull { claimType ->
        ledger[claimType]?.written?.maxByOrNull { it.confidence }?.let { claimType to it }
    }

/**
 * How tall the viewfinder is. Its width follows from [PREVIEW_ASPECT].
 *
 * Driven by height rather than width because the width is what has to give:
 * this phone's content column is 384 dp wide, and a full-width portrait
 * preview would be 512 dp tall and push the aim text and the mask note off
 * the first screen of a sheet people are meant to read before they tap Scan.
 * 380 dp is still tall enough to see a page's outline and its margins, which
 * is what aiming needs.
 */
private val PREVIEW_HEIGHT = 380.dp

/**
 * 3:4 — the portrait form of the 4:3 that `DocumentCamera.bind` asks for on both
 * the preview and the capture stream (`RATIO_4_3_FALLBACK_AUTO_STRATEGY`).
 *
 * `aspectRatio` takes width/height, so this is 0.75 and not 1.333. If the camera
 * config ever stops being 4:3, this number is wrong and the border stops being an
 * honest frame — change both together.
 */
private const val PREVIEW_ASPECT = 3f / 4f
