package app.vaakku.asr

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Build plan §6.3: `segmentQuality` = mean VAD speech probability x a clipped
 * energy factor.
 *
 * This number is load-bearing. `SpokenExtractor` sets
 * `confidence = matchQuality * segmentQuality`, and `Reconciler` refuses DIFFERS
 * on a single mention below `Thresholds.spokenStrong` (0.80). With
 * `FuzzyMatcher.EXACT_QUALITY` at 0.95, a segment scoring under **0.842** can
 * never produce a DIFFERS card no matter how clearly the words were said. An
 * energy curve that is pessimistic by a couple of tenths does not lower
 * confidence a little — it silences the product.
 *
 * **What this can and cannot tell you.** It measures whether a segment was loud
 * enough and speech-like enough to be worth believing. It says nothing about
 * whether the words came back right: a loud, confidently-voiced segment that the
 * recogniser mangled still scores high. Guarding against mangled text is the
 * lexicon's and the reconciler's job. So the energy factor is deliberately
 * narrow — "too quiet to trust at all" — rather than pretending to grade a
 * quality it cannot see.
 *
 * No `android.*` imports here on purpose: this is the one part of the ASR
 * subsystem that can be tested without a phone, and the phone is the scarce
 * resource.
 */
object SegmentQuality {

    /**
     * Loudness at or above which a segment gets full energy credit, in dBFS.
     *
     * Measured, not guessed. `evidence/asr_prescreen/vad_calibration.md` ran the
     * Silero VAD over all 15 recordings and reported 24 speech segments with RMS
     * dBFS of min -22.5, p10 -14.9, median -13.3. This anchor sits ~7 dB below
     * the quietest real segment, which is margin for the fact that those clips
     * were recorded by a phone recorder app (almost certainly with its own gain
     * control) while [MicAudioSource] reads AudioRecord directly, and a speaker
     * one metre from the phone in a hall will be quieter than these were.
     */
    const val FULL_CREDIT_DBFS = -30.0

    /**
     * Loudness at or below which the energy factor is zero, in dBFS.
     *
     * -50 dBFS is roughly room tone with nobody speaking. A segment down here is
     * either the VAD firing on a cough two tables away or a mic that is covered;
     * either way the right answer is silence, which is what a zero here produces
     * (CLAUDE.md #2).
     */
    const val NO_CREDIT_DBFS = -50.0

    /**
     * RMS of [samples] in dBFS, or [Double.NEGATIVE_INFINITY] for digital silence.
     *
     * Negative infinity rather than 0.0: dBFS is a logarithmic scale where 0.0
     * means *full scale*, so returning it for an all-zero buffer would rate
     * silence as the loudest possible signal.
     */
    fun rmsDbfs(samples: FloatArray): Double {
        if (samples.isEmpty()) return Double.NEGATIVE_INFINITY
        var sumSquares = 0.0
        for (s in samples) sumSquares += s.toDouble() * s.toDouble()
        val rms = sqrt(sumSquares / samples.size)
        if (rms <= 0.0) return Double.NEGATIVE_INFINITY
        return 20.0 * log10(rms)
    }

    /**
     * Maps loudness to 0..1, linearly in decibels between [NO_CREDIT_DBFS] and
     * [FULL_CREDIT_DBFS], clipped at both ends — the "clipped energy factor"
     * §6.3 asks for.
     *
     * Linear in dB rather than in amplitude because both loudness perception and
     * microphone gain are logarithmic; an amplitude-linear ramp would put every
     * realistic speech level into the top few percent of the scale and make the
     * factor useless.
     */
    fun energyFactor(dbfs: Double): Double = when {
        dbfs.isNaN() -> 0.0
        dbfs >= FULL_CREDIT_DBFS -> 1.0
        dbfs <= NO_CREDIT_DBFS -> 0.0
        else -> (dbfs - NO_CREDIT_DBFS) / (FULL_CREDIT_DBFS - NO_CREDIT_DBFS)
    }

    /**
     * Mean of the per-window speech probabilities the VAD reported for a segment.
     *
     * Returns 0.0 for an empty list rather than dividing by zero: a NaN here
     * would flow into `confidence`, and every NaN comparison is false, so each
     * threshold check would quietly "fail" for a reason invisible from the card.
     */
    fun meanProbability(windowProbabilities: List<Float>): Double {
        if (windowProbabilities.isEmpty()) return 0.0
        return windowProbabilities.sumOf { it.toDouble() } / windowProbabilities.size
    }

    /** The §6.3 product, clamped to 0..1. */
    fun of(meanSpeechProb: Double, samples: FloatArray): Double {
        val prob = meanSpeechProb.coerceIn(0.0, 1.0)
        return (prob * energyFactor(rmsDbfs(samples))).coerceIn(0.0, 1.0)
    }
}
