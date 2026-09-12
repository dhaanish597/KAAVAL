package app.vaakku.asr

/**
 * Everything downstream of here assumes 16 kHz mono float samples in -1..1.
 *
 * Fixed rather than negotiated: the Silero VAD is configured for 16 kHz, the
 * recognisers' feature extractors are configured for 16 kHz, and
 * `SegmentQuality`'s dBFS anchors were calibrated against 16 kHz recordings. A
 * source that quietly delivered another rate would not fail — it would produce
 * plausible-looking nonsense (see `tools/asr_prescreen/prescreen.py`, where a
 * mismatched rate turned Tamil into Japanese), so every source checks and throws.
 */
const val ASR_SAMPLE_RATE_HZ = 16_000

/**
 * A blocking source of PCM for the ASR pipeline — build plan §6.3.
 *
 * Two implementations: [MicAudioSource] (live AudioRecord) and
 * [WavAssetAudioSource] (a WAV from `assets/testaudio/`, for the bake-off and
 * the rehearsal fallback). They share one interface so the VAD, the engines and
 * the extractor cannot tell which one is feeding them — that is what makes a
 * bake-off result comparable to a live-mic result.
 *
 * **Audio never touches disk** (CLAUDE.md #4). No implementation writes samples
 * anywhere, and none offers a "save" option, in any build type.
 */
interface AudioSource {

    /** Short name for logs, evidence rows and the debug overlay. */
    val label: String

    /** Total duration in ms if known ahead of time (a file), or null (a mic). */
    val durationMs: Long?

    /** Opens the underlying device or stream. Throws if it cannot. */
    fun start()

    /**
     * Fills [into] with up to `into.size` samples.
     *
     * @return the number of samples written, or -1 when the stream has ended.
     *         A mic never returns -1 until [stop] is called.
     */
    fun read(into: FloatArray): Int

    /** Releases the device or stream. Safe to call twice. */
    fun stop()
}
