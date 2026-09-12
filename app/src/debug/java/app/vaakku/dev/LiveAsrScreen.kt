package app.vaakku.dev

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import app.vaakku.asr.AsrEngine
import app.vaakku.asr.AsrEngineHolder
import app.vaakku.asr.AsrEngineId
import app.vaakku.asr.AsrPipeline
import app.vaakku.asr.AudioSource
import app.vaakku.asr.EvidenceExport
import app.vaakku.asr.Iso8601
import app.vaakku.asr.MicAudioSource
import app.vaakku.asr.ModelPaths
import app.vaakku.asr.RecognisedSegment
import app.vaakku.asr.VadSegmenter
import app.vaakku.asr.WavAssetAudioSource
import app.vaakku.domain.extract.SpokenExtractor
import app.vaakku.domain.fixtures.SlotChecker
import app.vaakku.domain.lexicon.LexiconLoader
import app.vaakku.domain.model.Observation
import app.vaakku.domain.normalize.Normalizer
import app.vaakku.ui.theme.MonoStyle
import app.vaakku.ui.theme.VaakkuTheme
import app.vaakku.ui.theme.VaakkuTypography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The mic, as a pseudo-entry in the source list. */
private const val MIC_SOURCE = "(microphone)"

/**
 * Live ASR (build plan §6.3) — the screen that shows the whole spoken half of the
 * pipeline working, or not working, on the phone.
 *
 * Each row is one VAD segment: the transcript, the timing the §11.5 RTF budget is
 * measured against, the `segmentQuality` that scales every claim's confidence,
 * and the claims `SpokenExtractor` recovered from that text. Seeing all four
 * together is the point — a segment with good text and no claims is a lexicon
 * problem, and a segment with no text at all is an engine problem, and those have
 * nothing to do with each other.
 *
 * The same clips the bake-off uses can be played through here, which is how a
 * live-mic result and a file result stay comparable.
 *
 * **No audio is written anywhere** (CLAUDE.md #4). The export button writes the
 * transcript text only, which §6.3 permits in a debug build.
 */
@Composable
fun LiveAsrScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val extractor = remember {
        val lexicon = LexiconLoader.loadDefault()
        SpokenExtractor(lexicon, Normalizer(lexicon))
    }
    val clips = remember { WavAssetAudioSource.listClips(context.assets) }

    var engineId by remember { mutableStateOf(AsrEngineId.DEFAULT) }
    var numThreads by remember { mutableIntStateOf(VadSegmenter.DEFAULT_NUM_THREADS) }
    var sourceName by remember { mutableStateOf(MIC_SOURCE) }
    var status by remember { mutableStateOf("Idle.") }
    var running by remember { mutableStateOf(false) }

    val rows = remember { mutableStateListOf<RecognisedSegment>() }

    // Held so Stop can end the stream at the source rather than cancelling the
    // collector: cancelling throws away the segment still inside the VAD, and
    // that is the last thing the speaker said.
    var activeSource by remember { mutableStateOf<AudioSource?>(null) }
    var activeJob by remember { mutableStateOf<Job?>(null) }

    fun start() {
        if (running) return
        rows.clear()
        running = true
        status = "Loading ${engineId.displayName}…"

        activeJob = scope.launch {
            var engine: AsrEngine? = null
            var segmenter: VadSegmenter? = null
            try {
                val silero = ModelPaths.silero(context)
                check(silero != null && silero.isFile) {
                    "${ModelPaths.SILERO_VAD_FILE} is not on the phone. Run scripts/push_models.sh."
                }

                // Loading is hundreds of ms to seconds for the big models, so it
                // happens off the main thread even though the screen is blocked
                // on it anyway — a frozen UI reads as a crash.
                engine = withContext(Dispatchers.Default) {
                    AsrEngineHolder.create(context, engineId, numThreads)
                }
                segmenter = VadSegmenter(silero, numThreads)

                val source: AudioSource = if (sourceName == MIC_SOURCE) {
                    MicAudioSource()
                } else {
                    WavAssetAudioSource(context.assets, sourceName)
                }
                activeSource = source

                status = "Listening with ${engineId.displayName} (${numThreads} threads)…"
                AsrPipeline(engine, segmenter, extractor).stream(source).collect { rows += it }
                status = "Finished: ${rows.size} segment(s)."
            } catch (c: CancellationException) {
                status = "Cancelled."
                // Rethrown: swallowing it would leave this coroutine's parent
                // believing the child is still alive.
                throw c
            } catch (t: Throwable) {
                // Not rethrown. A missing model file or a mic held by another app
                // is an expected outcome on a debug screen, and crashing the Dev
                // menu would hide the message that says which one it was.
                status = "Failed: ${t.javaClass.simpleName}: ${t.message}"
            } finally {
                activeSource = null
                segmenter?.close()
                engine?.close()
                running = false
            }
        }
    }

    fun stop() {
        status = "Stopping…"
        // Stop the source, do not cancel the job: the VAD still holds the tail of
        // the current sentence and flush() is what emits it.
        activeSource?.stop()
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            start()
        } else {
            status = "RECORD_AUDIO refused. Choose a clip instead, or grant it in Settings."
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
        Text("Live ASR", style = VaakkuTypography.titleLarge, color = VaakkuTheme.colors.ink)
        Text(
            "mic or clip → Silero VAD → engine → SpokenExtractor",
            style = VaakkuTypography.labelMedium,
            color = VaakkuTheme.colors.inkSoft,
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = VaakkuTheme.colors.rule)
        Spacer(Modifier.height(16.dp))

        DevSection("engine")
        AsrEngineId.entries.forEach { id ->
            val availability = remember(id) { AsrEngineHolder.availability(context, id) }
            DevChoice(
                selected = id == engineId,
                enabled = !running && id.decodesSamples,
                label = "${id.displayName} — ${availability.summary}",
                onClick = { engineId = id },
            )
        }
        DevMono("Engine 5 is driven separately: it owns the mic and decodes no PCM.")

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

        Spacer(Modifier.height(14.dp))
        DevSection("source")
        DevChoice(
            selected = sourceName == MIC_SOURCE,
            enabled = !running,
            label = MIC_SOURCE,
            onClick = { sourceName = MIC_SOURCE },
        )
        clips.forEach { path ->
            DevChoice(
                selected = sourceName == path,
                enabled = !running,
                label = path.substringAfterLast('/'),
                onClick = { sourceName = path },
            )
        }
        if (clips.isEmpty()) DevMono("No clips in assets/testaudio/ — check syncBakeoffAudio ran.")

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    if (sourceName != MIC_SOURCE) {
                        start()
                    } else if (
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        start()
                    } else {
                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                enabled = !running,
                modifier = Modifier.weight(1f),
            ) {
                Text("Start")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = ::stop,
                enabled = running,
                modifier = Modifier.weight(1f),
            ) {
                Text("Stop")
            }
        }

        Spacer(Modifier.height(10.dp))
        DevMono(status)

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = VaakkuTheme.colors.rule)
        Spacer(Modifier.height(12.dp))

        DevSection("segments (${rows.size})")
        if (rows.isEmpty()) {
            DevMono("(none yet)")
        } else {
            rows.forEachIndexed { i, r -> SegmentBlock(i + 1, r) }
        }

        Spacer(Modifier.height(20.dp))
        OutlinedButton(
            onClick = {
                scope.launch {
                    val name = "G2_live_asr_${engineId.name}_${Iso8601.stamp()}.txt"
                    status = EvidenceExport.writeText(context, name, transcriptText(engineId, numThreads, sourceName, rows))
                }
            },
            enabled = rows.isNotEmpty() && !running,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Export transcript to Download/Vaakku/evidence/")
        }
        DevMono("Text only. Audio is never written to disk, in any build type.")

        Spacer(Modifier.height(20.dp))
        OutlinedButton(
            onClick = {
                activeSource?.stop()
                activeJob?.cancel()
                onClose()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Close")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SegmentBlock(index: Int, r: RecognisedSegment) {
    val s = r.segment
    DevMono("#$index  ${s.startMs}–${s.endMs} ms  q=%.3f".format(s.segmentQuality))
    DevMono("    decode=${r.decodeMs} ms  rtf=%.3f  ${if (r.rtf <= 0.5) "(within §11.5)" else "(over the §11.5 budget)"}".format(r.rtf))
    DevMono("    text: ${s.text.ifBlank { "(empty)" }}")
    if (r.observations.isEmpty()) {
        DevMono("    claims: (none)")
    } else {
        r.observations.forEach { DevMono("    claim: ${renderObservation(it)}") }
    }
    Spacer(Modifier.height(8.dp))
}

/**
 * One observation as a line.
 *
 * The value is rendered through [SlotChecker.describe] rather than a local
 * formatter, so this screen, the bake-off and `evalTranscripts` all print a claim
 * the same way. Three spellings of the same value is how two people compare two
 * runs and conclude they disagree when they do not.
 */
private fun renderObservation(o: Observation): String = buildString {
    append(o.type.name)
    append(" = ")
    append(SlotChecker.describe(o.type, listOf(o)))
    append("  conf=%.3f".format(o.confidence))
    if (o.hedged) append(" hedged")
    if (o.negated) append(" negated")
    if (o.conditional) append(" conditional")
    o.ambiguous?.let { append(" ambiguous=${it.name}") }
}

private fun transcriptText(
    engineId: AsrEngineId,
    numThreads: Int,
    sourceName: String,
    rows: List<RecognisedSegment>,
): String = buildString {
    appendLine("VAAKKU live ASR transcript (build plan §6.3, gate G2)")
    appendLine("captured_at: ${Iso8601.now()}")
    appendLine("engine: ${engineId.name} (${engineId.displayName})")
    appendLine("num_threads: $numThreads")
    appendLine("source: $sourceName")
    appendLine("segments: ${rows.size}")
    val audio = rows.sumOf { it.audioMs }
    val decode = rows.sumOf { it.decodeMs }
    appendLine("audio_ms: $audio  decode_ms: $decode  aggregate_rtf: ${if (audio > 0) "%.3f".format(decode.toDouble() / audio) else "n/a"}")
    appendLine()
    rows.forEachIndexed { i, r ->
        appendLine("#${i + 1} ${r.segment.startMs}-${r.segment.endMs} ms  q=%.3f  decode=${r.decodeMs} ms  rtf=%.3f".format(r.segment.segmentQuality, r.rtf))
        appendLine("  text: ${r.segment.text}")
        if (r.observations.isEmpty()) {
            appendLine("  claims: (none)")
        } else {
            r.observations.forEach { appendLine("  claim: ${renderObservation(it)}") }
        }
    }
    appendLine()
    appendLine("No audio was written to disk. This file is text only (CLAUDE.md #4).")
}

@Composable
internal fun DevSection(title: String) {
    Text(title, style = VaakkuTypography.labelMedium, color = VaakkuTheme.colors.inkSoft)
    Spacer(Modifier.height(4.dp))
}

@Composable
internal fun DevMono(line: String) {
    Text(line, style = MonoStyle, color = VaakkuTheme.colors.ink)
}

/**
 * A selectable row.
 *
 * A leading `>` marks the selection instead of a coloured highlight: CLAUDE.md #9
 * bans colour-coding states, and although that rule is about the buyer-facing
 * cards, keeping one convention across the whole app means nobody has to remember
 * which screens the rule applies to.
 */
@Composable
internal fun DevChoice(selected: Boolean, enabled: Boolean, label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
    ) {
        Text(
            text = (if (selected) "> " else "   ") + label,
            style = MonoStyle,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
