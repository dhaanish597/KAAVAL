package app.vaakku.domain.fixtures

import app.vaakku.domain.extract.SpokenExtractor
import app.vaakku.domain.lexicon.LexiconLoader
import app.vaakku.domain.model.AsrSegment
import app.vaakku.domain.model.ClaimType
import app.vaakku.domain.normalize.Normalizer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Entry point for the `:domain:evalTranscripts` Gradle task (build plan
 * §5.10 item 10, §11.3 item 1). Reads `evidence/asr_prescreen/<engine>/<file>.txt`
 * (the laptop pre-screen's transcripts, P2's output) plus
 * `testdata/testaudio/labels.json`, runs each transcript through
 * SpokenExtractor exactly as [FixtureReportRunner] runs a fixture's spoken
 * text, and writes `evidence/asr_slot_accuracy.md` — slot accuracy per
 * engine per file.
 *
 * P1 has no transcripts yet (P2 produces them), so this honestly reports
 * that instead of inventing a number (CLAUDE.md #7/#8) — the computation
 * itself is exercised by [LabelsTest] and [SlotCheckerTest] against the
 * real labels.json, so "wired but no data yet" is a true statement, not an
 * unverified one.
 */
private data class SlotRow(val engine: String, val file: String, val correct: Int, val total: Int) {
    val accuracy: Double? get() = if (total == 0) null else correct.toDouble() / total
}

fun main() {
    val repoRoot = findRepoRoot()
    val prescreenDir = File(repoRoot, "evidence/asr_prescreen")
    val outFile = File(repoRoot, "evidence/asr_slot_accuracy.md")
    outFile.parentFile?.mkdirs()

    val engineDirs = prescreenDir.takeIf { it.isDirectory }
        ?.listFiles { f -> f.isDirectory }
        ?.filter { dir -> dir.listFiles { f -> f.extension == "txt" }?.isNotEmpty() == true }
        .orEmpty()
        .sortedBy { it.name }

    if (engineDirs.isEmpty()) {
        outFile.writeText(
            """
            # ASR slot accuracy — VAAKKU domain

            **Status: P1 placeholder. No ASR pre-screen transcripts exist yet.**

            `evidence/asr_prescreen/<engine>/<file>.txt` does not exist or has no
            engine subdirectory with any `.txt` files — the laptop pre-screen
            (build plan §11.3 item 1) is a P2 task. This is deliberately honest
            about being empty rather than printing an invented accuracy number
            (CLAUDE.md #7, #8).

            The computation itself is real and already exercised: `LabelsTest`
            (`:domain:test`) runs every one of `testdata/testaudio/labels.json`'s
            15 scripts through the exact same SpokenExtractor + SlotChecker path
            this task uses, and all of them pass today, text-only. Once P2 drops
            real transcripts under `evidence/asr_prescreen/<engine>/`, this task
            starts reporting real per-engine, per-file slot accuracy — no code
            change needed.
            """.trimIndent() + "\n",
        )
        println("evalTranscripts: wrote ${outFile.path} — no ASR pre-screen transcripts exist yet (P2).")
        return
    }

    val labelsFile = File(repoRoot, "testdata/testaudio/labels.json")
    val labels = Json { ignoreUnknownKeys = true }.decodeFromString(Labels.serializer(), labelsFile.readText(Charsets.UTF_8))
    val labelsByFile = labels.files.associateBy { it.file }

    val lexicon = LexiconLoader.loadDefault()
    val extractor = SpokenExtractor(lexicon, Normalizer(lexicon))

    val rows = mutableListOf<SlotRow>()
    val skipped = mutableListOf<String>()

    engineDirs.forEach { engineDir ->
        engineDir.listFiles { f -> f.extension == "txt" }.orEmpty().sortedBy { it.name }.forEach { txtFile ->
            val fileId = txtFile.nameWithoutExtension
            val label = labelsByFile[fileId]
            if (label == null) {
                skipped += "${engineDir.name}/${txtFile.name} (no labels.json entry for '$fileId')"
                return@forEach
            }
            val transcript = txtFile.readText(Charsets.UTF_8).trim()
            val observations = extractor.extract(AsrSegment(transcript, 0, 1000, engineDir.name, segmentQuality = 1.0))
            val correct = label.expectedClaims.count { (typeName, expected) ->
                SlotChecker.matches(ClaimType.valueOf(typeName), expected, observations)
            }
            rows += SlotRow(engineDir.name, fileId, correct, label.expectedClaims.size)
        }
    }

    val body = buildString {
        appendLine("# ASR slot accuracy — VAAKKU domain")
        appendLine()
        appendLine("Transcripts scored: ${rows.size}, across ${rows.map { it.engine }.distinct().size} engine(s).")
        appendLine()
        rows.groupBy { it.engine }.toSortedMap().forEach { (engine, engineRows) ->
            appendLine("## $engine")
            appendLine()
            appendLine("| file | correct / total | accuracy |")
            appendLine("|---|---|---|")
            engineRows.sortedBy { it.file }.forEach { row ->
                appendLine("| ${row.file} | ${row.correct} / ${row.total} | ${formatAccuracy(row.accuracy)} |")
            }
            val totalCorrect = engineRows.sumOf { it.correct }
            val totalSlots = engineRows.sumOf { it.total }
            appendLine()
            appendLine(
                "**$engine overall: $totalCorrect / $totalSlots" +
                    " (${formatAccuracy(if (totalSlots == 0) null else totalCorrect.toDouble() / totalSlots)})**",
            )
            appendLine()
        }
        if (skipped.isNotEmpty()) {
            appendLine("## Skipped (no labels.json entry)")
            skipped.forEach { appendLine("- $it") }
        }
    }

    outFile.writeText(body)
    println("evalTranscripts: wrote ${outFile.path} (${rows.size} transcript(s) scored, ${skipped.size} skipped).")
}

private fun formatAccuracy(rate: Double?): String = if (rate == null) "n/a (no expected slots)" else "%.0f%%".format(rate * 100)
