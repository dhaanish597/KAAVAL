package app.vaakku.domain.copy

/** What one card needs to render — build plan §8.2. Only DIFFERS and NOT_IN_DOCUMENT ever produce a card. */
sealed interface CardCopy {
    val stateLabel: CopyRef
    val followUp: CopyRef

    /** "பேச்சில்: {spoken}" / "ஆவணத்தில்: {written}" / "சரிபார்க்கவும்" / "Said: {spokenEn} · Document: {writtenEn}". */
    data class Differs(
        override val stateLabel: CopyRef,
        val spokenLine: LineRef,
        val writtenLine: LineRef,
        val footerKey: String,
        val englishSmallLineKey: String,
        override val followUp: CopyRef,
    ) : CardCopy

    /** "அவர் சொன்னது: {spoken}" / "ஆவணத்தில் இது இல்லை — கேளுங்கள்". */
    data class NotInDocument(
        override val stateLabel: CopyRef,
        val spokenLine: LineRef,
        val hintKey: String,
        override val followUp: CopyRef,
    ) : CardCopy
}
