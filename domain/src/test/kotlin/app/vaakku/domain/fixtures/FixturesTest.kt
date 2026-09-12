package app.vaakku.domain.fixtures

import app.vaakku.domain.model.ClaimType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File

/**
 * Build plan §5.10 — runs every fixture in `src/test/resources/fixtures` (one
 * JSON file each) through the real pipeline (SpokenExtractor -> WrittenExtractor -> Reconciler)
 * and checks every state the fixture asserts. One [DynamicTest] per
 * (fixture, claim type) assertion, so a failure names exactly which cell of
 * the confusion matrix broke — the same thing `fixtureReport` computes.
 */
class FixturesTest {

    @TestFactory
    fun `every fixture assertion holds`(): List<DynamicTest> {
        val fixtures = loadAllFixtures()
        assertTrue(fixtures.size >= 26, "expected >= 26 fixtures per build plan §5.10, found ${fixtures.size}")

        return fixtures.flatMap { fixture ->
            val actual = FixturePipeline.run(fixture)
            fixture.expect.map { (typeName, expectedState) ->
                DynamicTest.dynamicTest("${fixture.id} / $typeName -> $expectedState") {
                    val type = ClaimType.valueOf(typeName)
                    assertEquals(expectedState, actual[type], "fixture ${fixture.id}, type $typeName")
                }
            }
        }
    }

    @TestFactory
    fun `honest-agent fixtures produce zero DIFFERS across ALL six claim types, not only the ones asserted`(): List<DynamicTest> {
        val honestFixtures = loadAllFixtures().filter { it.id.startsWith("H0") }
        assertTrue(honestFixtures.size >= 4, "expected >= 4 honest-agent fixtures per build plan §5.10, found ${honestFixtures.size}")

        return honestFixtures.map { fixture ->
            DynamicTest.dynamicTest(fixture.id) {
                val actual = FixturePipeline.run(fixture)
                val differsTypes = actual.filterValues { it == "DIFFERS" }.keys
                assertTrue(differsTypes.isEmpty(), "${fixture.id} produced DIFFERS for $differsTypes — an honest script must produce zero DIFFERS")
            }
        }
    }

    private fun loadAllFixtures(): List<Fixture> {
        val classLoader = Thread.currentThread().contextClassLoader
        val roots = classLoader.getResources("fixtures").toList()
            .mapNotNull { runCatching { File(it.toURI()) }.getOrNull() }
            .filter { it.isDirectory }
        val files = roots.flatMap { root -> root.listFiles { f -> f.extension == "json" }?.toList() ?: emptyList() }
            .distinctBy { it.name }
        return files.map { FixturePipeline.parse(it.readText(Charsets.UTF_8)) }
    }
}
