package app.vaakku.ocr

import app.vaakku.domain.model.Box
import app.vaakku.domain.model.OcrLine
import com.google.mlkit.vision.text.Text

/**
 * Maps ML Kit's `Text.Line` to the pure-JVM [OcrLine] the domain module
 * consumes — build plan §6.4. This is the one seam between the bundled Latin
 * recognizer and `RowAssembler`/`WrittenExtractor`; everything on the domain
 * side of it stays free of `android.*` and ML Kit imports (CLAUDE.md #5).
 *
 * ### Does `Text.Line`/`Text.Element` expose a confidence, in the pinned 16.0.1?
 *
 * **Yes — established from the actual resolved classpath on this machine, not
 * from memory.** `com.google.mlkit:text-recognition:16.0.1`
 * (`gradle/libs.versions.toml`, `libs.mlkit.text.recognition`) does not itself
 * contain `Text`/`Text.Line`/`Text.Element` — `unzip -l` on its `classes.jar`
 * has no `vision/text/Text*.class` at all. `./gradlew :app:dependencies
 * --configuration debugRuntimeClasspath` shows it transitively pulls in
 * `com.google.android.gms:play-services-mlkit-text-recognition-common:19.1.0`
 * (via `play-services-mlkit-text-recognition:19.0.1`), and it is that
 * `-common` artifact's `classes.jar` — confirmed present on `:app`'s actual
 * runtime classpath, not a guess — that defines those classes. `javap -p` on
 * it shows both `Text.Line.getConfidence()` and `Text.Element.getConfidence()`
 * (`public float getConfidence()`), documented in the artifact's javadoc jar
 * as "in range [0.0f, 1.0f]". `javap -c` on the getter shows a plain field
 * read (`getfield zzb; freturn`), not a hardcoded stub returning a constant.
 * Tracing the package-private constructor's one caller
 * (`com.google.mlkit.vision.text.zzd`, the internal per-line result
 * converter) shows that float is read straight off the recognition-result
 * proto for that line (`zzvd.zzb()`, a real per-line field, distinct from
 * `zzvd.zza()` which becomes `getAngle()`) and threaded through to the
 * constructor unmodified — i.e. wired to genuine per-line pipeline output,
 * not a constant baked into this Java-visible layer. Full transcript of the
 * commands and their output is in `.superpowers/sdd/p3-ocr-plan/task-2-report.md`.
 * Per §6.4's own rule ("use line/element confidence if the API exposes it,
 * otherwise 1.0"), [fromLine] below maps `line.confidence` straight through
 * rather than defaulting to 1.0.
 */
object OcrLineMapper {

    /**
     * Pure `Rect`-corner → [Box] mapping, taking four Ints rather than an
     * `android.graphics.Rect` so it is directly unit-testable on the plain
     * JVM without leaning on `unitTests.isReturnDefaultValues` doing the
     * right thing for a framework type (`app/build.gradle.kts`).
     */
    fun boxOf(left: Int, top: Int, right: Int, bottom: Int): Box = Box(left, top, right, bottom)

    /**
     * The §6.4 confidence policy for one raw ML Kit value: pass it through,
     * clamped to the documented `[0.0, 1.0]` range as a defensive measure —
     * matching how `SegmentQuality` clamps its own inputs elsewhere in this
     * app — in case a future build of the recognizer ever returns something
     * outside it.
     */
    fun confidenceOf(raw: Float): Double = raw.toDouble().coerceIn(0.0, 1.0)

    /**
     * The null-box drop policy, kept pure and independent of any ML Kit or
     * `android.*` type: given an already-mapped nullable [box], returns an
     * [OcrLine] built from [text]/[confidence]/[frameId], or `null` when
     * there was no box to map.
     *
     * A line's `boundingBox` is nullable in the ML Kit API and **must be
     * dropped here, not defaulted to a zero-size box**: `RowAssembler`
     * computes one median line height over the *whole page* and clusters
     * every other line against it (build plan §5.6). A synthetic
     * `(0,0,0,0)` box would pull that median down — silently corrupting the
     * row grouping for the entire page, not just the one line that had no
     * box.
     */
    fun fromNullableBox(box: Box?, text: String, confidence: Double, frameId: String): OcrLine? {
        if (box == null) return null
        return OcrLine(text = text, box = box, confidence = confidence, frameId = frameId)
    }

    /**
     * Maps one [Text.Line] to [OcrLine], or `null` if it has no bounding box
     * — see [fromNullableBox]. Thin adapter over the two pure functions
     * above; this is the only function here that touches an ML Kit type.
     */
    fun fromLine(line: Text.Line, frameId: String): OcrLine? {
        val rect = line.boundingBox
        val box = rect?.let { boxOf(it.left, it.top, it.right, it.bottom) }
        return fromNullableBox(box, line.text, confidenceOf(line.confidence), frameId)
    }
}
