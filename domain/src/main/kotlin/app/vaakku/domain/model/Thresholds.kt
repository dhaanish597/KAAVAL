package app.vaakku.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Tunable numbers for the reconciler — build plan §5.7. The defaults are
 * starting points "to be calibrated" against real bake-off audio (§11.3);
 * they are not hard-coded into the reconciler so that calibration never
 * requires a code change, only a new JSON file.
 */
@Serializable
data class Thresholds(
    /** Below this, a spoken observation's confidence is too low to trust — UNCERTAIN. */
    val spokenMin: Double = 0.55,
    /** DIFFERS on a single mention is allowed only at or above this confidence. */
    val spokenStrong: Double = 0.80,
    /** Below this, a written observation's confidence is too low to trust — UNCERTAIN. */
    val writtenMin: Double = 0.70,
    /** Percentage-point tolerance when comparing a spoken rate to the written set. */
    val rateTolPp: Double = 0.10,
) {
    companion object {
        val DEFAULT = Thresholds()

        private val json = Json { ignoreUnknownKeys = true }

        /** Parses a JSON object; any field it omits keeps [DEFAULT]'s value. */
        fun loadFrom(text: String): Thresholds = json.decodeFromString(serializer(), text)

        /** Loads `thresholds.json` from the module's classpath resources, if present. */
        fun loadFromResourceOrDefault(
            resourcePath: String = "/thresholds.json",
            classLoader: ClassLoader = Thresholds::class.java.classLoader,
        ): Thresholds {
            val stream = classLoader.getResourceAsStream(resourcePath.removePrefix("/")) ?: return DEFAULT
            return stream.use { loadFrom(it.readBytes().toString(Charsets.UTF_8)) }
        }
    }
}
