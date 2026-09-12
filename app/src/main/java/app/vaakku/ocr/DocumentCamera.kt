package app.vaakku.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * The three CameraX use cases the scan surface needs — build plan §6.4.
 *
 * `Preview` so the human can aim at the page, `ImageCapture` at a resolution
 * chosen for reading small print (see [CAPTURE_BOUND_SIZE]), and
 * `ImageAnalysis` which today does nothing at all.
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
 * Not a `Closeable`: [unbind] releases the camera and retires the executor, and
 * a screen already has to call it from `onDispose`.
 */
class DocumentCamera {

    private var provider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null

    /**
     * One background thread for the capture callback and the analysis stub.
     * The callback decodes a multi-megapixel JPEG, which is far too much work
     * for the main thread.
     *
     * Volatile, and retired through [releaseExecutorIfIdle] rather than in
     * [unbind] directly: see [capturesInFlight].
     */
    @Volatile
    private var executor: ExecutorService? = null

    /**
     * Captures whose callback CameraX has not delivered yet.
     *
     * Shutting the executor down while one is outstanding is a real crash, not
     * a theoretical one: unbinding an `ImageCapture` mid-capture makes CameraX
     * deliver an error callback, and it delivers it **on this executor**. A
     * `shutdown()` between the tap and that callback turns the demo's Close
     * button into a `RejectedExecutionException` on an internal camera thread.
     * So [unbind] only *asks* for shutdown, and whichever of the two finishes
     * last actually performs it.
     */
    private val capturesInFlight = AtomicInteger(0)

    @Volatile
    private var shutdownRequested = false

    /**
     * Binds preview, capture and the analysis stub to [lifecycleOwner].
     *
     * Must be called from the main thread — `bindToLifecycle` requires it, and
     * a composable's coroutine scope is already on the main dispatcher.
     */
    suspend fun bind(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
    ) {
        val cameraProvider = awaitProvider(context)
        shutdownRequested = false
        val threads = executor ?: Executors.newSingleThreadExecutor().also { executor = it }

        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(surfaceProvider)

        // Capture resolution is CAPPED, deliberately, against CameraX's own
        // default of ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY.
        //
        // §6.4's "full resolution for OCR" contrasts this stream with the
        // ~256 px analysis stream: it means "enough pixels to read small
        // print", not "whatever the sensor can emit". On a 50 MP phone
        // camera the maximum is ~8192x6144, which decodes to
        // 8192*6144*4 B = 201 MB as ARGB_8888, and [rotate] holds a second
        // full-size bitmap before releasing the first — ~400 MB per Scan tap,
        // before ML Kit's own working copy. §11.5 budgets app memory under
        // 1.5 GB and scan-to-clauses at 1.5 s; that allocation breaks both,
        // and the likely first symptom is OutOfMemoryError on the first tap.
        //
        // At the bound size below: 4000*3000*4 B = 48 MB, ~96 MB across the
        // rotate. A portrait A4 page filling the 3000 px short edge is about
        // 360 px per inch, so 8 pt table type is roughly 40 px tall — far more
        // than any OCR engine needs, and the G3 run on the real prop is what
        // confirms or refutes that.
        //
        // Do not "restore" the maximum, and do not bump the CameraX version to
        // get a different knob.
        //
        // CLOSEST_LOWER_THEN_HIGHER is one of the two fallback rules CameraX's
        // own ResolutionStrategy documentation recommends: FALLBACK_RULE_NONE
        // can make bindToLifecycle throw IllegalArgumentException when the
        // bound size is unavailable in a three-use-case combination.
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            CAPTURE_BOUND_SIZE,
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                        ),
                    )
                    .build(),
            )
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
     * Takes one photograph and returns it upright, in memory, with its long
     * edge no greater than [MAX_CAPTURE_LONG_EDGE_PX].
     *
     * In memory on purpose: §6.4 requires the page image on disk to have been
     * through [PrivacyMask] first, and `takePicture(OutputFileOptions, …)`
     * would write the unmasked frame to a file before any of our code could
     * touch it. There is no un-writing a photograph.
     *
     * The caller owns the returned bitmap. If this coroutine is cancelled
     * while the capture is outstanding, the bitmap that arrives afterwards is
     * recycled here rather than stranded.
     */
    suspend fun capturePage(): Bitmap {
        val capture = checkNotNull(imageCapture) { "capturePage() before bind()" }
        val threads = checkNotNull(executor) { "capturePage() before bind()" }

        capturesInFlight.incrementAndGet()
        val result = suspendCancellableCoroutine<Result<Bitmap>> { cont ->
            val callback = object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = runCatching {
                        // The decoded bitmap is in sensor orientation:
                        // BitmapFactory ignores the JPEG's EXIF tag, and
                        // rotationDegrees is exactly the correction that tag
                        // would have described. Rotating once here means the
                        // OCR input, the on-screen crop and the evidence file
                        // are all the same way up.
                        rotate(decodeCapped(image), image.imageInfo.rotationDegrees)
                    }
                    image.close()
                    finishCapture()
                    if (cont.isActive) {
                        cont.resume(bitmap)
                    } else {
                        // Nobody is waiting any more (the screen was closed
                        // mid-capture). Tens of MB, so release it here.
                        bitmap.getOrNull()?.recycle()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    finishCapture()
                    if (cont.isActive) cont.resume(Result.failure(exception))
                }
            }
            try {
                capture.takePicture(threads, callback)
            } catch (t: Throwable) {
                // takePicture threw before accepting the request, so neither
                // callback will ever run and neither will decrement.
                finishCapture()
                if (cont.isActive) cont.resume(Result.failure(t))
            }
        }
        return result.getOrThrow()
    }

    /**
     * Releases the camera and asks for the executor to be retired. Safe to
     * call more than once, and safe to call with a capture still outstanding —
     * the executor survives until that capture's callback has been delivered.
     */
    fun unbind() {
        provider?.unbindAll()
        provider = null
        imageCapture = null
        shutdownRequested = true
        releaseExecutorIfIdle()
    }

    private fun finishCapture() {
        capturesInFlight.decrementAndGet()
        releaseExecutorIfIdle()
    }

    @Synchronized
    private fun releaseExecutorIfIdle() {
        if (!shutdownRequested || capturesInFlight.get() > 0) return
        // Called either from the main thread (unbind) or from the executor's
        // own thread (a capture callback). shutdown() does not block and is
        // legal from a task running on the pool it retires.
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

    /**
     * Decodes the captured frame with the long edge capped at
     * [MAX_CAPTURE_LONG_EDGE_PX].
     *
     * The cap is applied *during* the decode, through `inSampleSize`, not
     * after it: a post-decode downscale still has to allocate the full-size
     * bitmap first, which is the allocation the cap exists to avoid. The
     * `ResolutionSelector` in [bind] should already keep the frame within the
     * bound, but its fallback rule may legally hand back a higher resolution
     * when nothing lower is available, and this is what makes the ceiling hold
     * anyway.
     */
    private fun decodeCapped(image: ImageProxy): Bitmap {
        if (image.format != ImageFormat.JPEG) {
            // Not the configured output format (ImageCapture defaults to
            // JPEG). Fall back to CameraX's own conversion and cap after the
            // fact — correct, just not as cheap.
            return capLongEdge(image.toBitmap())
        }

        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_CAPTURE_LONG_EDGE_PX)
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: error("The captured frame could not be decoded.")
        // inSampleSize only halves, so the result can still be up to twice the
        // cap when the source is not a power-of-two multiple of it.
        return capLongEdge(decoded)
    }

    private fun capLongEdge(bitmap: Bitmap): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= MAX_CAPTURE_LONG_EDGE_PX) return bitmap
        val scale = MAX_CAPTURE_LONG_EDGE_PX.toDouble() / longEdge
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    companion object {
        /**
         * The long edge a captured page may not exceed, in pixels. See the
         * §11.5 arithmetic at the `ResolutionSelector` in [bind].
         */
        const val MAX_CAPTURE_LONG_EDGE_PX = 4000

        /** 4:3, matching [AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY]. */
        val CAPTURE_BOUND_SIZE: Size = Size(MAX_CAPTURE_LONG_EDGE_PX, 3000)

        /**
         * The smallest power-of-two `inSampleSize` that brings the longer of
         * [width]/[height] to [maxLongEdge] or below. 1 when the source is
         * already within the cap or its bounds are unknown (`decodeBounds`
         * reports 0 for an undecodable buffer).
         */
        internal fun sampleSizeFor(width: Int, height: Int, maxLongEdge: Int): Int {
            val longEdge = maxOf(width, height)
            if (longEdge <= 0 || maxLongEdge <= 0) return 1
            var sample = 1
            while (longEdge / sample > maxLongEdge) sample *= 2
            return sample
        }
    }
}
