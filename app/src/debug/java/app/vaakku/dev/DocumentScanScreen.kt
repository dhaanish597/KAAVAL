package app.vaakku.dev

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.vaakku.domain.copy.ValuePhrase
import app.vaakku.domain.model.ClaimValue
import app.vaakku.domain.model.Observation
import app.vaakku.domain.model.Provenance
import app.vaakku.domain.model.RateQualifier
import app.vaakku.domain.reconcile.Reconciler
import app.vaakku.domain.reconcile.ReconcilerEvent
import app.vaakku.ocr.DocumentCamera
import app.vaakku.ocr.MlKitTextRecognizer
import app.vaakku.ocr.PageScanner
import app.vaakku.ocr.PrivacyMask
import app.vaakku.ocr.ScannedPage
import app.vaakku.session.SessionEvidence
import app.vaakku.ui.theme.VaakkuTheme
import app.vaakku.ui.theme.VaakkuTypography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The scan surface — build plan §6.4, gate G3.
 *
 * Point the camera at a printed page, tap Scan, and the clauses that page
 * contains appear underneath: CameraX capture → ML Kit → `RowAssembler` +
 * `WrittenExtractor` → `WrittenObserved`. "Done scanning" closes the scan with
 * `DocumentScanCompleted`, which is the event the reconciler requires before it
 * will ever report a claim as absent from the document.
 *
 * **The clause list is a reading of the document, not a comparison.** It shows
 * each claim type and the value read for it, in plain words, and nothing else:
 * no MATCHES / NOT_IN_DOCUMENT / DIFFERS, no colour, nothing said about any
 * person. A comparison needs the spoken half, which this screen does not have,
 * and the reconciler is the only thing that ever performs one (§5.7).
 *
 * **One scan session spans several pages.** No single page of the prop document
 * carries all five G3 clauses; printed pages 5 and 6 together do. Each capture
 * is extracted on its own and the observations accumulate — see the comment in
 * `PageScanner.scan` for why extracting over several pages at once is not an
 * option.
 *
 * In the Dev menu for now: P5 designs the real session UI and P3 must not
 * pre-empt it. For the same reason this screen owns a **local** [Reconciler] —
 * session lifecycle and any shared state belong to P5.
 */
@Composable
fun DocumentScanScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val camera = remember { DocumentCamera() }

    // One native recognizer for the lifetime of the screen, closed in onDispose
    // — the same shape as AsrEngine. Building one per scan would pay the load
    // cost on every tap and make the OCR ms figure describe the load, not the
    // recognition.
    val recognizer = remember { MlKitTextRecognizer() }

    // LOCAL to this screen. Not a service, not a holder, not a singleton: P5
    // owns session lifecycle, and scripts/check_manifest.sh still asserts that
    // SessionService is absent from the manifest.
    val reconciler = remember { Reconciler() }

    var sessionId by remember { mutableStateOf(SessionEvidence.newSessionId()) }
    val evidence = remember(sessionId) { SessionEvidence.forSession(context, sessionId) }
    val scanner = remember(sessionId) { PageScanner(recognizer, evidence) }

    val previewView = remember {
        PreviewView(context).apply {
            // COMPATIBLE (TextureView) rather than the default PERFORMANCE
            // (SurfaceView): this preview sits inside a scrolling column, and a
            // SurfaceView punches its own hole through the window, which does
            // not move when the column scrolls.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val pages = remember { mutableStateListOf<ScannedPage>() }
    var clausesRead by remember { mutableIntStateOf(0) }
    var scanning by remember { mutableStateOf(false) }
    var scanCompleted by remember { mutableStateOf(false) }
    var bound by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Grant the camera, then aim at the page.") }
    var job by remember { mutableStateOf<Job?>(null) }

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { result ->
        granted = result
        if (!result) status = "CAMERA refused. Grant it in Settings — without it there is nothing to read."
    }

    LaunchedEffect(granted) {
        if (!granted || bound) return@LaunchedEffect
        runCatching { camera.bind(context, lifecycleOwner, previewView.surfaceProvider) }
            .onSuccess {
                bound = true
                status = "Camera ready. Fill the frame with one page, hold still, tap Scan."
            }
            .onFailure { t -> status = "Camera bind failed: ${t.javaClass.simpleName}: ${t.message}" }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Order matters: stop the scan still in flight, then release the
            // camera, then the recognizer. Closing a recognizer out from under
            // a running recognition is how a native handle leaks.
            job?.cancel()
            camera.unbind()
            recognizer.close()
        }
    }

    fun scan() {
        if (scanning || !bound) return
        scanning = true
        status = "Capturing…"
        job = scope.launch {
            try {
                val bitmap = camera.capturePage()
                status = "Reading…"
                // Off the main thread: full-resolution recognition, a page JPEG
                // and one JPEG per observed row.
                val page = withContext(Dispatchers.Default) { scanner.scan(bitmap, pages.size + 1) }
                pages += page
                page.observations.forEach { reconciler.apply(ReconcilerEvent.WrittenObserved(it)) }
                clausesRead += page.observations.size
                status = "Page ${page.pageNumber}: ${page.observations.size} clause(s) " +
                    "from ${page.lineCount} OCR line(s) in ${page.ocrElapsedMs} ms."
            } catch (c: CancellationException) {
                status = "Cancelled."
                // Rethrown: swallowing it would leave the parent scope believing
                // this child is still alive.
                throw c
            } catch (t: Throwable) {
                // Not rethrown. A refused capture or an unreadable page is an
                // ordinary outcome on this screen; crashing the Dev menu would
                // hide the line that says which it was.
                status = "Scan failed: ${t.javaClass.simpleName}: ${t.message}"
            } finally {
                scanning = false
            }
        }
    }

    fun doneScanning() {
        reconciler.apply(ReconcilerEvent.DocumentScanCompleted)
        scanCompleted = true
        status = "Done scanning — DocumentScanCompleted sent after ${pages.size} page(s)."
    }

    fun newSession() {
        job?.cancel()
        reconciler.apply(ReconcilerEvent.Reset)
        pages.clear()
        clausesRead = 0
        scanCompleted = false
        sessionId = SessionEvidence.newSessionId()
        status = "New scan session. Aim at the first page."
    }

    val insets = WindowInsets.safeDrawing.asPaddingValues()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VaakkuTheme.colors.paper)
            .verticalScroll(rememberScrollState())
            .padding(
                start = 24.dp,
                end = 24.dp,
                top = 24.dp + insets.calculateTopPadding(),
                bottom = 24.dp + insets.calculateBottomPadding(),
            ),
    ) {
        Text("Document scan", style = VaakkuTypography.titleLarge, color = VaakkuTheme.colors.ink)
        Text(
            "camera → ML Kit → RowAssembler + WrittenExtractor → WrittenObserved",
            style = VaakkuTypography.labelMedium,
            color = VaakkuTheme.colors.inkSoft,
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = VaakkuTheme.colors.rule)
        Spacer(Modifier.height(16.dp))

        if (granted) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
            )
        } else {
            DevMono("Camera permission not granted yet.")
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { cameraPermission.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Grant camera")
            }
        }

        Spacer(Modifier.height(10.dp))
        DevMono(PrivacyMask.STATUS_LINE)

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            Button(
                onClick = ::scan,
                enabled = bound && !scanning && !scanCompleted,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (scanning) "Scanning…" else "Scan")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = ::doneScanning,
                enabled = pages.isNotEmpty() && !scanning && !scanCompleted,
                modifier = Modifier.weight(1f),
            ) {
                Text("Done scanning")
            }
        }

        Spacer(Modifier.height(10.dp))
        DevMono(status)

        Spacer(Modifier.height(14.dp))
        DevSection("this scan session")
        DevMono("pages captured: ${pages.size}")
        DevMono("clauses read: $clausesRead")
        DevMono("done scanning: ${if (scanCompleted) "sent" else "not sent yet"}")
        DevMono("page images: ${evidence.displayPath}")
        DevMono("crops: ${evidence.displayPath}crops/ (JPEG q80, max 800 px wide)")

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = VaakkuTheme.colors.rule)
        Spacer(Modifier.height(12.dp))

        DevSection("clauses read from the document")
        if (pages.isEmpty()) {
            DevMono("(nothing scanned yet)")
        } else {
            pages.forEach { page -> PageBlock(page) }
        }

        Spacer(Modifier.height(20.dp))
        OutlinedButton(
            onClick = ::newSession,
            enabled = !scanning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("New scan session")
        }
        DevMono("G3 asks for five scans of the prop; each one gets its own evidence folder.")

        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("Close")
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * One captured page: what the document says, and where it says it.
 *
 * The OCR time is ML Kit's own (§6.4 item 6) — the figure §11.5's scan budget
 * is written against, not the cost of this screen's own bookkeeping.
 */
@Composable
private fun PageBlock(page: ScannedPage) {
    DevSection(
        "page ${page.pageNumber} — ${page.lineCount} OCR lines, " +
            "OCR ${page.ocrElapsedMs} ms, ${page.cropCount} crop(s)",
    )
    if (page.observations.isEmpty()) {
        DevMono("  (no clause matched on this page)")
    } else {
        page.observations.forEach { observation ->
            DevMono("  ${plainClause(observation)}")
            val written = observation.provenance as? Provenance.Written
            if (written != null) {
                DevMono("     as printed: ${written.lineText}")
                DevMono("     crop: ${written.cropFile?.substringAfterLast('/') ?: "(none written)"}")
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

/**
 * One observation in plain words: the claim type, then the value read for it.
 *
 * Deliberately not `SlotChecker.describe`, which prints a machine-comparable
 * form ("nilBefore:60", "true") for the fixture harness. The human checking
 * this screen against the paper in their other hand needs to read it as
 * English.
 *
 * Nothing here compares anything: no state, no colour, and no claim about
 * whether any of this agrees with a word that was spoken.
 */
private fun plainClause(observation: Observation): String {
    val value = when (val v = observation.value) {
        is ClaimValue.Rate -> {
            val percents = ValuePhrase.formatPercentList(v.percents)
            val qualifier = if (v.qualifier == RateQualifier.ILLUSTRATIVE) " (illustrative)" else ""
            "$percents% a year$qualifier"
        }
        is ClaimValue.Guarantee ->
            if (v.guaranteed) "returns are guaranteed" else "returns are not guaranteed"
        is ClaimValue.LockIn -> duration(v.months)
        is ClaimValue.Liquidity -> {
            // Read into locals first: these are public properties of another
            // module, so Kotlin will not smart-cast them after a null check.
            val nilBefore = v.surrenderNilBeforeMonths
            val withdrawAfter = v.withdrawableAfterMonths
            when {
                nilBefore != null -> "surrender value nil before ${duration(nilBefore)}"
                withdrawAfter != null -> "withdrawal after ${duration(withdrawAfter)}"
                else -> "no period read"
            }
        }
        is ClaimValue.Bundling ->
            if (v.requiredForLoan) "required for the loan" else "voluntary"
        is ClaimValue.Charges ->
            if (!v.anyCharges) {
                "no charge stated"
            } else {
                listOfNotNull(v.label, v.percent?.let { "${ValuePhrase.formatNumber(it)}%" })
                    .joinToString(", ")
                    .ifEmpty { "a charge is stated" }
            }
    }
    return "${observation.type.name} — $value"
}

/** Whole years when the months divide evenly, months otherwise. */
private fun duration(months: Int): String = when {
    months == 12 -> "1 year"
    months % 12 == 0 -> "${months / 12} years"
    months == 1 -> "1 month"
    else -> "$months months"
}
