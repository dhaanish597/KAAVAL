package app.vaakku.npu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The masker's arithmetic, tested because its failures are quiet.
 *
 * None of the mistakes these tests look for throw anything. Read the output
 * tensor with the wrong stride, or index rows where columns belong, and what
 * comes back is still a mask — it still covers roughly a third of the frame, it
 * still looks like a person-shaped region in a debug overlay. It just covers the
 * wrong pixels, and the file that promised it holds no faces holds one.
 *
 * So every test here asserts an *exact* set of masked pixels against a tensor
 * built by hand, rather than a count or a coverage fraction. A count is the one
 * thing a transposed mask gets right.
 */
class MaskMathTest {

    /**
     * Builds an output tensor the way the model emits one: `edge * edge`
     * pixels, each with [MaskMath.CHANNELS] consecutive values.
     *
     * [personChannelAt] returns the channel that should win at (x, y), or `null`
     * for background. The winner gets 1.0 and everything else 0.0 — the margin
     * is deliberately unambiguous, because these tests are about indexing, not
     * about how the model resolves a close call.
     */
    private fun tensorOf(
        edge: Int,
        channels: Int = MaskMath.CHANNELS,
        personChannelAt: (x: Int, y: Int) -> Int?,
    ): FloatArray {
        val tensor = FloatArray(edge * edge * channels)
        for (y in 0 until edge) {
            for (x in 0 until edge) {
                val winner = personChannelAt(x, y) ?: MaskMath.BACKGROUND_CHANNEL
                tensor[(y * edge + x) * channels + winner] = 1.0f
            }
        }
        return tensor
    }

    /** The indices of every `true` in [mask], for exact set comparisons. */
    private fun maskedIndices(mask: BooleanArray): Set<Int> =
        mask.indices.filter { mask[it] }.toSet()

    // ---- personMask: reading the tensor ------------------------------------

    @Test
    fun `a tensor that is all background masks nothing`() {
        val mask = MaskMath.personMask(tensorOf(edge = 8) { _, _ -> null }, edge = 8)
        assertEquals(64, mask.size)
        assertTrue(mask.none { it }, "background-only tensor produced a non-empty mask")
    }

    @Test
    fun `every non-background channel counts as a person`() {
        // The design leans on this: the mask asks "is this background?", never
        // "which body part is this?", precisely so that the order of channels
        // 1..5 in a model we did not train cannot quietly stop covering faces.
        for (channel in 1 until MaskMath.CHANNELS) {
            val mask = MaskMath.personMask(tensorOf(edge = 4) { _, _ -> channel }, edge = 4)
            assertTrue(mask.all { it }, "channel $channel was not treated as a person")
        }
    }

    @Test
    fun `the mask is read pixel-interleaved, not channel-planar`() {
        // The failure this catches: reading the tensor as six full-frame planes
        // laid end to end instead of six values per pixel. Both readings consume
        // the same buffer without complaint. Only one is the model's layout.
        //
        // One person pixel, at (3, 1) of a 4x4 frame. Planar reading would find
        // its person value somewhere in the first plane and mask a different
        // pixel entirely.
        val mask = MaskMath.personMask(
            tensorOf(edge = 4) { x, y -> if (x == 3 && y == 1) 2 else null },
            edge = 4,
        )
        assertEquals(setOf(1 * 4 + 3), maskedIndices(mask))
    }

    @Test
    fun `rows and columns are not transposed`() {
        // The top row is the one shape that is obviously wrong when flipped: a
        // transposed read turns it into the left column, which is still a
        // straight edge of the frame and still looks deliberate.
        val mask = MaskMath.personMask(
            tensorOf(edge = 4) { _, y -> if (y == 0) 1 else null },
            edge = 4,
        )
        assertEquals(setOf(0, 1, 2, 3), maskedIndices(mask))
    }

    @Test
    fun `an exact tie resolves to background`() {
        // Documented behaviour: ties go to the lower channel index, so a tie
        // between background and a person class is background. dilate() is what
        // supplies the margin, not a thumb on this comparison.
        val edge = 4
        val tensor = FloatArray(edge * edge * MaskMath.CHANNELS)
        for (pixel in 0 until edge * edge) {
            val base = pixel * MaskMath.CHANNELS
            tensor[base + MaskMath.BACKGROUND_CHANNEL] = 0.5f
            tensor[base + 3] = 0.5f
        }
        assertTrue(MaskMath.personMask(tensor, edge = edge).none { it })
    }

    @Test
    fun `the winner is the largest channel, not the first one over a threshold`() {
        // Background at 0.4, person class at 0.6: person wins. Reversed: it does
        // not. An implementation that masked whenever any person channel cleared
        // some fixed value would pass the first and fail the second.
        val edge = 2
        fun tensorWith(background: Float, person: Float): FloatArray {
            val tensor = FloatArray(edge * edge * MaskMath.CHANNELS)
            for (pixel in 0 until edge * edge) {
                val base = pixel * MaskMath.CHANNELS
                tensor[base + MaskMath.BACKGROUND_CHANNEL] = background
                tensor[base + 4] = person
            }
            return tensor
        }
        assertTrue(MaskMath.personMask(tensorWith(0.4f, 0.6f), edge = edge).all { it })
        assertTrue(MaskMath.personMask(tensorWith(0.6f, 0.4f), edge = edge).none { it })
    }

    @Test
    fun `negative values are compared correctly`() {
        // Raw logits are routinely negative. An implementation that seeded its
        // running best at 0.0 instead of at channel 0's own value would call
        // every all-negative pixel background.
        val edge = 2
        val tensor = FloatArray(edge * edge * MaskMath.CHANNELS) { -9f }
        for (pixel in 0 until edge * edge) {
            tensor[pixel * MaskMath.CHANNELS + 5] = -1f
        }
        assertTrue(MaskMath.personMask(tensor, edge = edge).all { it })
    }

    @Test
    fun `a tensor too short to be this frame is refused`() {
        // Better to fail loudly here than to mask whatever the first N floats of
        // a mismatched buffer happen to describe.
        assertThrows<IllegalArgumentException> {
            MaskMath.personMask(FloatArray(8 * 8 * MaskMath.CHANNELS - 1), edge = 8)
        }
    }

    // ---- dilate: growing the mask -----------------------------------------

    @Test
    fun `dilating one pixel gives a square of the right size`() {
        val edge = 16
        val mask = BooleanArray(edge * edge)
        mask[8 * edge + 8] = true

        val grown = MaskMath.dilate(mask, edge = edge, radius = 2)

        val expected = buildSet {
            for (y in 6..10) for (x in 6..10) add(y * edge + x)
        }
        assertEquals(expected, maskedIndices(grown))
        assertEquals(25, maskedIndices(grown).size, "a radius-2 square is 5x5")
    }

    @Test
    fun `dilation stops at the frame edge instead of wrapping`() {
        // Wrapping is the interesting failure: a mask that runs off the left
        // edge and reappears on the right paints a band across the far side of
        // the page, over text, for no visible reason.
        val edge = 8
        val mask = BooleanArray(edge * edge)
        mask[0] = true

        val grown = MaskMath.dilate(mask, edge = edge, radius = 2)

        val expected = buildSet {
            for (y in 0..2) for (x in 0..2) add(y * edge + x)
        }
        assertEquals(expected, maskedIndices(grown))
        for (y in 0 until edge) {
            assertFalse(grown[y * edge + (edge - 1)], "row $y wrapped onto the right edge")
        }
    }

    @Test
    fun `dilation only ever adds`() {
        val edge = 16
        val mask = BooleanArray(edge * edge) { it % 7 == 0 }

        val grown = MaskMath.dilate(mask, edge = edge, radius = 1)

        for (i in mask.indices) {
            if (mask[i]) assertTrue(grown[i], "pixel $i was masked and then uncovered")
        }
    }

    @Test
    fun `dilating by zero copies rather than aliases`() {
        // If this returned the same array, a later write through the returned
        // mask would reach back into the caller's.
        val edge = 4
        val mask = BooleanArray(edge * edge) { it == 5 }

        val grown = MaskMath.dilate(mask, edge = edge, radius = 0)

        assertNotSame(mask, grown)
        assertEquals(maskedIndices(mask), maskedIndices(grown))
        grown[0] = true
        assertFalse(mask[0], "writing to the result changed the input")
    }

    @Test
    fun `an empty mask stays empty`() {
        val edge = 8
        val grown = MaskMath.dilate(BooleanArray(edge * edge), edge = edge, radius = 3)
        assertTrue(grown.none { it })
    }

    @Test
    fun `a mask of the wrong size for this frame is refused`() {
        assertThrows<IllegalArgumentException> {
            MaskMath.dilate(BooleanArray(63), edge = 8)
        }
    }

    @Test
    fun `the default radius cannot bridge one side of the page to the other`() {
        // The KDoc's claim, pinned: dilation is big enough to swallow a soft
        // hairline but small enough that a hand at one edge cannot reach text at
        // the other. A person across the left 30% of a 256-wide frame must leave
        // the right edge clear.
        val edge = MaskMath.EDGE
        val personColumns = 0..76
        val mask = BooleanArray(edge * edge) { it % edge in personColumns }

        val grown = MaskMath.dilate(mask, edge = edge)

        for (y in 0 until edge) {
            assertTrue(grown[y * edge + 79], "the boundary did not grow by the default radius")
            assertFalse(grown[y * edge + 80], "the boundary grew further than the default radius")
            assertFalse(grown[y * edge + (edge - 1)], "dilation reached the far edge of the page")
        }
    }

    // ---- personCoverage ----------------------------------------------------

    @Test
    fun `coverage is the masked fraction of the frame`() {
        assertEquals(0.0, MaskMath.personCoverage(BooleanArray(100)), 1e-9)
        assertEquals(1.0, MaskMath.personCoverage(BooleanArray(100) { true }), 1e-9)
        assertEquals(0.25, MaskMath.personCoverage(BooleanArray(100) { it < 25 }), 1e-9)
    }

    @Test
    fun `coverage of an empty mask is zero, not a division by zero`() {
        assertEquals(0.0, MaskMath.personCoverage(BooleanArray(0)), 1e-9)
    }

    @Test
    fun `coverage is what makes the background channel checkable on the phone`() {
        // If channel 0 were not background, a photo of a document at arm's
        // length would come back almost fully masked instead of almost empty.
        // This is the arithmetic behind that check; the observation itself
        // happens on the device.
        val edge = MaskMath.EDGE
        val mostlyBackground = MaskMath.personMask(
            tensorOf(edge) { x, _ -> if (x < 8) 1 else null },
            edge = edge,
        )
        assertTrue(MaskMath.personCoverage(mostlyBackground) < 0.05)
    }

    // ---- maskIndexFor: back onto a real photograph -------------------------

    @Test
    fun `the corners of the page map to the corners of the mask`() {
        val width = 4000
        val height = 3000
        val edge = MaskMath.EDGE

        assertEquals(0, MaskMath.maskIndexFor(0, 0, width, height))
        assertEquals(edge - 1, MaskMath.maskIndexFor(width - 1, 0, width, height))
        assertEquals((edge - 1) * edge, MaskMath.maskIndexFor(0, height - 1, width, height))
        assertEquals(
            edge * edge - 1,
            MaskMath.maskIndexFor(width - 1, height - 1, width, height),
        )
    }

    @Test
    fun `every pixel of a full-resolution capture lands inside the mask`() {
        // The off-by-one here is not quiet — it throws, on the last row of a real
        // photograph, after the shutter, in front of the buyer.
        val edge = MaskMath.EDGE
        for ((width, height) in listOf(4000 to 3000, 3000 to 4000, 1 to 1, 257 to 255)) {
            for (y in listOf(0, height / 2, height - 1)) {
                for (x in listOf(0, width / 2, width - 1)) {
                    val index = MaskMath.maskIndexFor(x, y, width, height)
                    assertTrue(
                        index in 0 until edge * edge,
                        "($x, $y) of ${width}x$height mapped to $index",
                    )
                }
            }
        }
    }

    @Test
    fun `the map is proportional on each axis independently`() {
        // A 4:3 page is stretched to a square, so halfway across the page is
        // halfway across the mask on both axes regardless of the aspect ratio.
        val edge = MaskMath.EDGE
        val index = MaskMath.maskIndexFor(2000, 1500, 4000, 3000)
        assertEquals((edge / 2) * edge + (edge / 2), index)
    }

    @Test
    fun `the map never goes backwards`() {
        // Monotonicity is what makes the mask a resampling of the page rather
        // than a scramble of it.
        val width = 3000
        var previous = -1
        for (x in 0 until width) {
            val index = MaskMath.maskIndexFor(x, 0, width, 2000)
            assertTrue(index >= previous, "x=$x mapped backwards")
            previous = index
        }
    }

    @Test
    fun `a degenerate image size is refused`() {
        assertThrows<IllegalArgumentException> {
            MaskMath.maskIndexFor(0, 0, 0, 100)
        }
        assertThrows<IllegalArgumentException> {
            MaskMath.maskIndexFor(0, 0, 100, 0)
        }
    }

    @Test
    fun `the constants are the ones the model file actually declares`() {
        // Read out of handoff/npu/selfie_multiclass.tflite rather than taken
        // from the sample: input [1, 256, 256, 3], output [1, 256, 256, 6].
        // If a future model swap changes these, the tests above stop describing
        // the model in the APK.
        assertEquals(256, MaskMath.EDGE)
        assertEquals(6, MaskMath.CHANNELS)
        assertEquals(0, MaskMath.BACKGROUND_CHANNEL)
    }
}
