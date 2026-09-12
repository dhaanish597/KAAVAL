package app.vaakku.asr

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.provider.Settings
import android.speech.RecognitionService
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Rung-0 probe (build plan §6.3, FINAL LOCKED SPEC §5 "ASR go/no-go tree, Rung 0").
 *
 * ## Why this exists
 *
 * Offline code-switched Tamil ASR is the one unknown that can sink this project.
 * Rung 0 is the cheapest possible test of it: before loading a single sherpa-onnx
 * model, ask the platform directly.
 *
 *   1. Is on-device (offline) recognition available at all on this device?
 *   2. Which recognition services are installed — Google's, vivo's, both?
 *   3. For ta-IN and en-IN, does a recognizer claim support, and is that support
 *      already *installed* or merely *pending* (i.e. needs a download)?
 *   4. Can a Tamil model be pulled down ahead of the event?
 *
 * ## Why both recognizers are queried
 *
 * The *default* recognizer (`createSpeechRecognizer`) and the *on-device*
 * recognizer (`createOnDeviceSpeechRecognizer`) can be different services with
 * different language sets. On a vivo ROM the default may be Jovi while the
 * on-device one is Google's — engine 5 in §6.3 uses the on-device one. Asking
 * only the default could report "Tamil unsupported" for a device that supports
 * it perfectly well offline, and we would abandon a working path. So both are
 * queried and reported separately.
 *
 * ## What this deliberately does NOT do
 *
 * It does not start listening and does not touch the microphone. A support query
 * is a metadata question. Nothing here records, buffers or writes audio, which
 * keeps it consistent with CLAUDE.md #4 and lets the probe run before
 * RECORD_AUDIO is granted.
 *
 * Every call that can fail is caught and recorded as text rather than thrown: on
 * an unverified vendor ROM, "this call threw UnsupportedOperationException" is
 * itself a finding worth keeping.
 *
 * NOTE: `isRecognitionAvailable()` depends on the `<queries>` element in
 * AndroidManifest.xml. Without it, package-visibility filtering makes it return
 * false on a device that has a recognizer. Do not remove that block.
 */

/** One language's support status, as a single recognizer reported it. */
data class LanguageSupport(
    val tag: String,
    val supportedOnDevice: List<String> = emptyList(),
    val installedOnDevice: List<String> = emptyList(),
    val pendingOnDevice: List<String> = emptyList(),
    val onlineLanguages: List<String> = emptyList(),
    val error: String? = null,
) {
    /** True when the platform both supports and has already installed this tag. */
    val readyOffline: Boolean get() = installedOnDevice.any { it.equals(tag, ignoreCase = true) }

    /** True when supported but not yet installed — i.e. a download is required. */
    val needsDownload: Boolean
        get() = !readyOffline && supportedOnDevice.any { it.equals(tag, ignoreCase = true) }
}

/** Results for one recognizer (the default one, or the on-device one). */
data class RecognizerProbe(
    val label: String,
    val component: String?,
    val available: Boolean,
    val tamil: LanguageSupport,
    val english: LanguageSupport,
    val error: String? = null,
)

/** The complete Rung-0 result. Serialised to plain text for export. */
data class Rung0Report(
    val capturedAtIso: String,
    val deviceSummary: String,
    val androidSdk: Int,
    val recognitionAvailable: Boolean,
    val onDeviceRecognitionAvailable: Boolean,
    val defaultRecognizerRaw: String?,
    val installedRecognitionServices: List<String>,
    val probes: List<RecognizerProbe>,
    val notes: List<String>,
) {
    /**
     * Plain-text rendering, flat `key: value` lines on purpose: the human copies
     * this file back over Office Kit and pastes it into STATUS.md, so it has to
     * stay readable once detached from the app.
     */
    fun toText(): String = buildString {
        appendLine("VAAKKU Rung-0 probe — on-device speech recognition")
        appendLine("==================================================")
        appendLine("captured_at: $capturedAtIso")
        appendLine("device: $deviceSummary")
        appendLine("android_sdk: $androidSdk")
        appendLine()
        appendLine("-- platform --")
        appendLine("isRecognitionAvailable(): $recognitionAvailable")
        appendLine("isOnDeviceRecognitionAvailable(): $onDeviceRecognitionAvailable")
        appendLine("settings voice_recognition_service: ${defaultRecognizerRaw ?: "(null)"}")
        appendLine()
        appendLine("-- installed recognition services (${installedRecognitionServices.size}) --")
        if (installedRecognitionServices.isEmpty()) {
            appendLine("(none visible)")
        } else {
            installedRecognitionServices.forEach { appendLine("  $it") }
        }
        appendLine()
        probes.forEach { p ->
            appendLine("-- recognizer: ${p.label} --")
            appendLine("component: ${p.component ?: "(unresolved)"}")
            appendLine("available: ${p.available}")
            if (p.error != null) {
                appendLine("error: ${p.error}")
            } else {
                listOf(p.tamil, p.english).forEach { s ->
                    appendLine("  ${s.tag}:")
                    if (s.error != null) {
                        appendLine("      error: ${s.error}")
                    } else {
                        appendLine("      ready_offline: ${s.readyOffline}")
                        appendLine("      needs_download: ${s.needsDownload}")
                        appendLine("      supported_on_device: ${s.supportedOnDevice.render()}")
                        appendLine("      installed_on_device: ${s.installedOnDevice.render()}")
                        appendLine("      pending_on_device:   ${s.pendingOnDevice.render()}")
                        appendLine("      online_languages:    ${s.onlineLanguages.render()}")
                    }
                }
            }
            appendLine()
        }
        appendLine("-- notes --")
        if (notes.isEmpty()) appendLine("(none)") else notes.forEach { appendLine("- $it") }
    }

    private fun List<String>.render(): String = joinToString(", ").ifEmpty { "(none)" }
}

/**
 * The cheap subset of [Rung0Report] that the Setup checklist can afford to poll.
 * See [Rung0Probe.quickAvailability].
 */
data class QuickAvailability(
    val onDeviceAvailable: Boolean,
    val defaultRecognizerPackage: String?,
)

object Rung0Probe {

    /** The two languages the product actually needs (§6.3). */
    const val TAMIL = "ta-IN"
    const val ENGLISH = "en-IN"

    /**
     * `Settings.Secure.VOICE_RECOGNITION_SERVICE` is @hide, so the key is written
     * out literally. This is the documented way to identify the default recognizer
     * and it may legitimately be null on a vendor ROM (build plan §6.3 says so).
     */
    private const val KEY_VOICE_RECOGNITION_SERVICE = "voice_recognition_service"

    /**
     * The framework string resource the platform itself reads to find the
     * on-device recognizer: `isOnDeviceRecognitionAvailable()` is literally
     * "does this resource parse into a ComponentName". Read by name because the
     * resource is not part of the public API. Resource lookup by name is not
     * subject to the hidden-API restrictions that apply to methods and fields.
     */
    private const val RES_ON_DEVICE_RECOGNIZER = "config_defaultOnDeviceSpeechRecognitionService"

    private const val SUPPORT_TIMEOUT_MS = 5_000L
    private const val DOWNLOAD_TIMEOUT_MS = 20_000L

    /**
     * Runs the whole probe. Never throws: a failure at any step becomes text in
     * the report and an entry in [Rung0Report.notes].
     */
    suspend fun run(context: Context): Rung0Report {
        val notes = mutableListOf<String>()
        val sdk = Build.VERSION.SDK_INT

        if (sdk < Build.VERSION_CODES.TIRAMISU) {
            notes += "API $sdk < 33: checkRecognitionSupport()/triggerModelDownload() do not exist; " +
                "only the availability flags below are meaningful."
        }

        val recognitionAvailable = runCatching { SpeechRecognizer.isRecognitionAvailable(context) }
            .getOrElse {
                notes += "isRecognitionAvailable() threw ${it.describe()}"
                false
            }

        val onDeviceAvailable = runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }
            .getOrElse {
                notes += "isOnDeviceRecognitionAvailable() threw ${it.describe()}"
                false
            }

        val rawService = runCatching {
            Settings.Secure.getString(context.contentResolver, KEY_VOICE_RECOGNITION_SERVICE)
        }.getOrElse {
            notes += "Reading voice_recognition_service threw ${it.describe()}"
            null
        }
        if (rawService == null) {
            notes += "voice_recognition_service is null: the default recognizer is not identifiable " +
                "from settings on this ROM."
        }

        val services = runCatching { installedRecognitionServices(context) }.getOrElse {
            notes += "queryIntentServices threw ${it.describe()}"
            emptyList()
        }
        if (services.isEmpty()) {
            notes += "No recognition services are visible. If the phone clearly has one, check that " +
                "the <queries> block is still in AndroidManifest.xml."
        }

        val probes = mutableListOf<RecognizerProbe>()

        // --- Probe 1: the default recognizer. ---
        probes += probeRecognizer(
            context = context,
            label = "default (createSpeechRecognizer)",
            component = rawService?.let { formatRaw(it) },
            notes = notes,
        ) { SpeechRecognizer.createSpeechRecognizer(context) }

        // --- Probe 2: the on-device recognizer, which is what engine 5 uses. ---
        if (onDeviceAvailable) {
            probes += probeRecognizer(
                context = context,
                label = "on-device (createOnDeviceSpeechRecognizer)",
                component = onDeviceRecognizerComponent(),
                notes = notes,
            ) { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }
        } else {
            notes += "On-device recognition is unavailable, so engine 5 (AndroidOnDevice) is not a " +
                "candidate on this device; Rung 1 (sherpa-onnx) carries ASR."
            probes += RecognizerProbe(
                label = "on-device (createOnDeviceSpeechRecognizer)",
                component = onDeviceRecognizerComponent(),
                available = false,
                tamil = LanguageSupport(TAMIL, error = "on-device recognition unavailable"),
                english = LanguageSupport(ENGLISH, error = "on-device recognition unavailable"),
                error = "isOnDeviceRecognitionAvailable() == false",
            )
        }

        return Rung0Report(
            capturedAtIso = Iso8601.now(),
            deviceSummary = deviceSummary(),
            androidSdk = sdk,
            recognitionAvailable = recognitionAvailable,
            onDeviceRecognitionAvailable = onDeviceAvailable,
            defaultRecognizerRaw = rawService,
            installedRecognitionServices = services,
            probes = probes,
            notes = notes,
        )
    }

    /**
     * Creates one recognizer, asks it about both languages, and always destroys
     * it. A recognizer left alive holds the recognition service open and would
     * skew the next measurement.
     */
    private suspend fun probeRecognizer(
        context: Context,
        label: String,
        component: String?,
        notes: MutableList<String>,
        create: () -> SpeechRecognizer,
    ): RecognizerProbe {
        // createSpeechRecognizer and createOnDeviceSpeechRecognizer are @MainThread.
        val recognizer = withContext(Dispatchers.Main) {
            runCatching { create() }.getOrElse { t ->
                notes += "[$label] creation threw ${t.describe()}"
                null
            }
        } ?: return RecognizerProbe(
            label = label,
            component = component,
            available = false,
            tamil = LanguageSupport(TAMIL, error = "recognizer could not be created"),
            english = LanguageSupport(ENGLISH, error = "recognizer could not be created"),
            error = "creation failed",
        )

        return try {
            RecognizerProbe(
                label = label,
                component = component,
                available = true,
                tamil = querySupport(context, recognizer, TAMIL, label, notes),
                english = querySupport(context, recognizer, ENGLISH, label, notes),
            )
        } finally {
            withContext(Dispatchers.Main) {
                runCatching { recognizer.destroy() }
                    .onFailure { notes += "[$label] destroy() threw ${it.describe()}" }
            }
        }
    }

    /**
     * Asks one recognizer about one language tag.
     *
     * Wrapped in a timeout because a vendor recognizer that never calls back
     * would otherwise hang the probe — and "it never answered" is a result worth
     * recording, not a hang.
     */
    private suspend fun querySupport(
        context: Context,
        recognizer: SpeechRecognizer,
        tag: String,
        label: String,
        notes: MutableList<String>,
    ): LanguageSupport {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return LanguageSupport(tag, error = "requires API 33+ (device is API ${Build.VERSION.SDK_INT})")
        }

        val intent = recognizerIntent(tag)

        // The whole block runs on Main: checkRecognitionSupport is @MainThread,
        // and running the coroutine there lets the body call it directly.
        val result: Result<RecognitionSupport>? = withContext(Dispatchers.Main) {
            withTimeoutOrNull(SUPPORT_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    runCatching {
                        recognizer.checkRecognitionSupport(
                            intent,
                            ContextCompat.getMainExecutor(context),
                            object : RecognitionSupportCallback {
                                override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                                    if (cont.isActive) cont.resume(Result.success(recognitionSupport))
                                }

                                override fun onError(error: Int) {
                                    if (cont.isActive) {
                                        cont.resume(
                                            Result.failure(
                                                IllegalStateException("onError($error) — ${recognitionErrorName(error)}"),
                                            ),
                                        )
                                    }
                                }
                            },
                        )
                    }.onFailure { t ->
                        if (cont.isActive) cont.resume(Result.failure(t))
                    }
                }
            }
        }

        if (result == null) {
            notes += "[$label] checkRecognitionSupport($tag) produced no callback within ${SUPPORT_TIMEOUT_MS}ms."
            return LanguageSupport(tag, error = "timeout after ${SUPPORT_TIMEOUT_MS}ms (no callback)")
        }

        return result.fold(
            onSuccess = { s ->
                LanguageSupport(
                    tag = tag,
                    supportedOnDevice = s.supportedOnDeviceLanguages,
                    installedOnDevice = s.installedOnDeviceLanguages,
                    pendingOnDevice = s.pendingOnDeviceLanguages,
                    onlineLanguages = s.onlineLanguages,
                )
            },
            onFailure = { t -> LanguageSupport(tag, error = t.describe()) },
        )
    }

    /**
     * Asks the recognizer to fetch a language model.
     *
     * IMPORTANT for whoever runs this: a model download needs a network, so it
     * must happen **before** airplane mode goes on. That ordering is the most
     * likely way to lose the demo, which is why the button in the probe screen
     * says so directly.
     *
     * @return a human-readable line describing what happened.
     */
    suspend fun triggerTamilDownload(context: Context, tag: String = TAMIL): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return "triggerModelDownload needs API 33+ (device is API ${Build.VERSION.SDK_INT})."
        }

        val recognizer = withContext(Dispatchers.Main) {
            runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        } ?: return "Could not create a recognizer to request the download."

        return try {
            val events = mutableListOf<String>()
            val outcome = withContext(Dispatchers.Main) {
                withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) {
                    suspendCancellableCoroutine { cont ->
                        runCatching {
                            recognizer.triggerModelDownload(
                                recognizerIntent(tag),
                                ContextCompat.getMainExecutor(context),
                                object : android.speech.ModelDownloadListener {
                                    override fun onProgress(completedPercent: Int) {
                                        events += "progress $completedPercent%"
                                    }

                                    override fun onSuccess() {
                                        events += "onSuccess"
                                        if (cont.isActive) cont.resume("download completed")
                                    }

                                    override fun onScheduled() {
                                        events += "onScheduled"
                                        if (cont.isActive) cont.resume("scheduled for later (not completed now)")
                                    }

                                    override fun onError(error: Int) {
                                        events += "onError($error)"
                                        if (cont.isActive) {
                                            cont.resume("error $error — ${recognitionErrorName(error)}")
                                        }
                                    }
                                },
                            )
                        }.onFailure { t ->
                            if (cont.isActive) cont.resume("call threw ${t.describe()}")
                        }
                    }
                }
            }

            val summary = outcome
                ?: "no callback within ${DOWNLOAD_TIMEOUT_MS}ms (it may still be running in the background)"
            if (events.isEmpty()) "$tag: $summary" else "$tag: $summary [${events.joinToString("; ")}]"
        } finally {
            withContext(Dispatchers.Main) { runCatching { recognizer.destroy() } }
        }
    }

    /**
     * A cheap, synchronous "what speech engine does this phone have" check for the
     * Setup checklist (build plan §6.6 asks for an ASR engine row there).
     *
     * Deliberately *not* [run]: that creates recognizers and waits on callbacks,
     * which is far too heavy for a screen that re-reads its state every second.
     * This only asks the platform two metadata questions and touches neither the
     * microphone nor a recognizer instance.
     *
     * The name it returns is the recognizer **the ROM ships**, not an engine this
     * app has loaded — at P0 the app has loaded none. The Setup row that displays
     * it must say so; see `setup_asr_note`.
     */
    fun quickAvailability(context: Context): QuickAvailability {
        val onDevice = runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }
            .getOrDefault(false)
        val default = runCatching {
            Settings.Secure.getString(context.contentResolver, KEY_VOICE_RECOGNITION_SERVICE)
        }.getOrNull()
        return QuickAvailability(
            onDeviceAvailable = onDevice,
            defaultRecognizerPackage = default
                ?.let { ComponentName.unflattenFromString(it)?.packageName ?: it },
        )
    }

    /**
     * The intent every support query and download request is built from.
     */
    private fun recognizerIntent(tag: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            // The product is offline by definition, so always ask for the offline path.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

    /**
     * Every service that implements RecognitionService.
     *
     * This is the same query `isRecognitionAvailable()` runs internally, and it
     * needs the `<queries>` element in the manifest to see anything at all.
     */
    private fun installedRecognitionServices(context: Context): List<String> =
        context.packageManager
            .queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
            .mapNotNull { it.serviceInfo }
            .map { "${it.packageName}/${it.name}" }
            .distinct()
            .sorted()

    /**
     * The component the platform would use for on-device recognition, read from
     * the same framework resource `isOnDeviceRecognitionAvailable()` consults.
     * Null is a legitimate answer and means the ROM ships no on-device service.
     */
    private fun onDeviceRecognizerComponent(): String? = runCatching {
        val res = Resources.getSystem()
        val id = res.getIdentifier(RES_ON_DEVICE_RECOGNIZER, "string", "android")
        if (id == 0) return@runCatching null
        val raw = res.getString(id)
        if (raw.isNullOrBlank()) null else formatRaw(raw)
    }.getOrNull()

    /** Normalises a flattened component string; falls back to the raw text. */
    private fun formatRaw(raw: String): String {
        val cn: ComponentName? = ComponentName.unflattenFromString(raw)
        return if (cn == null) raw else "${cn.packageName}/${cn.className}"
    }

    private fun deviceSummary(): String {
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}"
        } else {
            "n/a"
        }
        return "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), SoC=$soc, rom=${Build.DISPLAY}"
    }

    /** Turns the platform's integer error code into something readable. */
    private fun recognitionErrorName(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
        SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
        SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
        SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
        SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "ERROR_TOO_MANY_REQUESTS"
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "ERROR_SERVER_DISCONNECTED"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "ERROR_LANGUAGE_NOT_SUPPORTED"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "ERROR_LANGUAGE_UNAVAILABLE"
        SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT -> "ERROR_CANNOT_CHECK_SUPPORT"
        SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS -> "ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS"
        else -> "error code $error"
    }

    private fun Throwable.describe(): String = "${javaClass.simpleName}: ${message ?: "(no message)"}"
}

/** Tiny date helpers, kept local so this file adds no dependency. */
internal object Iso8601 {
    fun now(): String = format("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

    /**
     * A filename-safe stamp: `2026-09-12_143005`.
     *
     * Separate from [now] because a colon is legal in a filename on Linux but is
     * the volume separator on Windows, and these files get copied to a Windows
     * laptop over Office Kit before they land in `evidence/`.
     */
    fun stamp(): String = format("yyyy-MM-dd_HHmmss")

    private fun format(pattern: String): String {
        val fmt = java.text.SimpleDateFormat(pattern, Locale.US)
        fmt.timeZone = java.util.TimeZone.getDefault()
        return fmt.format(java.util.Date())
    }
}
