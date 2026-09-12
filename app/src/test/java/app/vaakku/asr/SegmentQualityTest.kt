package app.vaakku.asr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.pow

/**
 * `segmentQuality` is not cosmetic. `SpokenExtractor` sets
 * `confidence = matchQuality * segmentQuality`, and the reconciler will not let a
 * single mention read DIFFERS below `Thresholds.spokenStrong`. So these tests
 * pin the behaviour that decides whether the product speaks or stays silent.
 */
class SegmentQualityTest {

    /** A constant-amplitude signal has RMS == that amplitude, which makes dBFS exact. */
    private fun toneAt(dbfs: Double, n: Int = 8000): FloatArray {
        val amplitude = 10.0.pow(dbfs / 20.0).toFloat()
        return FloatArray(n) { if (it % 2 == 0) amplitude else -amplitude }
    }

    @Test
    fun `rms of a constant-amplitude signal is that amplitude, in dBFS`() {
        assertEquals(-20.0, SegmentQuality.rmsDbfs(toneAt(-20.0)), 1e-6)
        assertEquals(-40.0, SegmentQuality.rmsDbfs(toneAt(-40.0)), 1e-6)
    }

    @Test
    fun `digital silence is negative infinity, not zero dBFS`() {
        // Returning 0.0 here would read as "full scale" and score silence perfectly.
        assertEquals(Double.NEGATIVE_INFINITY, SegmentQuality.rmsDbfs(FloatArray(512)))
        assertEquals(Double.NEGATIVE_INFINITY, SegmentQuality.rmsDbfs(FloatArray(0)))
    }

    @Test
    fun `every level measured in our own recordings gets full energy credit`() {
        // evidence/asr_prescreen/vad_calibration.md: 24 real VAD segments, RMS
        // dBFS min -22.5, p10 -14.9, median -13.3. If any of these scored below
        // 1.0, the recordings the demo is built on would be penalised.
        listOf(-22.5, -14.9, -13.3, -11.8).forEach { db ->
            assertEquals(1.0, SegmentQuality.energyFactor(db), 1e-9, "$db dBFS")
        }
    }

    @Test
    fun `energy factor falls to zero for audio too quiet to trust`() {
        assertEquals(0.0, SegmentQuality.energyFactor(-50.0), 1e-9)
        assertEquals(0.0, SegmentQuality.energyFactor(-80.0), 1e-9)
        assertEquals(0.0, SegmentQuality.energyFactor(Double.NEGATIVE_INFINITY), 1e-9)
    }

    @Test
    fun `energy factor is linear in decibels between the anchors`() {
        // Halfway in dB between -50 and -30 is -40, so the factor is 0.5. Linear
        // in dB rather than in amplitude: loudness perception and mic gain are
        // both logarithmic, and an amplitude-linear ramp would collapse the whole
        // usable range into the top of the scale.
        assertEquals(0.5, SegmentQuality.energyFactor(-40.0), 1e-9)
        assertEquals(0.25, SegmentQuality.energyFactor(-45.0), 1e-9)
        assertEquals(0.75, SegmentQuality.energyFactor(-35.0), 1e-9)
    }

    @Test
    fun `energy factor never exceeds one, however loud`() {
        assertEquals(1.0, SegmentQuality.energyFactor(0.0), 1e-9)
        assertEquals(1.0, SegmentQuality.energyFactor(6.0), 1e-9)
    }

    @Test
    fun `quality is speech probability times the energy factor`() {
        assertEquals(0.9, SegmentQuality.of(meanSpeechProb = 0.9, samples = toneAt(-13.0)), 1e-9)
        // -40 dBFS halves it. Looser tolerance than the case above because this
        // one lands on the ramp rather than in the saturated region, so the Float
        // round-trip through the sample array actually shows up (1e-8 of it).
        assertEquals(0.45, SegmentQuality.of(meanSpeechProb = 0.9, samples = toneAt(-40.0)), 1e-6)
    }

    @Test
    fun `quality is clamped into zero to one even if a caller passes nonsense`() {
        assertEquals(0.0, SegmentQuality.of(meanSpeechProb = -0.5, samples = toneAt(-13.0)), 1e-9)
        assertEquals(1.0, SegmentQuality.of(meanSpeechProb = 1.4, samples = toneAt(-13.0)), 1e-9)
    }

    @Test
    fun `a clean loud segment can still reach the bar a DIFFERS card needs`() {
        // The integration constraint worth pinning: Thresholds.spokenStrong = 0.80
        // and FuzzyMatcher.EXACT_QUALITY = 0.95, so segmentQuality must clear
        // 0.8/0.95 = 0.842 or no single exact mention can ever produce DIFFERS.
        // Silero reports ~0.9+ on clear speech; this test fails loudly if the
        // energy anchors are ever tightened enough to close that path.
        val quality = SegmentQuality.of(meanSpeechProb = 0.90, samples = toneAt(-13.3))
        assertTrue(quality >= 0.80 / 0.95, "clean speech scored $quality, below the DIFFERS floor")
    }

    @Test
    fun `mean speech probability of an empty window list is zero, not NaN`() {
        // 0/0 would propagate NaN into confidence, and NaN comparisons are false,
        // which silently turns every threshold check into "fails" — the right
        // outcome for the wrong reason, and impossible to debug from a card.
        assertEquals(0.0, SegmentQuality.meanProbability(emptyList()), 1e-9)
        assertEquals(0.5, SegmentQuality.meanProbability(listOf(0.25f, 0.75f)), 1e-9)
    }
}
