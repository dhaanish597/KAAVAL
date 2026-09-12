package app.vaakku.dev

import android.os.Build
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import app.vaakku.asr.AsrBakeoff
import app.vaakku.asr.AsrEngineHolder
import app.vaakku.asr.AsrEngineId
import app.vaakku.asr.BakeoffEvent
import app.vaakku.asr.EngineTotals
import app.vaakku.asr.EvidenceExport
import app.vaakku.asr.Iso8601
import app.vaakku.asr.VadSegmenter
import app.vaakku.ui.theme.VaakkuTheme
import app.vaakku.ui.theme.VaakkuTypography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The ASR bake-off (build plan §11.3 item 2) — the second of the three pieces of
 * evidence gate G2 needs, and the only source of the **phone** RTF that §13's P2
 * decision rule is written against.
 *
 * Read it like this: take the engine with the highest slot accuracy whose
 * aggregate RTF is at or under 0.50; on a tie, the smaller model. If none clears
 * 70% slot accuracy, §13's own fallback applies — the demo runs from the
 * rehearsal clip and live mic becomes a "try it" moment — and that is a decision
 * to write into STATUS.md, not a number to nudge.
 *
 * Expect this to take a few minutes: four engines, fifteen clips, and the Whisper
 * model alone is 365 MB to load. It runs with the screen on because a locked
 * phone throttles, and a throttled RTF is not the number the budget means.
 */
@Composable
fun AsrBakeoffScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var numThreads by remember { mutableIntStateOf(VadSegmenter.DEFAULT_NUM_THREADS) }
    var status by remember { mutableStateOf("Idle. Expect a few minutes.") }
    var running by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }

    val log = remember { mutableStateListOf<String>() }
    val totals = remember { mutableStateListOf<EngineTotals>() }

    // Read once on open: a readiness check stats a handful of files, and the
    // answer only changes when the human runs push_models.sh, not while the
    // screen is up.
    val enginesReady = remember {
        AsrEngineId.entries
            .filter { it.decodesSamples }
            .map { it to AsrEngineHolder.availability(context, it).summary }
    }

    fun start() {
        if (running) return
        running = true
        log.clear()
        totals.clear()
        status = "Running…"

        job = scope.launch {
            try {
                AsrBakeoff(context, context.assets)
                    .run(numThreads = numThreads)
                    .collect { event ->
                        when (event) {
                            is BakeoffEvent.Started -> {
                                log += "== ${event.engine.displayName}: ${event.clips} clip(s)"
                                status = "Loading ${event.engine.displayName}…"
                            }
                            is BakeoffEvent.Loaded ->
                                log += "   loaded in ${event.loadMs} ms"
                            is BakeoffEvent.Row -> {
                                val r = event.row
                                status = "${r.engine.displayName} · ${r.clip}"
                                log += "   ${r.clip}: ${r.slotsCorrect}/${r.slotsExpected} slots, " +
                                    "rtf=%.3f, ${r.segments} seg".format(r.rtf) +
                                    (r.error?.let { " — $it" } ?: "")
                            }
                            is BakeoffEvent.EngineDone -> {
                                totals += event.totals
                                log += "   TOTAL ${event.totals.engine.displayName}: " +
                                    "slot_accuracy=%.3f agg_rtf=%.3f worst_rtf=%.3f"
                                        .format(
                                            event.totals.slotAccuracy,
                                            event.totals.aggregateRtf,
                                            event.totals.worstRtf,
                                        )
                            }
                            is BakeoffEvent.EngineFailed ->
                                log += "   SKIPPED ${event.engine.displayName}: ${event.reason}"
                            is BakeoffEvent.Finished ->
                                status = "Finished: ${event.totals.size} engine(s) measured."
                        }
                    }
            } catch (c: CancellationException) {
                status = "Cancelled."
                throw c
            } catch (t: Throwable) {
                status = "Failed: ${t.javaClass.simpleName}: ${t.message}"
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
                top = 24.dp + insets.calculateTopPadding(),
                bottom = 24.dp + insets.calculateBottomPadding(),
            ),
    ) {
        Text("ASR bake-off", style = VaakkuTypography.titleLarge, color = VaakkuTheme.colors.ink)
        Text(
            "every engine × every clip — phone RTF and slot accuracy",
            style = VaakkuTypography.labelMedium,
            color = VaakkuTheme.colors.inkSoft,
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = VaakkuTheme.colors.rule)
        Spacer(Modifier.height(16.dp))

        DevSection("engines")
        enginesReady.forEach { (id, summary) -> DevMono("${id.displayName}: $summary") }

        Spacer(Modifier.height(14.dp))
        DevSection("numThreads")
        Row(Modifier.fillMaxWidth()) {
            listOf(1, 2, 4, 6).forEach { n ->
                OutlinedButton(
                    onClick = { numThreads = n },
                    enabled = !running,
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text(if (n == numThreads) "[$n]" else "$n")
                }
            }
        }
        DevMono("Record the thread count with the result — RTF is meaningless without it.")

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = ::start,
            enabled = !running,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (running) "Running…" else "Run bake-off")
        }

        Spacer(Modifier.height(10.dp))
        DevMono(status)

        if (totals.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = VaakkuTheme.colors.rule)
            Spacer(Modifier.height(12.dp))
            DevSection("summary — §13 P2 rule: best slot accuracy with agg_rtf <= 0.50")
            totals
                .sortedWith(compareByDescending<EngineTotals> { it.slotAccuracy }.thenBy { it.aggregateRtf })
                .forEach { t ->
                    DevMono(
                        "%-22s acc=%.3f  agg_rtf=%.3f  %s".format(
                            t.engine.name,
                            t.slotAccuracy,
                            t.aggregateRtf,
                            if (t.aggregateRtf <= RTF_BUDGET) "eligible" else "over the RTF budget",
                        ),
                    )
                }
            val eligible = totals.filter { it.aggregateRtf <= RTF_BUDGET }
            val best = eligible.maxByOrNull { it.slotAccuracy }
            DevMono(
                when {
                    best == null -> "No engine met the RTF budget. §13's fallback applies."
                    best.slotAccuracy < ACCURACY_THRESHOLD ->
                        "Best eligible is ${best.engine.name} at %.3f — under the 0.70 §13 asks for, so the fallback applies."
                            .format(best.slotAccuracy)
                    else -> "Best eligible: ${best.engine.name} at %.3f.".format(best.slotAccuracy)
                },
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = VaakkuTheme.colors.rule)
        Spacer(Modifier.height(12.dp))
        DevSection("log (${log.size})")
        if (log.isEmpty()) DevMono("(nothing yet)") else log.forEach { DevMono(it) }

        Spacer(Modifier.height(20.dp))
        OutlinedButton(
            onClick = {
                scope.launch {
                    val name = "G2_asr_bakeoff_${Iso8601.stamp()}.csv"
                    status = EvidenceExport.writeText(
                        context = context,
                        fileName = name,
                        text = AsrBakeoff.toCsv(totals.toList(), deviceSummary(), numThreads),
                        mimeType = EvidenceExport.MIME_CSV,
                    )
                }
            },
            enabled = totals.isNotEmpty() && !running,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Export CSV to Download/Vaakku/evidence/")
        }

        Spacer(Modifier.height(20.dp))
        OutlinedButton(
            onClick = {
                job?.cancel()
                onClose()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Close")
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** §11.5's ASR decode budget, and the gate in §13's P2 rule. */
private const val RTF_BUDGET = 0.50

/** §13 P2: below this, the demo falls back to the rehearsal clip. */
private const val ACCURACY_THRESHOLD = 0.70

/**
 * The device line that goes into the CSV header.
 *
 * The SoC is in there deliberately: an RTF measured on a Snapdragon 8 Elite Gen 5
 * is not transferable to any other phone, and a CSV that does not say which chip
 * produced it is a number without a unit.
 */
private fun deviceSummary(): String =
    "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), " +
        "SoC=${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}, " +
        "rom=${Build.DISPLAY}, sdk=${Build.VERSION.SDK_INT}"
