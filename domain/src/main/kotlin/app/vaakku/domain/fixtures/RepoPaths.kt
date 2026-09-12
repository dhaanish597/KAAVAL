package app.vaakku.domain.fixtures

import java.io.File

/**
 * Finds the repo root by walking up from [start] looking for
 * `settings.gradle.kts`, so evidence-writing/reading code works the same
 * whether it is run by Gradle (JavaExec, `test`) or from an IDE, regardless
 * of which directory happens to be the process's working directory.
 */
fun findRepoRoot(start: File = File(".").absoluteFile): File {
    var dir: File? = start
    while (dir != null) {
        if (File(dir, "settings.gradle.kts").exists()) return dir
        dir = dir.parentFile
    }
    error("Could not locate the repo root (no settings.gradle.kts found above $start)")
}
