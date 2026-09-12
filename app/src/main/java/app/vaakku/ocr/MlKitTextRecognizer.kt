package app.vaakku.ocr

import android.os.SystemClock
import app.vaakku.domain.model.OcrLine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable
import kotlin.coroutines.resume

/**
 * One OCR pass's result: the mapped [OcrLine]s and the elapsed recognition
 * time in ms — build plan §6.4 item 6, feeding the §11.5 "scan → clauses
 * ≤ 1.5 s" budget.
 *
 * [elapsedMs] times the recognition call only — [SystemClock.elapsedRealtime]
 * wrapped tightly around `recognizer.process(image)` in [MlKitTextRecognizer.recognize]
 * — not the [OcrLineMapper] pass after it, so it measures what ML Kit itself
 * cost rather than this module's own bookkeeping.
 */
data class OcrScan(
    val lines: List<OcrLine>,
    val elapsedMs: Long,
)

/**
 * Thin coroutine wrapper over ML Kit's bundled Latin recognizer — build plan
 * §6.4.
 *
 * `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)` is the
 * **bundled** recognizer declared as `libs.mlkit.text.recognition`
 * (`com.google.mlkit:text-recognition:16.0.1`, `app/build.gradle.kts`). Do
 * not switch this to the play-services-hosted client or change the version —
 * see [OcrLineMapper]'s KDoc for why the play-services `-common` artifact is
 * already on the classpath regardless (it supplies the `Text` API classes,
 * not a different recognizer implementation).
 *
 * `recognizer.process(image)` returns a Play Services `Task`, and this
 * project declares no `kotlinx-coroutines-play-services` dependency
 * (`gradle/libs.versions.toml` has no such entry, and CLAUDE.md forbids
 * adding libraries mid-event) — so this wraps the `Task`'s listeners in
 * [suspendCancellableCoroutine], the same pattern `Rung0Probe` already uses
 * for `SpeechRecognizer`'s callback APIs.
 */
class MlKitTextRecognizer : Closeable {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Runs one recognition pass over [image] and maps the result to
     * [OcrLine]s tagged with [frameId]. Lines with no bounding box are
     * dropped — see [OcrLineMapper.fromNullableBox].
     */
    suspend fun recognize(image: InputImage, frameId: String): OcrScan {
        val started = SystemClock.elapsedRealtime()
        val text = awaitText(image)
        val elapsedMs = SystemClock.elapsedRealtime() - started

        val lines = text.textBlocks
            .flatMap { it.lines }
            .mapNotNull { OcrLineMapper.fromLine(it, frameId) }
        return OcrScan(lines, elapsedMs)
    }

    private suspend fun awaitText(image: InputImage): Text {
        val result = suspendCancellableCoroutine<Result<Text>> { cont ->
            recognizer.process(image)
                .addOnSuccessListener { text ->
                    if (cont.isActive) cont.resume(Result.success(text))
                }
                .addOnFailureListener { error ->
                    if (cont.isActive) cont.resume(Result.failure(error))
                }
        }
        return result.getOrThrow()
    }

    /** Releases the native recognizer. Safe to call once the recognizer is no longer needed. */
    override fun close() {
        recognizer.close()
    }
}
