// :domain — pure Kotlin/JVM. All product logic lives here so it can be tested in
// seconds on the laptop with no phone and no Android SDK in the loop.
//
// HARD RULE (CLAUDE.md #5, build plan §5): nothing in this module may import
// android.*, androidx.*, ML Kit, sherpa-onnx or LiteRT. There is a unit test in
// this module that enforces it by reading the compiled classes, because a rule
// that is only in a document is not a rule.

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    // Pure-JVM JSON for the lexicon, Thresholds, fixtures and labels.json.
    // kotlinx.serialization is a Kotlin/JVM library — not an Android or ML
    // dependency — so it does not violate CLAUDE.md #5.
    implementation(libs.kotlinx.serialization.json)

    // JUnit 5 per §13 P0 ("Kotlin JVM library, JUnit 5").
    testImplementation(libs.junit.jupiter)
    // Gradle 9 no longer puts a launcher on the test runtime classpath
    // implicitly; without this, `test` fails with "no tests found".
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// ---------------------------------------------------------------------------
// fixtureReport — writes evidence/fixture_report.md  (build plan §5.10, §9 item 2)
//
// P0 placeholder. P0 creates no fixtures (P1 does), so this task would have
// nothing real to report. Rather than print invented numbers — which CLAUDE.md
// #7 and #8 forbid — it writes an honest "no fixtures yet" report and passes.
// P1 replaces this body with the real confusion matrix and DIFFERS precision.
// ---------------------------------------------------------------------------
tasks.register("fixtureReport") {
    group = "verification"
    description = "Writes evidence/fixture_report.md (P0: placeholder, real matrix lands in P1)."

    val outFile = rootProject.layout.projectDirectory.file("evidence/fixture_report.md")
    val fixtureDir = layout.projectDirectory.dir("src/test/resources/fixtures")
    inputs.dir(fixtureDir)

    doLast {
        val fixtures = fixtureDir.asFile.listFiles()?.filter { it.extension == "json" } ?: emptyList()
        val target = outFile.asFile
        target.parentFile.mkdirs()

        val body = if (fixtures.isEmpty()) {
            """
            # Fixture report — VAAKKU domain

            **Status: P0 placeholder. No fixtures exist yet, so there is nothing to report.**

            This task is deliberately honest about being empty. It exists in P0 only so
            that the pre-commit guard list (`:domain:test :domain:fixtureReport
            checkBannedWords`, CLAUDE.md "Always do") is runnable from the first commit.

            P1 builds the real thing: >= 26 fixtures (10 adversarial, 6 demo-path,
            6 normalization, 4 honest-agent) and this file becomes the confusion
            matrix of expected {MATCHES, NOT_IN_DOCUMENT, DIFFERS, SILENT} against
            actual, with **precision on DIFFERS printed (must be 1.00)**.

            Expected shape once P1 lands:

            | expected \ actual | MATCHES | NOT_IN_DOCUMENT | DIFFERS | SILENT |
            |---|---|---|---|---|
            | MATCHES | | | | |
            | NOT_IN_DOCUMENT | | | | |
            | DIFFERS | | | | |
            | SILENT | | | | |

            **Precision on DIFFERS: not yet measured.**
            """.trimIndent()
        } else {
            """
            # Fixture report — VAAKKU domain

            Fixture files found: ${fixtures.size}
            ${fixtures.joinToString("\n") { "- ${it.name}" }}

            **The comparison logic is not implemented yet (P1).** Fixtures exist but no
            matrix is computed, so no precision figure is claimed here.
            """.trimIndent()
        }

        target.writeText(body + "\n")
        logger.lifecycle("fixtureReport: wrote ${target.invariantSeparatorsPath} (${fixtures.size} fixture file(s) found).")
    }
}
