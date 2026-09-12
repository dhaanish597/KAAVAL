package app.vaakku.domain.model

/**
 * One segment of ASR output — build plan §5.2. `segmentQuality` is 0..1 from
 * VAD speech probability + energy; it is not a transcript confidence.
 */
data class AsrSegment(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val engine: String,
    val segmentQuality: Double,
)

/**
 * One OCR line — build plan §5.2. `confidence` is 1.0 when the OCR engine
 * does not report one.
 */
data class OcrLine(
    val text: String,
    val box: Box,
    val confidence: Double,
    val frameId: String,
)
