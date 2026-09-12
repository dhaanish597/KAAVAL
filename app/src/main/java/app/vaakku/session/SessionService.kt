package app.vaakku.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.vaakku.MainActivity
import app.vaakku.R
import app.vaakku.asr.AsrEngine
import app.vaakku.asr.AsrEngineHolder
import app.vaakku.asr.AsrEngineId
import app.vaakku.asr.AsrPipeline
import app.vaakku.asr.MicAudioSource
import app.vaakku.asr.ModelPaths
import app.vaakku.asr.VadSegmenter
import app.vaakku.domain.extract.SpokenExtractor
import app.vaakku.domain.lexicon.LexiconLoader
import app.vaakku.domain.normalize.Normalizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The session's microphone, as a foreground service — build plan §6.7.
 *
 * ### Why a service at all
 *
 * The buyer puts the phone down on the table. The screen turns off, OriginOS
 * starts trimming background work, and a pipeline owned by an activity stops
 * mid-sentence. A `foregroundServiceType="microphone"` service is the only thing
 * Android promises to keep running while the mic is open, and the notification
 * it is obliged to post is a feature here rather than a cost: the person across
 * the table can see, on the status bar, that the mic is on and that nothing is
 * being recorded.
 *
 * ### What it owns and what it does not
 *
 * It owns the engine, the VAD and the microphone, and nothing else. Every
 * recognised segment goes straight to [SessionRuntime], which owns the ledger —
 * so the camera half of the session reconciles against the same state without
 * this class knowing the camera exists.
 *
 * ### Audio
 *
 * Samples travel from `AudioRecord` into the VAD into the recogniser and are
 * dropped. Nothing is buffered across segments and **nothing is ever written to
 * disk**, in any build type (CLAUDE.md #4). There is no code path here that
 * opens a file for audio, and there must never be one.
 *
 * ### Stopping
 *
 * [ACTION_STOP] stops the *audio source*, not the coroutine. Cancelling would
 * resume the collector immediately and throw away the segment still inside the
 * VAD — in a sales pitch that is the sentence that closes. Stopping the source
 * makes `read` return -1, the loop exits normally, and `flush()` emits the tail.
 */
class SessionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var listenJob: Job? = null

    /** Held so [ACTION_STOP] can end the stream at the source. See the class KDoc. */
    @Volatile
    private var source: MicAudioSource? = null

    /** Nothing binds to this service; the UI observes [SessionRuntime.state]. */
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startListening()
            ACTION_STOP -> stopListening()
            // A null action is a restart delivery. START_NOT_STICKY means the
            // system should not be doing that; if it happens anyway, stopping is
            // the only correct answer — see the return value below.
            else -> stopSelf()
        }

        // START_NOT_STICKY, deliberately. A session that was killed must never
        // come back by itself: the microphone would reopen with nobody present
        // to have consented to it, and the notification would appear over
        // whatever the user is doing instead. A session begins because a person
        // tapped "start", or it does not begin.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        source?.stop()
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------

    private fun startListening() {
        if (listenJob?.isActive == true) return

        // Foreground first, listening second: Android gives a service a few
        // seconds to post its notification and kills it if it does not, and the
        // engine load below takes longer than that.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )

        listenJob = scope.launch { listen() }
    }

    private fun stopListening() {
        // Not stopSelf() and not a cancel: end the audio, let the stream finish,
        // and let `listen`'s own completion path shut the service down.
        source?.stop()
    }

    private suspend fun listen() {
        val engineId = AsrEngineId.DEFAULT
        var engine: AsrEngine? = null
        var segmenter: VadSegmenter? = null
        try {
            val silero = ModelPaths.silero(this)
            check(silero != null && silero.isFile) {
                "${ModelPaths.SILERO_VAD_FILE} is not on this phone. Run scripts/push_models.sh."
            }

            SessionRuntime.engineLoading(engineId.displayName)
            val threads = VadSegmenter.DEFAULT_NUM_THREADS
            engine = AsrEngineHolder.create(this, engineId, threads)
            segmenter = VadSegmenter(silero, threads)

            // The extractor is built here rather than held as a field: it loads
            // the lexicon, and doing that at construction would make a service
            // that fails to start before it has anywhere to report the failure.
            val extractor = LexiconLoader.loadDefault().let { SpokenExtractor(it, Normalizer(it)) }

            val mic = MicAudioSource()
            source = mic
            SessionRuntime.micOpen()

            AsrPipeline(engine, segmenter, extractor).stream(mic).collect { SessionRuntime.spoken(it) }
        } catch (c: CancellationException) {
            // Rethrown: swallowing it would leave the scope believing this child
            // is still alive.
            throw c
        } catch (t: Throwable) {
            // Not rethrown. A model that was not pushed and a microphone held by
            // another app are both ordinary outcomes at an event, and the phone
            // has to be able to say which one it was without a laptop attached.
            SessionRuntime.failed("${t.javaClass.simpleName}: ${t.message}")
        } finally {
            source = null
            segmenter?.close()
            engine?.close()
            SessionRuntime.endSession(System.currentTimeMillis())
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // ------------------------------------------------------------------

    /**
     * The one notification, in both languages at once (§6.7).
     *
     * `IMPORTANCE_LOW` because CLAUDE.md #9 is absolute: **no sound, ever**. Low
     * also suppresses the heads-up banner, so the notification cannot cover the
     * one large line the Session screen exists to show.
     *
     * `setOngoing` keeps it undismissable while the mic is open — the person
     * being recorded must not be able to lose sight of that by swiping, and
     * neither must the buyer.
     */
    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.session_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.session_channel_description)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            },
        )

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_session_mic)
            .setContentTitle(getString(R.string.session_notification_title))
            .setContentText(getString(R.string.session_notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            // Visible on the lock screen on purpose: "the mic is open and audio
            // is not stored" is exactly the sentence that should survive the
            // screen turning off.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val ACTION_START = "app.vaakku.session.START"
        const val ACTION_STOP = "app.vaakku.session.STOP"

        private const val CHANNEL_ID = "vaakku_session"
        private const val NOTIFICATION_ID = 1

        /**
         * Starts listening. **The caller must be in the foreground**: Android
         * refuses a microphone-type foreground service started from the
         * background, which is the correct rule — a session begins with a person
         * looking at the phone.
         */
        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, SessionService::class.java).setAction(ACTION_START),
            )
        }

        /** Ends the audio and lets the last sentence out of the VAD before stopping. */
        fun stop(context: Context) {
            context.startService(
                Intent(context, SessionService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
