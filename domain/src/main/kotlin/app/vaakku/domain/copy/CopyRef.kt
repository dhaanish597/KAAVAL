package app.vaakku.domain.copy

/**
 * A string-resource key plus the arguments to fill its placeholders — never
 * a rendered string. Build plan §5.8: "The domain returns template keys +
 * arguments; the app resolves them with strings.xml." Because the domain
 * only ever emits identifiers and numbers (never English or Tamil prose),
 * it is structurally impossible for a banned word (§2.4) to pass through
 * this module's copy layer — there is no prose here for one to hide in.
 */
data class CopyRef(val key: String, val args: List<String> = emptyList())

/** A line template ([templateKey], e.g. "card_differs_spoken_line") whose one placeholder is filled by [value]. */
data class LineRef(val templateKey: String, val value: CopyRef)
