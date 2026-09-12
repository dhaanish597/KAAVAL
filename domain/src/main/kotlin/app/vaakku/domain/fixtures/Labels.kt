package app.vaakku.domain.fixtures

import kotlinx.serialization.Serializable

/**
 * One row of the build plan §11.2 table — `testdata/testaudio/labels.json`.
 * `expectedClaims` is a small string encoding [SlotChecker] understands, not
 * a raw [app.vaakku.domain.model.ClaimValue] — see labels.json's own
 * `_readme` for the exact format per claim type.
 */
@Serializable
data class LabelEntry(
    val file: String,
    val script: String,
    val note: String = "",
    val expectedClaims: Map<String, String> = emptyMap(),
)

@Serializable
data class Labels(val files: List<LabelEntry> = emptyList())
