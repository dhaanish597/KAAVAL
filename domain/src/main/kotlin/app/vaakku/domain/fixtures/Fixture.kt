package app.vaakku.domain.fixtures

import kotlinx.serialization.Serializable

/** One scripted spoken segment in a fixture — mirrors [app.vaakku.domain.model.AsrSegment]. */
@Serializable
data class FixtureSpoken(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val segmentQuality: Double,
    val engine: String = "fixture",
)

/** One scripted OCR line in a fixture — mirrors [app.vaakku.domain.model.OcrLine]. `box` is [left, top, right, bottom]. */
@Serializable
data class FixtureWritten(
    val text: String,
    val box: List<Int>,
    val confidence: Double,
    val frameId: String = "fixture",
)

/**
 * One test scenario — build plan §5.10. `expect` maps a [app.vaakku.domain.model.ClaimType]
 * name to the USER-FACING state: `MATCHES`, `NOT_IN_DOCUMENT`, `DIFFERS`, or
 * `SILENT` (build plan §2.3: PENDING and UNCERTAIN both collapse to the
 * single silent state a user would actually see — nothing). A type absent
 * from `expect` is not asserted by that fixture.
 */
@Serializable
data class Fixture(
    val id: String,
    val note: String = "",
    val spoken: List<FixtureSpoken> = emptyList(),
    val written: List<FixtureWritten> = emptyList(),
    val documentScanCompleted: Boolean = false,
    val expect: Map<String, String> = emptyMap(),
)
