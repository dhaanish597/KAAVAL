// Root build script.
//
// Plugins are declared here with `apply false` and applied in the module that
// needs them: :domain takes the JVM plugin, :app takes the Android + Compose
// plugins. Keeping the versions in one place means a module can never drift.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// ---------------------------------------------------------------------------
// Guard: checkBannedWords  (build plan §9 item 4)
//
// Scans app strings/resources, app Java/Kotlin, and domain main sources for the
// §2.4 banned-word list as whole words, case-insensitively.
//
// Why this exists at all: the load-bearing product rule is that VAAKKU never
// renders a verdict. Policy alone decays under deadline pressure, so the ban is
// a build failure instead. If anyone adds a verdict-flavoured string or field,
// ./gradlew checkBannedWords fails and the commit does not happen.
//
// Allow-list: only this file (it necessarily contains the list) and test
// sources. Tests are allowed to name the banned words in order to assert their
// absence — that is the schema-guard's whole job (§5.9).
// ---------------------------------------------------------------------------

// Banned words come in two matching strengths, and the distinction matters:
//
//  * PREFIX stems — the word may be followed by more letters, so inflections are
//    caught. "mislead" must also catch "misleading"/"misled"; "accus" must catch
//    "accuse"/"accusation"; "suspect" must catch "suspected"/"suspicious".
//    Matching only the literal §2.4 strings would let "misleading" through, and
//    that is the single most likely word to sneak into a string resource.
//
//  * WHOLE words — must NOT match as a prefix, because they are substrings of
//    ordinary words. "lie" is inside "believe", "field", "client"; "score" is
//    inside "underscore"; "risk" is inside "brisk". Prefix-matching these would
//    make the guard fire on innocent code until someone weakened it, which is
//    exactly the failure mode CLAUDE.md #1 warns about, so they are exact-only.
val bannedPrefixStems = listOf(
    "verdict", "judge", "judgment", "severity", "fraud", "scam",
    "suspicious", "suspect", "mis-sell", "missell", "mis_sell", "mislead",
    "cheat", "guilty", "accus", "danger", "warning", "alarm",
)

val bannedWholeWords = listOf(
    "score", "risk", "lie", "liar", "trust score",
)

// Colour-coding ban (CLAUDE.md #9 / build plan §2.4): no red/amber/green for a
// state. Only checked in the app's own sources, and only as colour-ish tokens,
// so that unrelated uses of the English words stay legal.
val bannedColorTokens = listOf(
    "Color.Red", "Color.Green", "Color.Yellow", "0xFFFF0000", "0xFF00FF00",
    "#FF0000", "#00FF00", "trafficlight", "traffic_light",
)

// "confidence %" must never be shown. The INTERNAL `confidence: Double` field is
// explicitly allowed (§2.4), so this is only checked as a display string.
val bannedDisplayStrings = listOf("confidence %", "confidence%")

// The roots checkBannedWords scans, before the existence filter.
//
// Kept separate from scanRoots() so the guard can tell "this root is
// deliberately absent" from "this root moved and nobody noticed" — see the
// positive control in the task below. A path here that stops existing is a
// build failure, not a silently shorter scan.
fun declaredScanRoots(): List<File> = listOf(
    file("app/src/main/res"),
    file("app/src/main/java"),
    // The debug source set ships in debug builds, which is what runs on the phone
    // during the event, so it is held to the same language rules as main.
    file("app/src/debug"),
    file("app/src/main/AndroidManifest.xml"),
    file("domain/src/main"),
    file("tools/packet-cli"),
)

fun scanRoots(): List<File> = declaredScanRoots().filter { it.exists() }

fun isAllowed(file: File): Boolean {
    val p = file.invariantSeparatorsPath
    // Test sources may name banned words in order to assert their absence.
    if (p.contains("/src/test/")) return true
    if (p.contains("/src/androidTest/")) return true
    // This script defines the list, so it contains every banned word.
    if (file.name == "build.gradle.kts" && file.parentFile == rootDir) return true
    return false
}

/**
 * Finds every occurrence of [stem] in [lower] that starts on a word boundary and,
 * when [allowSuffix] is false, also ends on one.
 *
 * @return one finding description per occurrence.
 */
fun matchAll(
    lower: String,
    stem: String,
    allowSuffix: Boolean,
    line: String,
    file: File,
    lineIndex: Int,
): List<String> {
    val hits = mutableListOf<String>()
    var idx = lower.indexOf(stem)
    while (idx >= 0) {
        val before = if (idx == 0) ' ' else lower[idx - 1]
        val afterIdx = idx + stem.length
        val after = if (afterIdx >= lower.length) ' ' else lower[afterIdx]
        val okBefore = !before.isLetterOrDigit() && before != '_'
        // Prefix stems accept a following letter ("misleading"); whole words do
        // not ("believe" must not match "lie").
        val okAfter = if (allowSuffix) true else (!after.isLetterOrDigit() && after != '_')
        if (okBefore && okAfter) {
            hits += "${file.invariantSeparatorsPath}:${lineIndex + 1}: banned word '$stem' -> ${line.trim().take(120)}"
        }
        idx = lower.indexOf(stem, idx + 1)
    }
    return hits
}

tasks.register("checkBannedWords") {
    group = "verification"
    description = "Fails if a banned word (verdict/risk/score/...) appears in app or domain sources."

    // Declare inputs so a source edit invalidates any cached result. Note this
    // task declares no outputs, so it re-runs on every invocation regardless —
    // which is the right behaviour for a guard.
    scanRoots().forEach { root ->
        if (root.isDirectory) {
            inputs.dir(root).withPropertyName("scan_dir_" + root.name)
        } else {
            inputs.file(root).withPropertyName("scan_file_" + root.name)
        }
    }

    doLast {
        val offenders = mutableListOf<String>()
        // Counted per root, not just in total: a single total cannot tell a
        // missing root from a smaller one. See the positive control below.
        val perRoot = linkedMapOf<File, Int>()

        scanRoots().forEach { root ->
            var rootCount = 0
            root.walkTopDown()
                .filter { it.isFile }
                .filter { it.extension.lowercase() in setOf("kt", "java", "xml", "json", "js", "ts", "html", "txt", "css") }
                .filter { !isAllowed(it) }
                .forEach { file ->
                    rootCount++
                    val lines = file.readLines()
                    lines.forEachIndexed { i, line ->
                        val lower = line.lowercase()

                        // Word-boundary-aware match. A stem must start at a
                        // boundary that is not itself a letter/digit, and the
                        // character after the match must be a boundary for the
                        // whole-word list (or anything for the prefix list).
                        bannedPrefixStems.forEach { stem ->
                            offenders += matchAll(lower, stem, allowSuffix = true, line, file, i)
                        }
                        bannedWholeWords.forEach { stem ->
                            offenders += matchAll(lower, stem, allowSuffix = false, line, file, i)
                        }

                        bannedDisplayStrings.forEach { s ->
                            if (lower.contains(s)) {
                                offenders += "${file.invariantSeparatorsPath}:${i + 1}: banned display string '$s' -> ${line.trim().take(120)}"
                            }
                        }

                        bannedColorTokens.forEach { tok ->
                            if (line.contains(tok)) {
                                offenders += "${file.invariantSeparatorsPath}:${i + 1}: state colour coding '$tok' -> ${line.trim().take(120)}"
                            }
                        }
                    }
                }
            perRoot[root] = rootCount
        }

        val scanned = perRoot.values.sum()

        if (offenders.isNotEmpty()) {
            logger.error("checkBannedWords FAILED — ${offenders.size} finding(s) across $scanned scanned file(s):")
            offenders.forEach { logger.error("  $it") }
            throw GradleException("checkBannedWords: ${offenders.size} banned word(s) found. VAAKKU never renders a verdict (CLAUDE.md #1).")
        }

        // POSITIVE CONTROL (decision 88). "0 findings" is the same output for
        // "nothing is wrong" and "nothing was looked at", and the second one is
        // what a guard looks like after someone moves a source root, renames a
        // source set, or adds a file extension this filter does not list.
        //
        // This is not hypothetical: check_manifest.sh printed "ok: INTERNET
        // absent from APK" on every run from P0 until it was rewritten, because
        // it ASCII-grepped a UTF-16LE binary manifest and matched nothing. The
        // guard was green for months and could not go red (open issue 30).
        //
        // Checked PER ROOT rather than against a total, because a total is far
        // too blunt: deleting app/src/main/java — 46 files, every Kotlin UI
        // string in the app — still leaves 54, which sails past any total floor
        // low enough to be safe from ordinary churn. That exact experiment was
        // run, and a total-only floor of 40 passed it. Each root must contribute
        // at least one file, and every declared root must still exist.
        //
        // If this fires, do NOT delete the root from the list to make it green —
        // find out where the files went.
        val vanished = declaredScanRoots().filterNot { it.exists() }
        val empty = perRoot.filterValues { it == 0 }.keys

        if (vanished.isNotEmpty() || empty.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("checkBannedWords is broken, not clean — it reported 0 findings ")
                    append("because part of the source tree was never read.\n")
                    if (vanished.isNotEmpty()) {
                        append("  declared roots that no longer exist:\n")
                        vanished.forEach { append("    ${it.invariantSeparatorsPath}\n") }
                    }
                    if (empty.isNotEmpty()) {
                        append("  roots that exist but contributed no scannable files:\n")
                        empty.forEach { append("    ${it.invariantSeparatorsPath}\n") }
                    }
                    append("  files scanned per root:\n")
                    perRoot.forEach { (root, n) -> append("    ${root.invariantSeparatorsPath}: $n\n") }
                },
            )
        }

        logger.lifecycle("checkBannedWords: clean ($scanned files scanned, 0 findings).")
    }
}
