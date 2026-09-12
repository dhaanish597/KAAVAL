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
// Runs FixtureReportRunner.main() (domain/src/test/kotlin/.../fixtures/) on
// the TEST runtime classpath, because the fixtures themselves are test
// resources and the runner shares FixturePipeline with FixturesTest — one
// pipeline, exercised the same way by both the assertions and the report.
// The runner throws if any fixture assertion fails, so this task goes red
// exactly when :domain:test's FixturesTest would.
// ---------------------------------------------------------------------------
tasks.register<JavaExec>("fixtureReport") {
    group = "verification"
    description = "Runs every fixture and writes evidence/fixture_report.md (confusion matrix, DIFFERS precision/recall)."
    dependsOn("testClasses")
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("app.vaakku.domain.fixtures.FixtureReportRunnerKt")
    workingDir = rootProject.projectDir
}

// ---------------------------------------------------------------------------
// evalTranscripts — writes evidence/asr_slot_accuracy.md  (build plan §5.10
// item 10, §11.3 item 1)
//
// Runs EvalTranscriptsRunner.main() the same way. P1 has no ASR pre-screen
// transcripts yet (P2 produces evidence/asr_prescreen/<engine>/<file>.txt),
// so this honestly reports emptiness rather than inventing a number
// (CLAUDE.md #7/#8) — see the runner's own doc comment.
// ---------------------------------------------------------------------------
tasks.register<JavaExec>("evalTranscripts") {
    group = "verification"
    description = "Reads evidence/asr_prescreen + testdata/testaudio/labels.json and writes evidence/asr_slot_accuracy.md."
    dependsOn("testClasses")
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("app.vaakku.domain.fixtures.EvalTranscriptsRunnerKt")
    workingDir = rootProject.projectDir
}

// ---------------------------------------------------------------------------
// receiptFixture — writes evidence/receipt_fixture/*.json  (build plan §7.1, §7.4)
//
// The bridge between the Kotlin hash chain and the Node packet CLI. HashChainTest
// proves this code agrees with itself; that says nothing about whether a second
// implementation in another language produces the same 64 hex characters. So
// this task writes a receipt and a tampered copy of it, and
// `node tools/packet-cli/index.js --verify` re-canonicalizes and re-hashes them
// with its own code. The first must print INTEGRITY: PASSED and the second must
// not — and if the two canonicalizers ever disagree by one byte, the first one
// fails.
//
// Deterministic: every value in ReceiptFixtureRunner is a literal, so re-running
// this rewrites the same bytes. A diff here means something in the format moved.
// ---------------------------------------------------------------------------
tasks.register<JavaExec>("receiptFixture") {
    group = "verification"
    description = "Writes evidence/receipt_fixture/receipt.json and receipt_tampered.json for tools/packet-cli to verify."
    dependsOn("testClasses")
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("app.vaakku.domain.receipt.ReceiptFixtureRunnerKt")
    workingDir = rootProject.projectDir
    // Optional: <keystore.p12> <password>, which adds a real ECDSA signature.
    // scripts/receipt_fixture_signed.sh passes them; a bare run does not, and
    // then leaves receipt_signed.json alone rather than replacing it with an
    // unsigned file.
    if (project.hasProperty("keystore")) {
        args(project.property("keystore").toString(), project.property("keystorePassword").toString())
    }
}

