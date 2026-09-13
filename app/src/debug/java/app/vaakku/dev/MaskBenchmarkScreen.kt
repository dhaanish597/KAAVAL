package app.vaakku.dev

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
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
import app.vaakku.npu.MaskAccelerator
import app.vaakku.npu.PersonMasker
import app.vaakku.ocr.DocumentCamera
import app.vaakku.ui.theme.VaakkuTheme
import app.vaakku.ui.theme.VaakkuTypography
import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The §6.5 benchmark screen: the mask model 50× on each of NPU, GPU and CPU,
 * with median and p90, exported as CSV — gate G4's first piece of evidence.
 *
 * ### Why it builds three maskers instead of using the app's one
 *
 * `PrivacyMask` walks the ladder and keeps the first rung that loads, which is
 * exactly right for the product and useless for a comparison. This screen calls
 * [PersonMasker.createOn] three times, so a rung that refuses is a *result* —
 * "NPU refused, here is what it said" is the single most useful line this screen
 * can print, and the ladder swallows it.
 *
 * ### What the two numbers mean
 *
 * `infer` is `model.run` alone: the figure §11.5 budgets at 10 ms and the only
 * one that is really about the accelerator. `total` adds scaling the capture to
 * 256x256, normalising it, and painting the mask back over a full-resolution
 * page — work that happens on the CPU no matter which rung ran the model, and
 * which therefore converges as the page gets bigger. Both are reported because
 * the first answers "is the NPU fast?" and the second answers "does masking fit
 * in the scan budget?", and those have different answers.
 *
 * ### The page is synthetic, and the screen says so
 *
 * A generated page at the real capture size ([DocumentCamera.MAX_CAPTURE_LONG_EDGE_PX]),
 * not a photograph: latency through this model is fixed by tensor shape, not by
 * content, and a synthetic page makes the run repeatable and keeps a benchmark
 * from depending on a file someone has to remember to stage. It does mean the
 * mask covers nearly nothing, which is stated in the export rather than left for
 * a reader to infer from a coverage of 0.00 (CLAUDE.md #8).
 */
@Composable
fun MaskBenchmarkScreen(onClose: () -> Unit) {
    val context = LocalContext.current

    val benchScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val reportLines = remember { mutableStateListOf<String>() }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Runs the mask model $RUNS× on each accelerator. Takes a minute.") }
    var exportLine by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            // Each masker is closed inside the run loop, so cancelling here can
            // only strand one if the cancellation lands mid-rung — and that one
            // is released when its owning coroutine unwinds through the finally.
            job?.cancel()
            benchScope.cancel()
        }
    }

    fun run() {
        if (running) return
        running = true
        exportLine = ""
        reportLines.clear()
        job = benchScope.launch {
            val stamp = Iso8601.now()
            try {
                status = "Building the test page …"
                // Built on Default and held across all three rungs: the point of
                // comparing them is that they saw the same pixels, and rebuilding
                // a 48 MB bitmap per rung would also put three of them through
                // the allocator during the runs that are being timed.
                val page = withContext(Dispatchers.Default) { syntheticPage() }

                val results = mutableListOf<RungResult>()
                try {
                    for (rung in MaskAccelerator.entries) {
                        // The status write stays on the main dispatcher, which is
                        // where this coroutine lives; only the measurement is
                        // moved off it. Compose state has no happens-before
                        // guarantee for a write from an arbitrary pool thread.
                        status = "Measuring ${rung.label} …"
                        results += withContext(Dispatchers.Default) {
                            benchmark(context, rung, page)
                        }
                    }
                } finally {
                    // NonCancellable: leaving 48 MB of native bitmap behind
                    // because the human hit Back is the one outcome worth
                    // spending a few microseconds of a cancelled scope on.
                    withContext(NonCancellable) {
                        if (!page.isRecycled) page.recycle()
                    }
                }

                reportLines.clear()
                reportLines += report(stamp, results)

                status = "Exporting …"
                exportLine = EvidenceExport.writeText(
                    context,
                    "G4_mask_bench_${Iso8601.stamp()}.csv",
                    csv(stamp, results),
                    mimeType = "text/csv",
                )
                val ran = results.count { it is RungResult.Measured }
                status = "Done. $ran of ${results.size} accelerator(s) ran the model."
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                // Including OutOfMemoryError, which is the plausible one: this
                // screen deliberately allocates at the capture size. A dev
                // instrument that reports its own failure is worth more than one
                // that takes the app down mid-measurement.
                status = "Benchmark failed: ${error.javaClass.simpleName}: ${error.message}"
            } finally {
                running = false
            }
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
                top = insets.calculateTopPadding() + 16.dp,
                bottom = insets.calculateBottomPadding() + 24.dp,
            ),
    ) {
        Text("Mask benchmark", style = VaakkuTypography.titleLarge, color = VaakkuTheme.colors.ink)
        Text(
            "the §6.5 privacy masker, $RUNS× on each accelerator (gate G4)",
            style = VaakkuTypography.labelMedium,
            color = VaakkuTheme.colors.inkSoft,
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = VaakkuTheme.colors.rule)
        Spacer(Modifier.height(16.dp))

        DevMono(status)

        Spacer(Modifier.height(14.dp))
        Button(onClick = { run() }, enabled = !running, modifier = Modifier.fillMaxWidth()) {
            Text(if (running) "Running …" else "Run benchmark")
        }

        if (exportLine.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            DevMono(exportLine)
        }

        if (reportLines.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = VaakkuTheme.colors.rule)
            Spacer(Modifier.height(12.dp))
            DevSection("measurements")
            reportLines.forEach { DevMono(it) }
        }

        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("Close")
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** How many timed runs per accelerator — the figure §6.5 names. */
private const val RUNS = 50

/**
 * Discarded runs before timing starts.
 *
 * The first run through a fresh `CompiledModel` pays for lazy buffer setup and,
 * on the NPU rung, for the driver bringing the graph onto the Hexagon. Timing it
 * would put a one-off cost into a median that is supposed to describe the
 * steady state — and would flatter CPU, which has the least of it.
 */
private const val WARMUP = 5

/** One accelerator's outcome: it ran, it refused, or it loaded and produced nothing. */
private sealed interface RungResult {
    val accelerator: MaskAccelerator

    data class Measured(
        override val accelerator: MaskAccelerator,
        val inferenceMs: List<Double>,
        val totalMs: List<Double>,
        val coverage: Double,
    ) : RungResult

    /** The rung would not load the model. [reason] is LiteRT's own words. */
    data class Refused(
        override val accelerator: MaskAccelerator,
        val reason: String,
    ) : RungResult

    /** The model loaded but masking returned nothing — worth its own line. */
    data class NoOutput(override val accelerator: MaskAccelerator) : RungResult
}

/**
 * Builds a masker on [rung] and times it, or reports why it could not.
 *
 * The masker is closed on every path: three native model handles held at once
 * would be three driver contexts, and the NPU one is not cheap.
 */
private suspend fun benchmark(
    context: Context,
    rung: MaskAccelerator,
    page: Bitmap,
): RungResult {
    val masker = PersonMasker.createOn(context, rung).getOrElse { failure ->
        return RungResult.Refused(
            rung,
            failure.message?.takeIf { it.isNotBlank() } ?: failure::class.java.simpleName,
        )
    }

    return try {
        repeat(WARMUP) { masker.mask(page)?.bitmap?.recycle() }

        val inference = ArrayList<Double>(RUNS)
        val total = ArrayList<Double>(RUNS)
        var coverage = 0.0
        repeat(RUNS) {
            val masked = masker.mask(page) ?: return@repeat
            inference += masked.inferenceMs
            total += masked.totalMs
            coverage = masked.coverage
            // Immediately: at the capture size this is ~48 MB per run, and
            // holding 50 of them would be the benchmark measuring the garbage
            // collector.
            masked.bitmap.recycle()
        }

        if (inference.isEmpty()) RungResult.NoOutput(rung)
        else RungResult.Measured(rung, inference, total, coverage)
    } finally {
        masker.close()
    }
}

/**
 * A page at the real capture size with some printed-looking structure on it.
 *
 * The structure is not for the model's benefit — it segments a blank page and a
 * ruled one identically — but for the scale and paint steps, which walk every
 * pixel and should be walking as many as a real capture has.
 */
private fun syntheticPage(): Bitmap {
    val width = DocumentCamera.MAX_CAPTURE_LONG_EDGE_PX * 3 / 4
    val height = DocumentCamera.MAX_CAPTURE_LONG_EDGE_PX
    val page = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(page)
    canvas.drawColor(Color.rgb(250, 247, 240))
    val ink = Paint().apply { color = Color.rgb(30, 30, 30) }
    var y = height / 10
    while (y < height * 9 / 10) {
        canvas.drawRect(
            width / 10f,
            y.toFloat(),
            width * 9f / 10f,
            y + height / 200f,
            ink,
        )
        y += height / 40
    }
    return page
}

/**
 * Nearest-rank percentile: the smallest sample at or above [p] of the way
 * through the sorted data.
 *
 * Nearest-rank rather than interpolating, because an interpolated p90 is a
 * number no run actually produced, and this figure goes into a gate.
 */
private fun percentile(sorted: List<Double>, p: Double): Double {
    if (sorted.isEmpty()) return Double.NaN
    val rank = ceil(p * sorted.size).toInt().coerceIn(1, sorted.size)
    return sorted[rank - 1]
}

private fun List<Double>.stats(): Triple<Double, Double, Double> {
    val sorted = sorted()
    return Triple(percentile(sorted, 0.5), percentile(sorted, 0.9), sorted.firstOrNull() ?: Double.NaN)
}

/**
 * `String.format` fixed to [Locale.ROOT].
 *
 * Not a nicety: the default locale decides the decimal separator, and on a phone
 * set to a locale that uses a comma this would write `7,91` into a comma-separated
 * file — splitting one column into two, silently, in the export that is supposed
 * to *be* the evidence. The on-screen numbers use it too, so what a screenshot
 * shows and what the CSV holds are the same characters.
 */
private fun fmt(format: String, vararg args: Any?): String = String.format(Locale.ROOT, format, *args)

/** The on-screen report. Same numbers as the CSV, fewer of them. */
private fun report(stamp: String, results: List<RungResult>): List<String> = buildList {
    add("MASK BENCHMARK — $stamp")
    add("device ${Build.MODEL} · soc ${Build.SOC_MANUFACTURER}/${Build.SOC_MODEL}")
    add("model ${PersonMasker.MODEL_ASSET} · $RUNS runs after $WARMUP warm-up")
    add("page synthetic, ${DocumentCamera.MAX_CAPTURE_LONG_EDGE_PX * 3 / 4}x${DocumentCamera.MAX_CAPTURE_LONG_EDGE_PX}")
    add("")
    results.forEach { result ->
        when (result) {
            is RungResult.Measured -> {
                val (infMedian, infP90, infMin) = result.inferenceMs.stats()
                val (totMedian, totP90, _) = result.totalMs.stats()
                add("${result.accelerator.label}:")
                add(fmt("  infer  median %.2f ms · p90 %.2f ms · best %.2f ms", infMedian, infP90, infMin))
                add(fmt("  total  median %.2f ms · p90 %.2f ms", totMedian, totP90))
                add(fmt("  masked %.2f%% of the frame (synthetic page: expected near zero)", result.coverage * 100))
            }
            is RungResult.Refused -> {
                add("${result.accelerator.label}: refused the model")
                add("  ${result.reason}")
            }
            is RungResult.NoOutput ->
                add("${result.accelerator.label}: loaded, but produced no mask")
        }
        add("")
    }
    add("infer = model.run only (the §11.5 10 ms budget).")
    add("total = scale + normalise + infer + paint, at the page size above.")
    add("This names the accelerator LiteRT accepted. Proof of Hexagon dispatch")
    add("is a logcat line, not this screen (CLAUDE.md #8).")
}

/**
 * Every run as its own row, with the metadata as leading `#` lines.
 *
 * Raw rows rather than just the summary: a median in a file nobody can
 * recompute is a number you have to take on trust, and G4 is a gate.
 */
private fun csv(stamp: String, results: List<RungResult>): String = buildString {
    appendLine("# VAAKKU mask benchmark (build plan §6.5, gate G4)")
    appendLine("# generated,$stamp")
    appendLine("# device,${Build.MODEL}")
    appendLine("# soc,${Build.SOC_MANUFACTURER}/${Build.SOC_MODEL}")
    appendLine("# android,${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    appendLine("# model,${PersonMasker.MODEL_ASSET}")
    appendLine("# runs,$RUNS")
    appendLine("# warmup_discarded,$WARMUP")
    appendLine("# page,synthetic ${DocumentCamera.MAX_CAPTURE_LONG_EDGE_PX * 3 / 4}x${DocumentCamera.MAX_CAPTURE_LONG_EDGE_PX}")
    appendLine("# inference_ms,model.run only")
    appendLine("# total_ms,scale + normalise + infer + paint")
    appendLine("# note,accelerator is the rung LiteRT accepted; dispatch proof is logcat")
    results.filterIsInstance<RungResult.Refused>().forEach {
        appendLine("# refused,${it.accelerator.label},\"${it.reason.replace("\"", "'")}\"")
    }
    results.filterIsInstance<RungResult.NoOutput>().forEach {
        appendLine("# no_output,${it.accelerator.label}")
    }
    appendLine("accelerator,run,inference_ms,total_ms")
    results.filterIsInstance<RungResult.Measured>().forEach { measured ->
        measured.inferenceMs.forEachIndexed { index, inference ->
            appendLine(
                fmt(
                    "%s,%d,%.3f,%.3f",
                    measured.accelerator.label,
                    index + 1,
                    inference,
                    measured.totalMs.getOrElse(index) { Double.NaN },
                ),
            )
        }
    }
}
