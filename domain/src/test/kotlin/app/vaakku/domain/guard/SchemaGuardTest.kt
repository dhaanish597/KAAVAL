package app.vaakku.domain.guard

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File

/**
 * Build plan §5.9 — structural enforcement of "no verdict." Reflects over
 * every MAIN (never test) class actually compiled into `app.vaakku.domain`
 * and fails if any class, field, method or enum-constant name matches the
 * §5.9 banned-identifier pattern. This is deliberately independent of the
 * root `checkBannedWords` Gradle task (which scans UI strings/resources,
 * word-boundary-aware): this guard is a plain substring match against
 * *identifiers*, because we control every identifier name ourselves and the
 * bar for "never even name a field this" is stricter than the bar for
 * "never let this word reach a Tamil UI string."
 *
 * "If anyone adds a verdict field, the build fails" is only true if this
 * test actually finds classes to scan — [schema guard scanner finds a
 * healthy number of real domain classes] guards against the scanner itself
 * silently finding nothing and passing vacuously.
 */
class SchemaGuardTest {

    private val bannedPattern = Regex(
        "(risk|score|severity|fraud|verdict|likelihood|suspicious|mislead|missell|mis_sell|guilty|accus)",
        RegexOption.IGNORE_CASE,
    )

    @org.junit.jupiter.api.Test
    fun `no class, field, method or enum constant in app_vaakku_domain main matches the banned pattern`() {
        val classes = discoverMainDomainClasses()
        assertTrue(classes.size >= 20, "schema guard scanner found only ${classes.size} classes — it may be broken, not passing honestly")

        val offenders = mutableListOf<String>()
        for (klass in classes) {
            val simpleName = klass.simpleName ?: klass.name
            if (bannedPattern.containsMatchIn(simpleName)) offenders += "class ${klass.name}"

            runCatching { klass.declaredFields }.getOrNull()?.forEach { f ->
                if (bannedPattern.containsMatchIn(f.name)) offenders += "field ${klass.name}.${f.name}"
            }
            runCatching { klass.declaredMethods }.getOrNull()?.forEach { m ->
                if (bannedPattern.containsMatchIn(m.name)) offenders += "method ${klass.name}.${m.name}"
            }
            if (klass.isEnum) {
                klass.enumConstants?.forEach { c ->
                    val name = (c as? Enum<*>)?.name ?: c.toString()
                    if (bannedPattern.containsMatchIn(name)) offenders += "enum constant ${klass.name}.$name"
                }
            }
        }

        assertTrue(
            offenders.isEmpty(),
            "Schema guard found banned identifier(s) — VAAKKU never renders a verdict (CLAUDE.md #1):\n" +
                offenders.joinToString("\n"),
        )
    }

    /** The pattern itself, checked against the exact §5.9 word list, so the test-of-the-test is explicit. */
    @ParameterizedTest
    @ValueSource(
        strings = [
            "risk", "score", "severity", "fraud", "verdict", "likelihood",
            "suspicious", "mislead", "missell", "mis_sell", "guilty", "accus",
            "Risk", "VERDICT", "trustScore", "riskLevel", "isSuspicious",
        ],
    )
    fun `the banned pattern matches every §5_9 word and identifiers built from them`(word: String) {
        assertTrue(bannedPattern.containsMatchIn(word), "expected '$word' to be flagged")
    }

    private fun discoverMainDomainClasses(): List<Class<*>> {
        val basePackage = "app.vaakku.domain"
        val path = basePackage.replace('.', '/')
        val classLoader = Thread.currentThread().contextClassLoader

        val mainRoots = classLoader.getResources(path).toList()
            .mapNotNull { runCatching { File(it.toURI()) }.getOrNull() }
            .filter { it.isDirectory && it.path.replace('\\', '/').contains("/main/") }

        val names = sortedSetOf<String>()
        mainRoots.forEach { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "class" }.forEach { file ->
                val relative = file.relativeTo(root).path.removeSuffix(".class").replace(File.separatorChar, '.')
                names += "$basePackage.$relative"
            }
        }

        return names.mapNotNull { name -> runCatching { Class.forName(name, false, classLoader) }.getOrNull() }
    }
}
