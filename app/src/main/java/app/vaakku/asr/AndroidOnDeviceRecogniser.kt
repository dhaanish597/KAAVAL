package app.vaakku.asr

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import app.vaakku.domain.extract.SpokenExtractor
import app.vaakku.domain.model.AsrSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Engine 5 of build plan §6.3: `SpeechRecognizer.createOnDeviceSpeechRecognizer()`.
 *
 * ### Why this is not an [AsrEngine]
 * It owns the microphone itself and hands back only text — it cannot be given
 * PCM, so it cannot be fed a WAV, so it cannot appear in the bake-off. There is
 * no configuration that makes it comparable; the honest thing is a separate class
 * with a separate output, which is what this is.
 *
 * ### It is already ruled out for Tamil on the demo phone
 * The Rung-0 probe (STATUS.md M1) found `ta-IN` absent from this phone's
 * on-device recogniser's 31 languages. This class exists anyway because §6.3
 * lists the engine, because `en-IN` *is* supported (though not installed), and
 * because a live A/B against sherpa on English is cheap once the code is here.
 * Run [Rung0Probe.run] before assuming anything about a different phone.
 *
 * ### Audio
 * `onBufferReceived` delivers raw audio and is deliberately ignored. Nothing here
 * retains or writes samples (CLAUDE.md #4).
 */
class AndroidOnDeviceRecogniser(
    private val context: Context,
    private val extractor: SpokenExtractor,
    private val languageTag: String = Rung0Probe.ENGLISH,
) {

    /**
     * Listens continuously, restarting after each utterance, until the collecting
     * coroutine is cancelled.
     *
     * Restarting is necessary rather than tidy: the platform recogniser ends its
     * session at each end-of-speech, and a seller's pitch is many utterances. The
     * gap between sessions is real dead time — one more reason this engine is not
     * measured against the sherpa ones on equal terms.
     */
    fun listen(): Flow<RecognisedSegment> = callbackFlow {
        val recognizer = withContext(Dispatchers.Main) {
            check(SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                "On-device recognition is unavailable on this device; engine 5 cannot run. " +
                    "See the Rung-0 probe screen."
            }
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        // Clock marks for the timing columns. Wall-clock, not audio-derived: this
        // engine never shows us the audio.
        var sessionStart = 0L
        var speechStart = 0L
        var speechEnd = 0L

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit

            override fun onBeginningOfSpeech() {
                speechStart = SystemClock.elapsedRealtime()
            }

            override fun onRmsChanged(rmsdB: Float) = Unit

            /** Raw audio. Ignored on purpose — see the class doc. */
            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                speechEnd = SystemClock.elapsedRealtime()
            }

            override fun onError(error: Int) {
                // ERROR_NO_MATCH and the speech timeout are normal in a pause and
                // must not end the session: they mean "nothing said", which is a
                // silence, and silence is the correct output (CLAUDE.md #2).
                val transient = error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                if (transient) {
                    restart()
                } else {
                    close(IllegalStateException("On-device recogniser error $error."))
                }
            }

            override fun onResults(results: Bundle?) {
                emitResult(results)
                restart()
            }

            /**
             * Partial results are dropped.
             *
             * A partial hypothesis is routinely revised — "pathinaindhu" can
             * become "pathinaaru" a word later. Extracting claims from partials
             * would put a number on screen that then changes, which is the one
             * thing this product must never do.
             */
            override fun onPartialResults(partialResults: Bundle?) = Unit

            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            private fun emitResult(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (text.isEmpty()) return

                val now = SystemClock.elapsedRealtime()
                val startedAt = if (speechStart > 0L) speechStart else sessionStart
                val endedAt = if (speechEnd > speechStart) speechEnd else now
                val audioMs = (endedAt - startedAt).coerceAtLeast(0L)

                val segment = AsrSegment(
                    text = text,
                    startMs = startedAt - sessionStart,
                    endMs = endedAt - sessionStart,
                    engine = AsrEngineId.ANDROID_ON_DEVICE.name,
                    segmentQuality = UNMEASURED_SEGMENT_QUALITY,
                )
                trySend(
                    RecognisedSegment(
                        segment = segment,
                        observations = extractor.extract(segment),
                        decodeMs = (now - endedAt).coerceAtLeast(0L),
                        audioMs = audioMs,
                    ),
                )
            }

            private fun restart() {
                speechStart = 0L
                speechEnd = 0L
                // startListening is @MainThread and the callbacks already arrive
                // on the main thread, so this is a direct call, not a post().
                runCatching { recognizer.startListening(intent) }
                    .onFailure { close(it) }
            }
        }

        withContext(Dispatchers.Main) {
            recognizer.setRecognitionListener(listener)
            sessionStart = SystemClock.elapsedRealtime()
            recognizer.startListening(intent)
        }

        awaitClose {
            // No withContext here: awaitClose's block is not a suspend scope, and
            // cancel/destroy on a recognizer created on Main must also run there.
            android.os.Handler(context.mainLooper).post {
                runCatching { recognizer.cancel() }
                runCatching { recognizer.destroy() }
            }
        }
    }.flowOn(Dispatchers.Main)

    companion object {
        /**
         * The `segmentQuality` used when the audio could not be measured.
         *
         * This engine never shows us its samples, so neither half of
         * [SegmentQuality] — VAD speech probability and RMS level — can be
         * computed. Rather than invent a number, the value is pinned where its
         * consequences are known and conservative:
         *
         *  - above `Thresholds.spokenMin` (0.55), so a claim heard here still
         *    enters the ledger and can still reach NOT_IN_DOCUMENT;
         *  - below the ~0.842 that an exact lexicon match (quality 0.95) needs to
         *    clear `spokenStrong` (0.80), so **one** unmeasured mention can never
         *    on its own produce a DIFFERS card.
         *
         * A claim said twice still can (`Reconciler` accepts `mentionCount >= 2`
         * in place of a strong single mention), which is the right behaviour:
         * repetition is evidence we actually observed, unlike audio level we
         * never saw. This is CLAUDE.md #2 applied to a missing measurement.
         */
        const val UNMEASURED_SEGMENT_QUALITY = 0.80
    }
}
