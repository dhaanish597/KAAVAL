package app.vaakku.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vaakku.domain.copy.CardCopy
import app.vaakku.ui.AppLanguage
import app.vaakku.ui.LocalAppLanguage
import app.vaakku.ui.copyText
import app.vaakku.ui.englishCopyText
import app.vaakku.ui.theme.VaakkuTheme
import app.vaakku.ui.theme.VoiceDocument

/**
 * The one card — build plan §6.6 screen 2, §8.2.
 *
 * Two stacked statements: what was **said**, and what is **written**. The whole
 * product is the gap between them, so the card is drawn as a carbon-copy form —
 * the spoken half on canary stock, the written half on the white original, and a
 * perforation between them. You can tell which half is which without reading a
 * word: the spoken half is set in sans, the written half in serif (see the
 * "three voices" note in `Theme.kt`).
 *
 * ### What decides what appears here
 *
 * Nothing in this file. [CardCopy] comes from `CopyBuilder`, which returns null
 * for MATCHES, PENDING and UNCERTAIN — the three silent states (CLAUDE.md #2) —
 * and *which* claim is on screen comes from `Reconciler.selectCard()`. This
 * composable renders the card it is handed and makes no decision about states.
 *
 * ### Colour
 *
 * The violet perforation appears on a DIFFERS card and nowhere else in the app
 * (CLAUDE.md #9). A NOT_IN_DOCUMENT card gets a dotted *open field* instead — an
 * empty form line, in plain rule grey — because "this is not written down" is an
 * absence, not a contradiction, and giving it the stamp colour would collapse two
 * different facts into one mark. There is no red, amber or green anywhere.
 */
@Composable
fun DeltaCard(
    copy: CardCopy,
    position: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val colors = VaakkuTheme.colors
    val type = VaakkuTheme.type
    val space = VaakkuTheme.space

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.sheet, RoundedCornerShape(space.radiusCard))
            .border(space.hairline, colors.ruleStrong, RoundedCornerShape(space.radiusCard)),
    ) {
        // --- Header: the state label, and the position in the queue. ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = space.base, vertical = space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = copyText(copy.stateLabel),
                style = type.label,
                color = colors.inkSoft,
                modifier = Modifier.weight(1f),
            )
            if (total > 1) {
                // Mono, because it is a count and not prose: the digits line up as
                // the queue advances instead of jittering the header.
                Text(text = "$position / $total", style = type.mono, color = colors.inkFaint)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            when (copy) {
                is CardCopy.Differs -> {
                    SpokenHalf(copyText(copy.spokenLine))
                    Perforation(colors.stamp)
                    WrittenHalf(copyText(copy.writtenLine))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = space.base, vertical = space.md),
                    ) {
                        Text(
                            text = copyText(copy.footerKey),
                            style = type.label,
                            color = colors.stamp,
                        )
                        // The small English line under the big Tamil pair, so a
                        // reader who does not read Tamil can still follow the one
                        // fact on screen (§8.2). In ENGLISH_ONLY mode the two big
                        // lines are already English and this would repeat them, so
                        // it is not drawn at all — one fact per screen (§6.6).
                        if (LocalAppLanguage.current.value != AppLanguage.ENGLISH_ONLY) {
                            Text(
                                text = copyText(
                                    copy.englishSmallLineKey,
                                    englishCopyText(copy.spokenLine.value),
                                    englishCopyText(copy.writtenLine.value),
                                ),
                                style = type.caption,
                                color = colors.inkFaint,
                                modifier = Modifier.padding(top = space.xs),
                            )
                        }
                    }
                }

                is CardCopy.NotInDocument -> {
                    SpokenHalf(copyText(copy.spokenLine))
                    Perforation(colors.rule)
                    OpenField(copyText(copy.hintKey))
                }
            }
        }
    }
}

/** The buyer's carbon copy: what was said, in the speech voice, on canary stock. */
@Composable
private fun SpokenHalf(text: String) {
    val colors = VaakkuTheme.colors
    val space = VaakkuTheme.space
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.canary)
            .padding(horizontal = space.base, vertical = space.lg),
    ) {
        AlertText(text = text, style = VaakkuTheme.type.display, color = colors.ink)
    }
}

/** The original: what the document says, in the document voice, on white. */
@Composable
private fun WrittenHalf(text: String) {
    val colors = VaakkuTheme.colors
    val space = VaakkuTheme.space
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.sheet)
            .padding(horizontal = space.base, vertical = space.lg),
    ) {
        AlertText(
            text = text,
            style = VaakkuTheme.type.display.copy(fontFamily = VoiceDocument),
            color = colors.ink,
        )
    }
}

/**
 * NOT_IN_DOCUMENT's written half: a form field with nothing written in it.
 *
 * The build plan's mapping for this state is literally "open field, dotted
 * border" (§6.6), and that is the right picture — the document was read and this
 * line of it is blank. The hint underneath turns the blank into something the
 * buyer can act on: ask about it.
 */
@Composable
private fun OpenField(hint: String) {
    val colors = VaakkuTheme.colors
    val space = VaakkuTheme.space
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.sheet)
            .padding(horizontal = space.base, vertical = space.lg),
    ) {
        // The empty line itself. Deliberately blank: there is nothing to quote.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .drawBehind {
                    drawRect(
                        color = colors.rule,
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(3.dp.toPx(), 4.dp.toPx()),
                            ),
                        ),
                    )
                },
        )
        Spacer(Modifier.height(space.md))
        AlertText(
            text = hint,
            style = VaakkuTheme.type.display.copy(fontFamily = VoiceDocument),
            color = colors.ink,
        )
    }
}

/**
 * The tear line between the two copies.
 *
 * Violet on a DIFFERS card — the one place in the app the stamp colour appears
 * (CLAUDE.md #9) — and plain rule grey on a NOT_IN_DOCUMENT card.
 */
@Composable
private fun Perforation(color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .drawBehind {
                drawLine(
                    color = color,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = size.height,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(6.dp.toPx(), 5.dp.toPx()),
                    ),
                )
            },
    )
}

/**
 * A card statement, sized to the §6.6 Session Mode rule: **alert text ≥ 34 sp,
 * target 40 sp**.
 *
 * A three-step ladder on character count rather than a measure-and-shrink loop.
 * The loop is the more general answer and the wrong one here: it needs a frame
 * where the text is laid out but not yet drawn, and the failure mode when it does
 * not settle is a card that is *blank*. On this screen a blank card is the worst
 * possible outcome — it is the one fact the buyer came for. The ladder cannot
 * fail, its floor is exactly the spec's floor, and the card scrolls if a value
 * phrase ever turns out longer than the ladder anticipated.
 *
 * The break points are set from the longest real value phrases: `v_guaranteed_pct`
 * with a two-digit rate fits the top step, and
 * `v_surrender_nil_before` — the longest phrase the domain can emit — lands on the
 * bottom one.
 */
@Composable
private fun AlertText(text: String, style: TextStyle, color: Color) {
    val size = when {
        text.length <= SHORT_STATEMENT_CHARS -> TARGET_ALERT_SP
        text.length <= MEDIUM_STATEMENT_CHARS -> MIDDLE_ALERT_SP
        else -> MIN_ALERT_SP
    }
    Text(
        text = text,
        style = style.copy(fontSize = size.sp, lineHeight = (size * ALERT_LINE_HEIGHT).sp),
        color = color,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** §6.6: "alert text ≥ 34 sp (target 40 sp)". These two are that sentence. */
private const val TARGET_ALERT_SP = 40f
private const val MIN_ALERT_SP = 34f
private const val MIDDLE_ALERT_SP = 37f

private const val SHORT_STATEMENT_CHARS = 28
private const val MEDIUM_STATEMENT_CHARS = 44

/** Tamil needs the leading — at 1.5 the marks above ஆ ொ ூ and the descenders collide. */
private const val ALERT_LINE_HEIGHT = 1.25f
