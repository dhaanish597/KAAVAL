package app.vaakku.npu

/**
 * The arithmetic of the privacy masker, with no Android and no LiteRT in it.
 *
 * Build plan §6.5 runs a selfie-segmentation model over every page image before
 * it is written into a session's evidence folder, so the receipt holds the
 * document and never the person holding it. The model call itself needs a
 * phone; everything around it — deciding which of six per-pixel scores wins,
 * growing the mask outward, and mapping a 256x256 result back onto a
 * multi-megapixel photograph — is ordinary arithmetic that gets it right or
 * wrong identically on a laptop.
 *
 * That is the whole reason this file exists apart from [PersonMasker]. A
 * mistake in here does not crash: an off-by-one in the argmax stride or an
 * inverted row index produces a mask that is *plausible* — it covers something,
 * just not the person — and the failure mode is a face in a file that promised
 * not to contain one. That is not a bug a screenshot catches, so it is tested
 * instead.
 *
 * This is not `:domain`. CLAUDE.md #5 keeps `domain/` pure JVM for the claim
 * ledger, and a camera privacy mask is not claim logic; it lives in `:app`
 * beside the camera, in the same spirit as `asr/SegmentQuality`.
 */
object MaskMath {

    /** The model's square input and output edge, in pixels (verified from the .tflite). */
    const val EDGE = 256

    /**
     * Number of per-pixel scores the model emits.
     *
     * MediaPipe's selfie multiclass head: one background channel and five that
     * are all parts of a person (hair, body skin, face skin, clothes,
     * accessories). Read out of the file, not assumed — the output tensor is
     * `[1, 256, 256, 6]`.
     */
    const val CHANNELS = 6

    /**
     * The one channel that is not a person.
     *
     * Everything downstream asks only "is this pixel background?", never "which
     * body part is this?". That is deliberate: the exact order of channels 1..5
     * is a property of a model file we did not train, and if it were wrong, a
     * mask keyed to "channel 3 is the face" would quietly stop covering faces.
     * Keyed to "anything that is not background", the mask is wrong only if the
     * *background* channel moved, which is the one channel whose position is
     * visible in any test image: on a photograph of a document held at arm's
     * length, background must win nearly everywhere.
     *
     * [personCoverage] is what makes that assumption checkable on the device
     * rather than assumed here.
     */
    const val BACKGROUND_CHANNEL = 0

    /**
     * Per-pixel argmax over [CHANNELS], flattened row-major, as "is this a person".
     *
     * [scores] is the model's output tensor read as floats: `EDGE * EDGE *
     * CHANNELS` values, channel-interleaved, so pixel *i*'s six scores are at
     * `i * CHANNELS` through `i * CHANNELS + 5`. The result has one boolean per
     * pixel, `true` where the winning channel is anything but
     * [BACKGROUND_CHANNEL].
     *
     * Ties go to the lower channel index, which means an exact tie between
     * background and a person class resolves to *background*. Such a tie is
     * vanishingly unlikely in float32 output, and [dilate] exists to give the
     * mask its margin; encoding "ties are people" here would be a thumb on the
     * scale that never actually fires.
     */
    fun personMask(scores: FloatArray, edge: Int = EDGE, channels: Int = CHANNELS): BooleanArray {
        val pixels = edge * edge
        require(scores.size >= pixels * channels) {
            "expected at least ${pixels * channels} scores for ${edge}x$edge x$channels, got ${scores.size}"
        }
        val mask = BooleanArray(pixels)
        for (pixel in 0 until pixels) {
            val base = pixel * channels
            var bestChannel = 0
            var bestScore = scores[base]
            for (channel in 1 until channels) {
                val channelScore = scores[base + channel]
                if (channelScore > bestScore) {
                    bestScore = channelScore
                    bestChannel = channel
                }
            }
            mask[pixel] = bestChannel != BACKGROUND_CHANNEL
        }
        return mask
    }

    /** Fraction of [mask] that is a person, 0.0..1.0. */
    fun personCoverage(mask: BooleanArray): Double {
        if (mask.isEmpty()) return 0.0
        var covered = 0
        for (m in mask) if (m) covered++
        return covered.toDouble() / mask.size
    }

    /**
     * Grows [mask] outward by [radius] pixels (square structuring element).
     *
     * Two errors both point the same way and this is the correction for both.
     * The model sees a 256x256 stretch of a 4:3 photograph, so one mask pixel is
     * roughly 12x16 page pixels at a 3000 px capture — a boundary that is one
     * mask pixel too tight leaves a visible sliver of skin along an edge. And
     * segmentation boundaries are soft in general: the model is confident in the
     * middle of a face and much less so at the hairline.
     *
     * Erring outward costs a band of document pixels around a person who is
     * already occluding that part of the page. Erring inward costs the promise
     * in §6.5. §6.4 puts OCR on the *unmasked* capture, so nothing that grows
     * here can cost a clause — it cannot make the ledger read less.
     */
    fun dilate(mask: BooleanArray, edge: Int = EDGE, radius: Int = DILATE_RADIUS): BooleanArray {
        require(mask.size == edge * edge) { "mask of ${mask.size} is not ${edge}x$edge" }
        if (radius <= 0) return mask.copyOf()

        // Separable: a square dilation is a horizontal pass then a vertical one,
        // O(n * radius) instead of O(n * radius^2). At 256x256 either is fast,
        // but this runs per page and the simpler loop is also the clearer one.
        val horizontal = BooleanArray(mask.size)
        for (row in 0 until edge) {
            val rowStart = row * edge
            for (col in 0 until edge) {
                if (!mask[rowStart + col]) continue
                val from = (col - radius).coerceAtLeast(0)
                val to = (col + radius).coerceAtMost(edge - 1)
                for (c in from..to) horizontal[rowStart + c] = true
            }
        }

        val out = BooleanArray(mask.size)
        for (row in 0 until edge) {
            val rowStart = row * edge
            for (col in 0 until edge) {
                if (!horizontal[rowStart + col]) continue
                val from = (row - radius).coerceAtLeast(0)
                val to = (row + radius).coerceAtMost(edge - 1)
                for (r in from..to) out[r * edge + col] = true
            }
        }
        return out
    }

    /**
     * How far [dilate] grows the mask by default, in mask pixels.
     *
     * 3 of 256 is a little over 1% of the frame's width in each direction. Big
     * enough to swallow a soft hairline boundary at the resolutions §6.4
     * captures at; small enough that it cannot close the gap between a hand at
     * one edge of the page and text at the other.
     */
    const val DILATE_RADIUS = 3

    /**
     * Maps a pixel of a [width] x [height] image to its index in an
     * `edge * edge` mask, nearest-neighbour.
     *
     * The model is fed the page stretched to a square, not letterboxed, so the
     * inverse is a straight proportional map on each axis independently. The
     * stretch distorts a person's shape, which for a *mask* costs only accuracy
     * at the boundary — the thing [dilate] is already paying for. Letterboxing
     * would instead spend real model resolution on grey bars.
     *
     * Nearest-neighbour, not bilinear: the mask is boolean, so there is nothing
     * to interpolate between. Blending would only produce fractional values that
     * need a threshold, which is another number to get wrong.
     */
    fun maskIndexFor(x: Int, y: Int, width: Int, height: Int, edge: Int = EDGE): Int {
        require(width > 0 && height > 0) { "image is ${width}x$height" }
        val mx = (x.toLong() * edge / width).toInt().coerceIn(0, edge - 1)
        val my = (y.toLong() * edge / height).toInt().coerceIn(0, edge - 1)
        return my * edge + mx
    }
}
