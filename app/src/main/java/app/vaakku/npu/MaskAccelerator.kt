package app.vaakku.npu

/**
 * Which processor actually ran the mask.
 *
 * CLAUDE.md #8: never display "NPU" unless it is true. The trap this type
 * exists to close is that LiteRT's [com.google.ai.edge.litert.CompiledModel.Options]
 * takes a *set* of accelerators and picks one internally — ask for
 * `Options(NPU, GPU)` and you get a working model with no honest way to say
 * which one is under it. So [PersonMasker.createOn] asks for exactly one at a
 * time and lets creation fail, and this enum records which request was the one
 * that succeeded.
 *
 * That still only proves which accelerator LiteRT *accepted*. The gate on
 * printing "NPU" to the human is a logcat line showing Hexagon dispatch (§6.5
 * honesty rule, G4 evidence) — see [AcceleratorReport.proven].
 */
enum class MaskAccelerator {
    /** Hexagon NPU via the Qualcomm QNN runtime, compiled on device (JIT). */
    NPU,

    /** GPU via LiteRT's `libLiteRtClGlAccelerator.so`, which ships inside the AAR. */
    GPU,

    /** Plain CPU via `libLiteRt.so`. Always available; the floor of the ladder. */
    CPU,
    ;

    /**
     * What the scan screen prints beside the latency (§6.6 screen 3: "mask
     * 7.9 ms · NPU").
     *
     * The enum name is already the honest label, and deliberately so: there is
     * no display string here that could drift away from the value it describes.
     */
    val label: String get() = name
}

/**
 * The result of building the masker: what was asked for, what was obtained, and
 * what each rung refused.
 *
 * The refusals are kept rather than logged and dropped. If the phone ends up on
 * GPU on demo day, "why" is the first question, and the answer — the exact
 * LiteRT exception from the NPU attempt — is worth more than a screenshot of
 * the fallback working.
 */
data class AcceleratorReport(
    /** The rung that produced a working model. */
    val accelerator: MaskAccelerator,

    /**
     * Rungs that were tried and failed, in order, with the reason each gave.
     *
     * Empty when the first rung worked.
     */
    val refusals: List<Refusal> = emptyList(),

    /**
     * Whether the device claims NPU support at all
     * ([com.google.ai.edge.litert.BuiltinNpuAcceleratorProvider.isDeviceSupported]),
     * and whether its runtime libraries are present
     * ([com.google.ai.edge.litert.BuiltinNpuAcceleratorProvider.isLibraryReady]).
     *
     * These separate "this chip has no NPU" from "this chip has one and we did
     * not ship the libraries for it" — two failures that look identical from a
     * fallback to GPU but have completely different fixes.
     */
    val deviceSupportsNpu: Boolean = false,
    val npuLibraryReady: Boolean = false,

    /**
     * Accelerators LiteRT reported as available in the environment, by name.
     *
     * Recorded as strings rather than as [MaskAccelerator] because this is
     * LiteRT's own vocabulary (it includes `NONE`) and the point of the field is
     * to capture what LiteRT said, not to translate it.
     */
    val environmentAccelerators: List<String> = emptyList(),

    /**
     * Set only once a logcat line has proven Hexagon dispatch for this session.
     *
     * Until then the app may show the accelerator name — that part is honest,
     * it is which rung LiteRT accepted — but no claim of *proof* is made
     * anywhere. G4's evidence is the logcat excerpt, not this flag; the flag
     * exists so a future screen cannot accidentally present the two as the same
     * thing.
     */
    val proven: Boolean = false,
) {
    /** One failed rung. */
    data class Refusal(val accelerator: MaskAccelerator, val reason: String)

    /**
     * A one-line summary for the debug overlay and the STATUS.md evidence line.
     *
     * Deliberately includes the refusals: a line that reads `GPU` alone invites
     * the reader to supply their own reason for why it is not NPU.
     */
    fun describe(): String = buildString {
        append(accelerator.label)
        if (refusals.isNotEmpty()) {
            append(" (")
            append(refusals.joinToString("; ") { "${it.accelerator.label} refused: ${it.reason}" })
            append(")")
        }
    }
}
