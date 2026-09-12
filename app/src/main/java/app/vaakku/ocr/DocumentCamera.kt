package app.vaakku.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * The three CameraX use cases the scan surface needs — build plan §6.4.
 *
 * `Preview` so the human can aim at the page, `ImageCapture` at the highest
 * resolution the camera offers because OCR accuracy on 8 pt table text is
 * mostly a pixels-per-glyph question, and `ImageAnalysis` which today does
 * nothing at all.
 *
 * ### Why an analysis use case that discards every frame
 *
 * §6.5's privacy masker (P4) runs per analysis frame at ~256 px, and the number
 * of bound use cases changes which resolutions and stream configurations
 * CameraX can hand out. Binding all three now means P4 adds a masker to an
 * already-working camera configuration instead of re-binding the camera and
 * re-checking capture resolution at the same time as it debugs an NPU model.
 * The analyzer receives frames, records **nothing** — no file, no buffer copy,
 * no timing — and closes the proxy immediately, which is what keeps the stream
 * flowing rather than stalling on a full queue.
 *
 * Not a `Closeable`: [unbind] both releases the camera and shuts the executor
 * down, and a screen already has to call it from `onDispose`.
 */
class DocumentCamera {

    private var provider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null

    /**
     * One background thread for the capture callback and the analysis stub.
     * The callback decodes a full-resolution JPEG, which is far too much work
     * for the main thread.
     */
    private var executor: ExecutorService? = null

    /**
     * Binds preview, capture and the analysis stub to [lifecycleOwner].
     *
     * Must be called from the main thread — `bindToLifecycle` requires it, and
     * a composable's `rememberCoroutineScope` is already on the main
     * dispatcher.
     */
    suspend fun bind(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
    ) {
        val cameraProvider = awaitProvider(context)
        val threads = executor ?: Executors.newSingleThreadExecutor().also { executor = it }

        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(surfaceProvider)

        // No ResolutionSelector: §6.4 asks for full resolution and CameraX
        // already does exactly that by default. `ImageCapture.Defaults`
        // (camera-core 1.5.1 sources, read from the Gradle cache) builds its
        // DEFAULT_RESOLUTION_SELECTOR from RATIO_4_3_FALLBACK_AUTO_STRATEGY +
        // ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY, and
        // ResolutionSelector.Builder defaults to that same aspect-ratio
        // strategy — so spelling it out here would produce an identical
        // selector, with one more thing to get wrong.
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(threads) { frame: ImageProxy ->
            // P4's masker goes here. Until then: read nothing, keep nothing,
            // and release the frame at once so the stream never backs up.
            frame.close()
        }

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            capture,
            analysis,
        )

        provider = cameraProvider
        imageCapture = capture
    }

    /**
     * Takes one full-resolution photograph and returns it upright, in memory.
     *
     * In memory on purpose: §6.4 requires the page image on disk to have been
     * through [PrivacyMask] first, and `takePicture(OutputFileOptions, …)`
     * would write the unmasked frame to a file before any of our code could
     * touch it. There is no un-writing a photograph.
     *
     * The caller owns the returned bitmap.
     */
    suspend fun capturePage(): Bitmap {
        val capture = checkNotNull(imageCapture) { "capturePage() before bind()" }
        val threads = checkNotNull(executor) { "capturePage() before bind()" }

        val result = suspendCancellableCoroutine<Result<Bitmap>> { cont ->
            capture.takePicture(
                threads,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bitmap = runCatching {
                            // The decoded bitmap is in sensor orientation:
                            // BitmapFactory ignores the JPEG's EXIF tag, and
                            // rotationDegrees is exactly the correction that
                            // tag would have described. Rotating once here
                            // means the OCR input, the on-screen crop and the
                            // evidence file are all the same way up.
                            val raw = image.toBitmap()
                            rotate(raw, image.imageInfo.rotationDegrees)
                        }
                        image.close()
                        if (cont.isActive) cont.resume(bitmap)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        if (cont.isActive) cont.resume(Result.failure(exception))
                    }
                },
            )
        }
        return result.getOrThrow()
    }

    /** Releases the camera and the background thread. Safe to call more than once. */
    fun unbind() {
        provider?.unbindAll()
        provider = null
        imageCapture = null
        executor?.shutdown()
        executor = null
    }

    /**
     * `ProcessCameraProvider.getInstance` returns a `ListenableFuture`, and this
     * project declares neither `concurrent-futures-ktx` nor
     * `coroutines-play-services` (`gradle/libs.versions.toml`; CLAUDE.md
     * forbids adding libraries mid-event). So the future's listener is wrapped
     * the same way [MlKitTextRecognizer] wraps its `Task` — one idiom for every
     * callback API in this app.
     */
    private suspend fun awaitProvider(context: Context): ProcessCameraProvider {
        val future = ProcessCameraProvider.getInstance(context)
        return suspendCancellableCoroutine { cont ->
            future.addListener(
                {
                    val outcome = runCatching { future.get() }
                    if (cont.isActive) {
                        outcome
                            .onSuccess { cont.resume(it) }
                            .onFailure { cont.cancel(it) }
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
        }
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}
