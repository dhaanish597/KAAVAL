package app.vaakku.dev

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.vaakku.asr.EvidenceExport
import app.vaakku.asr.Iso8601
import app.vaakku.domain.model.ClaimType
import app.vaakku.domain.model.Provenance
import app.vaakku.domain.model.Thresholds
import app.vaakku.ocr.DocumentCamera
import app.vaakku.ocr.MlKitTextRecognizer
import app.vaakku.ocr.PageConfidence
import app.vaakku.ocr.PageScanner
import app.vaakku.ocr.PrivacyMask
import app.vaakku.ocr.ScannedPage
import app.vaakku.session.SessionEvidence
import app.vaakku.ui.theme.VaakkuTheme
import app.vaakku.ui.theme.VaakkuTypography
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Replay OCR + extraction over image files already on disk — a Dev-menu
 * instrument for gate G3, build plan §6.4.
 *
 * ### What it is, and what it deliberately is not
 *
 * [DocumentScanScreen] answers "does the camera → OCR → extractor path read the
 * printed prop?" This screen answers the *other half* of that question: given a
 * page the camera never had trouble with — a clean render, or one of the human's
 * own photographs staged as a file — does ML Kit → `RowAssembler` →
 * `WrittenExtractor` read the clauses it should? It runs the **same**
 * [PageScanner] the scan screen runs, over `InputImage.fromBitmap`, so
 * everything past the shutter is identical; only the pixels come from a file
 * rather than the sensor.
 *
 * It is therefore **a replay, not a scan**, and both the screen and the exported
 * report say so (CLAUDE.md #8): it does not measure camera capture quality,
 * focus or lighting — only the recognition and extraction that follow. The point
 * is to separate "the extractor cannot read this clause" from "the photograph
 * was not good enough", which a live scan cannot tell apart.
 *
 * ### Where the files come from
 *
 * `<external files>/replay/`, i.e. `Android/data/app.vaakku/files/replay/` — the
 * same external-files tree the models already live in, so `adb push` reaches it
 * with no storage permission and the app reads only its own sandbox. Stage a
 * render of a printed page, or a photograph pulled off the phone, then tap Run.
 *
 * ### Still a reading, never a comparison
 *
 * Like the scan screen, this lists what the document *says* and nothing about
 * whether it agrees with any spoken word: no MATCHES / NOT_IN_DOCUMENT / DIFFERS,
 * no colour, nothing about any person. The reconciler is the only thing that
 * compares, and it is not on this screen. The one number shown per clause is its
 * own read-strength against the `writtenMin` the reconciler would later filter on
 * — a measurement of the reading, exactly as on the scan screen.
 */
@Composable
fun PageReplayScreen(onClose: () -> Unit) {
    val context = LocalContext.current

    val replayScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val recognizer = remember { MlKitTextRecognizer() }

    // A real masker, not a stub. This screen exists to exercise the same path
    // the scan screen takes, and the mask is part of that path: if it withholds
    // a page here, the report says so, which is exactly the signal worth having
    // before the same thing happens in front of a buyer. Replaying an already
    // masked page simply masks nothing, because there is no person left in it.
    val privacyMask = remember { PrivacyMask(context) }

    val replayDir = remember { File(context.getExternalFilesDir(null), REPLAY_DIR_NAME) }

    val reportLines = remember { mutableStateListOf<String>() }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(initialStatus(replayDir)) }
    var exportLine by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            // Same teardown discipline as the scan screen: a recognition may be
            // in flight, and closing the detector under it is the one thing that
            // crashes. Let the job finish, then close and cancel.
            val inFlight = job
            if (inFlight == null || inFlight.isCompleted) {
                recognizer.close()
                privacyMask.close()
                replayScope.cancel()
            } else {
                inFlight.invokeOnCompletion {
                    recognizer.close()
                    privacyMask.close()
                    replayScope.cancel()
                }
            }
        }
    }

    fun run() {
        if (running) return
        running = true
        exportLine = ""
        reportLines.clear()
        status = "Reading files from ${replayDir.absolutePath} …"
        job = replayScope.launch {
            val stamp = Iso8601.now()
            val files = imageFilesIn(replayDir)
            if (files.isEmpty()) {
                status = "No image files in ${replayDir.absolutePath}. Push a page there and tap Run."
                running = false
                return@launch
            }

            // One evidence folder for the whole replay run, named so it never
            // reads as a real scan session in the evidence tree.
            val evidence = SessionEvidence.forSession(context, "replay_${Iso8601.stamp()}")
            val scanner = PageScanner(recognizer, evidence, privacyMask)

            val results = mutableListOf<ReplayResult>()
            files.forEachIndexed { index, file ->
                status = "Reading ${file.name} (${index + 1} of ${files.size}) …"
                val result = withContext(Dispatchers.Default) { replayOne(scanner, file, index + 1) }
                results += result
                // Show progress line by line as each page lands, rather than
                // only at the end of a multi-page run.
                reportLines += pageReportLines(result)
                reportLines += ""
            }

            val lines = fullReport(stamp, replayDir, evidence, results)
            reportLines.clear()
            reportLines += lines

            status = "Read ${results.size} file(s). Exporting the report …"
            val fileName = "G3_replay_${Iso8601.stamp()}.txt"
            exportLine = EvidenceExport.writeText(context, fileName, lines.joinToString("\n"))
            status = "Done. ${results.count { it.page != null }} of ${results.size} file(s) read."
            running = false
        }
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
        Text("Page replay", style = VaakkuTypography.titleLarge, color = VaakkuTheme.colors.ink)
        Text(
            "staged image file → ML Kit → RowAssembler + WrittenExtractor (a replay, not a scan)",
            style = VaakkuTypography.labelMedium,
            color = VaakkuTheme.colors.inkSoft,
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = VaakkuTheme.colors.rule)
        Spacer(Modifier.height(16.dp))

        DevMono("files: ${replayDir.absolutePath}")
        DevMono("push a page there with:")
        DevMono("  adb push page.png ${replayDir.absolutePath}/")

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = ::run,
            enabled = !running,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (running) "Reading…" else "Run replay")
        }

        Spacer(Modifier.height(10.dp))
        DevMono(status)
        if (exportLine.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            DevMono(exportLine)
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = VaakkuTheme.colors.rule)
        Spacer(Modifier.height(12.dp))

        DevSection("clauses read")
        if (reportLines.isEmpty()) {
            DevMono("(nothing replayed yet)")
        } else {
            reportLines.forEach { DevMono(it) }
        }

        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("Close")
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** One staged file's outcome: the page it produced, or why it produced none. */
private data class ReplayResult(
    val name: String,
    val page: ScannedPage?,
    val error: String?,
)

/**
 * Decodes [file] and runs it through [scanner]. A decode failure is an ordinary
 * outcome here (someone pushed a non-image, or a truncated file), reported as a
 * line rather than a crash.
 */
private suspend fun replayOne(scanner: PageScanner, file: File, pageNumber: Int): ReplayResult {
    val bitmap = runCatching { decodeCapped(file) }.getOrNull()
        ?: return ReplayResult(file.name, null, "could not be decoded as an image")
    return runCatching { ReplayResult(file.name, scanner.scan(bitmap, pageNumber), null) }
        .getOrElse { t ->
            // scanner.scan owns and recycles the bitmap on every path, so there
            // is nothing to release here.
            ReplayResult(file.name, null, "${t.javaClass.simpleName}: ${t.message}")
        }
}

/**
 * Decodes [file] with its long edge capped at [DocumentCamera.MAX_CAPTURE_LONG_EDGE_PX],
 * the same ceiling a live capture is held to — so the replay reads the page at
 * the resolution the real path would, and a large render cannot OutOfMemory the
 * Dev menu.
 */
private fun decodeCapped(file: File): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val options = BitmapFactory.Options().apply {
        inSampleSize = DocumentCamera.sampleSizeFor(
            bounds.outWidth,
            bounds.outHeight,
            DocumentCamera.MAX_CAPTURE_LONG_EDGE_PX,
        )
    }
    val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
        ?: error("decodeFile returned null for ${file.name}")
    // inSampleSize only halves, so the result can still be up to twice the cap.
    val longEdge = maxOf(decoded.width, decoded.height)
    if (longEdge <= DocumentCamera.MAX_CAPTURE_LONG_EDGE_PX) return decoded
    val scale = DocumentCamera.MAX_CAPTURE_LONG_EDGE_PX.toDouble() / longEdge
    val width = (decoded.width * scale).toInt().coerceAtLeast(1)
    val height = (decoded.height * scale).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(decoded, width, height, true)
    if (scaled !== decoded) decoded.recycle()
    return scaled
}

/** Image files in [dir], sorted by name so runs are repeatable and in page order. */
private fun imageFilesIn(dir: File): List<File> {
    val exts = setOf("jpg", "jpeg", "png", "webp")
    return dir.listFiles()
        ?.filter { it.isFile && it.extension.lowercase() in exts }
        ?.sortedBy { it.name }
        ?: emptyList()
}

private fun initialStatus(replayDir: File): String {
    val count = imageFilesIn(replayDir).size
    return when {
        !replayDir.exists() -> "No replay folder yet — push a page to ${replayDir.absolutePath}/ and tap Run."
        count == 0 -> "Replay folder is empty. Push a page there and tap Run."
        else -> "$count image file(s) staged. Tap Run."
    }
}

/** The full exported report: header, one block per file, then the roll-up. */
private fun fullReport(
    stamp: String,
    replayDir: File,
    evidence: SessionEvidence,
    results: List<ReplayResult>,
): List<String> {
    val lines = mutableListOf<String>()
    lines += "VAAKKU page replay — OCR + extractor over staged image files (build plan §6.4, gate G3)"
    lines += "captured_at: $stamp"
    lines += ""
    lines += "This is a REPLAY of image files already on disk, not a camera scan. It exercises"
    lines += "the same PageScanner (ML Kit → RowAssembler → WrittenExtractor) the scan screen"
    lines += "runs, but it does NOT measure camera capture quality — only the reading that"
    lines += "follows. It reads a document; it compares nothing (CLAUDE.md #1)."
    lines += ""
    lines += "files read from: ${replayDir.absolutePath}"
    lines += "evidence written to: files/sessions/${evidence.sessionDir.name}/"
    lines += ""

    results.forEach { result ->
        lines += pageReportLines(result)
        lines += ""
    }

    lines += rollUp(results)
    return lines
}

/** One file's block, shared by the live on-screen list and the exported report. */
private fun pageReportLines(result: ReplayResult): List<String> {
    val lines = mutableListOf<String>()
    lines += "--- ${result.name} ---"
    val page = result.page
    if (page == null) {
        lines += "  not read: ${result.error}"
        return lines
    }

    lines += "  OCR: ${page.lineCount} line(s) in ${page.ocrElapsedMs} ms, ${page.cropCount} crop(s)"
    if (page.pageImage == null) lines += "  page image: (not written)"
    page.confidence.reportLines().forEach { lines += "  $it" }

    if (page.observations.isEmpty()) {
        lines += "  (no clause read on this page)"
    } else {
        page.observations.forEach { observation ->
            lines += "  ${plainClause(observation)}"
            lines += "     read at ${PageConfidence.format3(observation.confidence)}${gateNote(observation, page)}"
            val written = observation.provenance as? Provenance.Written
            if (written != null) lines += "     as printed: ${written.lineText}"
        }
    }
    return lines
}

/**
 * Which of the five G3 clause types were read at or above `writtenMin` across the
 * whole run, and the strongest value read for each. This is the read side of the
 * G3 criterion — the reconciler's comparison is not performed here, and no state
 * is expressed.
 */
private fun rollUp(results: List<ReplayResult>): List<String> {
    val gate = Thresholds.DEFAULT.writtenMin
    val observations = results.mapNotNull { it.page }.flatMap { it.observations }

    val lines = mutableListOf<String>()
    lines += "=== read across ${results.size} file(s), at or above writtenMin ${PageConfidence.format2(gate)} ==="
    G3_CLAUSE_TYPES.forEach { type ->
        val best = observations
            .filter { it.type == type && !it.confidence.isNaN() && it.confidence >= gate }
            .maxByOrNull { it.confidence }
        lines += if (best == null) {
            "  ${type.name}: not read at or above the gate"
        } else {
            "  ${type.name}: ${plainClause(best)} (read at ${PageConfidence.format3(best.confidence)})"
        }
    }
    val readCount = G3_CLAUSE_TYPES.count { type ->
        observations.any { it.type == type && !it.confidence.isNaN() && it.confidence >= gate }
    }
    lines += "read ${readCount} of ${G3_CLAUSE_TYPES.size} G3 clause types at or above the gate."
    return lines
}

/**
 * The five clauses G3 asks for, in reading order. BUNDLING is the sixth claim
 * type and not one of them, so it is not rolled up here — a page that happens to
 * state it still lists it in its own block above.
 */
private val G3_CLAUSE_TYPES = listOf(
    ClaimType.RETURN_RATE,
    ClaimType.GUARANTEE,
    ClaimType.LOCK_IN,
    ClaimType.LIQUIDITY,
    ClaimType.CHARGES,
)

private const val REPLAY_DIR_NAME = "replay"
