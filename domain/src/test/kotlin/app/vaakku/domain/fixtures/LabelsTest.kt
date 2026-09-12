package app.vaakku.domain.fixtures

import app.vaakku.domain.extract.SpokenExtractor
import app.vaakku.domain.lexicon.LexiconLoader
import app.vaakku.domain.model.AsrSegment
import app.vaakku.domain.model.ClaimType
import app.vaakku.domain.normalize.Normalizer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File

/**
 * Validates `testdata/testaudio/labels.json` itself against the real
 * SpokenExtractor — every §11.2 script must actually produce the claims its
 * own label row expects, run text-only (as a laptop pre-screen transcript
 * would arrive, no audio, segmentQuality assumed 1.0). This is what
 * `:domain:evalTranscripts` will compare a real ASR engine's output
 * against once transcripts exist (§11.3).
 */
class LabelsTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val lexicon = LexiconLoader.loadDefault()
    private val extractor = SpokenExtractor(lexicon, Normalizer(lexicon))

    private fun loadLabels(): Labels {
        val file = File(findRepoRoot(), "testdata/testaudio/labels.json")
        assertTrue(file.exists(), "testdata/testaudio/labels.json is missing")
        return json.decodeFromString(Labels.serializer(), file.readText(Charsets.UTF_8))
    }

    @TestFactory
    fun `every labels_json script produces its own expected claims`(): List<DynamicTest> {
        val labels = loadLabels()
        assertTrue(labels.files.size >= 15, "expected >= 15 rows per build plan §11.2, found ${labels.files.size}")

        return labels.files.flatMap { entry ->
            val observations = extractor.extract(AsrSegment(entry.script, 0, 1000, "labels_test", segmentQuality = 1.0))
            entry.expectedClaims.map { (typeName, expected) ->
                DynamicTest.dynamicTest("${entry.file} / $typeName -> $expected") {
                    val type = ClaimType.valueOf(typeName)
                    assertTrue(
                        SlotChecker.matches(type, expected, observations),
                        "${entry.file}: expected $typeName=$expected, got ${observations.filter { it.type == type }}",
                    )
                }
            }
        }
    }

    @TestFactory
    fun `T11 deliberately produces no LOCK_IN claim (empty expectedClaims is intentional)`(): List<DynamicTest> {
        val labels = loadLabels()
        val t11 = labels.files.single { it.file == "T11_numbers" }
        return listOf(
            DynamicTest.dynamicTest("T11 expectedClaims is empty") {
                assertTrue(t11.expectedClaims.isEmpty())
            },
            DynamicTest.dynamicTest("T11 script really produces no LOCK_IN") {
                val observations = extractor.extract(AsrSegment(t11.script, 0, 1000, "labels_test", segmentQuality = 1.0))
                assertTrue(observations.none { it.type == ClaimType.LOCK_IN })
            },
        )
    }
}
