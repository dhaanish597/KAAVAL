package app.vaakku.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ThresholdsTest {

    @Test
    fun `defaults match build plan section 5_7`() {
        val t = Thresholds.DEFAULT
        assertEquals(0.55, t.spokenMin)
        assertEquals(0.80, t.spokenStrong)
        assertEquals(0.70, t.writtenMin)
        assertEquals(0.10, t.rateTolPp)
    }

    @Test
    fun `loads a full JSON object`() {
        val t = Thresholds.loadFrom(
            """{"spokenMin":0.6,"spokenStrong":0.85,"writtenMin":0.75,"rateTolPp":0.2}""",
        )
        assertEquals(0.6, t.spokenMin)
        assertEquals(0.85, t.spokenStrong)
        assertEquals(0.75, t.writtenMin)
        assertEquals(0.2, t.rateTolPp)
    }

    @Test
    fun `a partial JSON object keeps defaults for the fields it omits`() {
        val t = Thresholds.loadFrom("""{"spokenStrong":0.9}""")
        assertEquals(0.55, t.spokenMin)
        assertEquals(0.9, t.spokenStrong)
        assertEquals(0.70, t.writtenMin)
        assertEquals(0.10, t.rateTolPp)
    }

    @Test
    fun `a missing classpath resource falls back to defaults, not an exception`() {
        val t = Thresholds.loadFromResourceOrDefault("/does_not_exist_thresholds.json")
        assertEquals(Thresholds.DEFAULT, t)
    }
}
