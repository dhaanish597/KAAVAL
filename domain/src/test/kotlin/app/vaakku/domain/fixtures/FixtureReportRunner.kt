package app.vaakku.domain.fixtures

import app.vaakku.domain.model.ClaimType
import java.io.File

/**
 * Entry point for the `:domain:fixtureReport` Gradle task (build plan
 * §5.10, §9 item 2). Runs every fixture through [FixturePipeline], builds
 * the confusion matrix of expected vs. actual USER-FACING state, and writes
 * `evidence/fixture_report.md` with precision and recall on DIFFERS.
 * Deliberately honest if there is nothing to report (CLAUDE.md #7/#8):
 * with zero fixtures found it says so instead of printing an invented 1.00.
 */

private data class Cell(val fixtureId: String, val type: String, val expected: String, val actual: String)

private val LABELS = listOf("MATCHES", "NOT_IN_DOCUMENT", "DIFFERS", "SILENT")

fun main() {
    val fixtures = loadFixturesFromClasspath()
    val outFile = File(findRepoRoot(), "evidence/fixture_report.md")
    outFile.parentFile?.mkdirs()

    if (fixtures.isEmpty()) {
        outFile.writeText(
            """
            # Fixture report — VAAKKU domain

            **No fixtures were found on the test classpath.** This would mean the
            fixtureReport task itself is broken (fixtures exist in the repo as of
            P1) — treat this file as a failing signal, not a clean report.
            """.trimIndent() + "\n",
        )
        println("fixtureReport: WROTE AN ERROR REPORT — zero fixtures found. See ${outFile.path}.")
        return
    }

    val cells = mutableListOf<Cell>()
    fixtures.forEach { fixture ->
        val actual = FixturePipeline.run(fixture)
        fixture.expect.forEach { (typeName, expected) ->
            val type = ClaimType.valueOf(typeName)
            cells += Cell(fixture.id, typeName, expected, actual[type] ?: "SILENT")
        }
    }

    val matrix = LABELS.associateWith { expected ->
        LABELS.associateWith { actual -> cells.count { it.expected == expected && it.actual == actual } }
    }

    val tp = matrix.getValue("DIFFERS").getValue("DIFFERS")
    val fp = LABELS.filter { it != "DIFFERS" }.sumOf { matrix.getValue(it).getValue("DIFFERS") }
    val fn = LABELS.filter { it != "DIFFERS" }.sumOf { matrix.getValue("DIFFERS").getValue(it) }
    val precision = if (tp + fp == 0) null else tp.toDouble() / (tp + fp)
    val recall = if (tp + fn == 0) null else tp.toDouble() / (tp + fn)

    val mismatches = cells.filter { it.expected != it.actual }

    val body = buildString {
        appendLine("# Fixture report — VAAKKU domain")
        appendLine()
        appendLine("Fixtures run: ${fixtures.size}. Assertions checked: ${cells.size}.")
        appendLine()
        appendLine("| expected \\ actual | MATCHES | NOT_IN_DOCUMENT | DIFFERS | SILENT |")
        appendLine("|---|---|---|---|---|")
        LABELS.forEach { expected ->
            append("| $expected |")
            LABELS.forEach { actual -> append(" ${matrix.getValue(expected).getValue(actual)} |") }
            appendLine()
        }
        appendLine()
        appendLine("**Precision on DIFFERS: ${formatRate(precision)}** (TP=$tp, FP=$fp)")
        appendLine()
        appendLine("Recall on DIFFERS: ${formatRate(recall)} (TP=$tp, FN=$fn) — reported per §5.10, not required.")
        appendLine()
        if (mismatches.isEmpty()) {
            appendLine("All ${cells.size} fixture assertions passed.")
        } else {
            appendLine("## Mismatches (${mismatches.size})")
            mismatches.forEach { appendLine("- ${it.fixtureId} / ${it.type}: expected ${it.expected}, got ${it.actual}") }
        }
    }

    outFile.writeText(body)
    println(
        "fixtureReport: wrote ${outFile.path} " +
            "(${fixtures.size} fixtures, ${cells.size} assertions, " +
            "precision=${formatRate(precision)}, recall=${formatRate(recall)}, mismatches=${mismatches.size})",
    )
    if (mismatches.isNotEmpty()) {
        throw IllegalStateException("fixtureReport: ${mismatches.size} fixture assertion(s) failed — see ${outFile.path}")
    }
}

private fun formatRate(rate: Double?): String = if (rate == null) "n/a" else "%.2f".format(rate)

private fun loadFixturesFromClasspath(): List<Fixture> {
    val classLoader = Thread.currentThread().contextClassLoader
    val roots = classLoader.getResources("fixtures").toList()
        .mapNotNull { runCatching { File(it.toURI()) }.getOrNull() }
        .filter { it.isDirectory }
    val files = roots.flatMap { root -> root.listFiles { f -> f.extension == "json" }?.toList() ?: emptyList() }
        .distinctBy { it.name }
        .sortedBy { it.name }
    return files.map { FixturePipeline.parse(it.readText(Charsets.UTF_8)) }
}
