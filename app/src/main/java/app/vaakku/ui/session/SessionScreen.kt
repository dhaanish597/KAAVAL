package app.vaakku.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vaakku.BuildConfig
import app.vaakku.R
import app.vaakku.domain.copy.CopyBuilder
import app.vaakku.session.SessionPhase
import app.vaakku.session.SessionRuntime
import app.vaakku.session.SessionService
import app.vaakku.ui.localized
import app.vaakku.ui.theme.VaakkuTheme

/**
 * Session Mode — build plan §6.6 screen 2. The screen the phone shows while it
 * is lying on the table between two people.
 *
 * ### The shape is the accessibility spec
 *
 * §6.6 fixes it: the alert area is the **top ~60%** and every control is in the
 * **bottom third**, so a thumb reaching for "Scan" cannot cross the one line the
 * buyer is reading. Alert text is ≥34 sp (see [DeltaCard]), the surface is light,
 * and there is one fact on screen at a time — never a list of findings.
 *
 * ### What silence looks like
 *
 * Most of a sales conversation produces no card, and that is the product working.
 * The empty state is "கேட்கிறது…" and two counters: how many spoken claims have
 * been heard, how many document clauses have been read. The counters are there so
 * "nothing to report" and "nothing is reaching the app" look different — a mic
 * that has died must not resemble an honest seller (CLAUDE.md #2 makes silence
 * the default, which only works if the user can tell the two apart).
 *
 * ### Nothing here chooses what to show
 *
 * [app.vaakku.session.SessionState.card] is `Reconciler.selectCard()`'s output,
 * arriving through [SessionRuntime]. This screen renders it, pulses once when it
 * becomes a different card, and offers "அடுத்து" to set it aside. It contains no
 * comparison, no threshold and no ranking of claims.
 *
 * ### Leaving
 *
 * There is no `onExit` callback. `MainActivity` picks the screen from the session's
 * phase alone — Setup when there is no session, this screen while one is running,
 * and [ReceiptScreen] once it has ended — so "End session" only has to stop the
 * audio. One source of truth for "is there a session", shared with the microphone.
 */
@Composable
fun SessionScreen() {
    val context = LocalContext.current
    val colors = VaakkuTheme.colors
    val type = VaakkuTheme.type
    val space = VaakkuTheme.space

    val session by SessionRuntime.state.collectAsStateWithLifecycle()

    var showScan by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var showDebug by remember { mutableStateOf(false) }

    // One pulse per *new* card. Keyed on cardEpoch, which SessionRuntime advances
    // only when the card becomes a different card — so a counter tick, a rotation
    // or a recomposition cannot buzz the phone. Epoch 0 is the empty start state
    // and deliberately does not pulse.
    LaunchedEffect(session.cardEpoch) {
        if (session.cardEpoch > 0 && session.card != null) CardPulse.pulse(context)
    }

    val insets = WindowInsets.safeDrawing.asPaddingValues()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.paper)
            .then(if (BuildConfig.DEBUG) Modifier.threeFingerHold { showDebug = true } else Modifier)
            .padding(
                top = insets.calculateTopPadding(),
                bottom = insets.calculateBottomPadding(),
            ),
    ) {
        StatusLine(session.phase, session.failure != null)

        // --- The top ~60%: one fact, or the sound of listening. ---
        Box(
            modifier = Modifier
                .weight(TOP_WEIGHT)
                .fillMaxWidth()
                .padding(horizontal = space.gutter),
        ) {
            val card = session.card
            val copy = card?.let { CopyBuilder.build(it.entry) }
            if (card != null && copy != null) {
                DeltaCard(
                    copy = copy,
                    position = card.position,
                    total = card.totalPending,
                )
            } else {
                Listening(
                    spokenCount = session.spokenCount,
                    documentCount = session.documentCount,
                    failure = session.failure,
                )
            }
        }

        // --- "அடுத்து" sits with the card, not with the session controls: it is
        //     about the thing being read, and it must not appear when there is
        //     nothing to move on from. ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = space.gutter),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (session.card != null) {
                TextButton(onClick = { SessionRuntime.nextCard() }) {
                    Text(
                        text = localized(R.string.session_next, R.string.session_next_en),
                        style = type.label,
                        color = colors.ink,
                    )
                }
            } else {
                Spacer(Modifier.height(space.touchTarget))
            }
        }

        // --- The bottom third: every session control, and nothing else (§6.6). ---
        Spacer(Modifier.weight(BOTTOM_SPACER_WEIGHT))
        HorizontalDivider(color = colors.rule)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = space.gutter, vertical = space.base),
            horizontalArrangement = Arrangement.spacedBy(space.md),
        ) {
            Button(
                onClick = { showScan = true },
                shape = RoundedCornerShape(space.radiusButton),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.ink,
                    contentColor = colors.onInk,
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(space.touchTarget + space.sm),
            ) {
                Text(
                    text = localized(R.string.session_scan_document, R.string.session_scan_document_en),
                    style = type.label,
                    textAlign = TextAlign.Center,
                )
            }
            OutlinedButton(
                onClick = { showDetails = true },
                shape = RoundedCornerShape(space.radiusButton),
                modifier = Modifier
                    .weight(1f)
                    .height(space.touchTarget + space.sm),
            ) {
                Text(
                    text = localized(R.string.session_details, R.string.session_details_en),
                    style = type.label,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = space.gutter)
                .padding(bottom = space.base),
        ) {
            OutlinedButton(
                onClick = {
                    // Not stopSelf and not a cancel: this ends the *audio*, so
                    // the sentence still inside the VAD is recognised before the
                    // service shuts down. See SessionService's class KDoc.
                    //
                    // What happens next is not this screen's business. The
                    // service ends the session from its own `finally`, the phase
                    // becomes ENDED, and MainActivity swaps this screen for
                    // ReceiptScreen.
                    SessionService.stop(context)
                },
                shape = RoundedCornerShape(space.radiusButton),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(space.touchTarget + space.sm),
            ) {
                Text(
                    text = localized(R.string.session_end, R.string.session_end_en),
                    style = type.label,
                    color = colors.ink,
                )
            }
        }
    }

    if (showScan) {
        ScanSheet(onClose = { showScan = false })
    }
    if (showDetails) {
        DetailsSheet(onClose = { showDetails = false })
    }
    if (showDebug && BuildConfig.DEBUG) {
        DebugOverlay(session = session, onClose = { showDebug = false })
    }
}

/**
 * One line at the top saying what the microphone is doing.
 *
 * Words, not a coloured dot: CLAUDE.md #9 rules out a status light, and "the mic
 * did not open" is a sentence a person can act on where an amber dot is not.
 *
 * There is no line for a session that has ended, because this screen is gone by
 * then — `MainActivity` shows [ReceiptScreen] on `SessionPhase.ENDED`, including
 * when the session ended because it failed.
 */
@Composable
private fun StatusLine(phase: SessionPhase, failed: Boolean) {
    val colors = VaakkuTheme.colors
    val type = VaakkuTheme.type
    val space = VaakkuTheme.space

    val text = when {
        failed -> localized(R.string.session_mic_failed, R.string.session_mic_failed_en)
        phase == SessionPhase.LISTENING -> localized(R.string.session_listening, R.string.session_listening_en)
        else -> localized(R.string.session_starting, R.string.session_starting_en)
    }
    Text(
        text = text,
        style = type.label,
        color = colors.inkSoft,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = space.gutter, vertical = space.md),
    )
}

/**
 * The empty state — §6.6: "கேட்கிறது…" and `பேச்சு N · ஆவணம் M`.
 *
 * The counters are the honest part. They say how much the app has *taken in*,
 * which is a fact about the app, and they say nothing at all about the
 * conversation or the person having it.
 */
@Composable
private fun Listening(
    spokenCount: Int,
    documentCount: Int,
    failure: String?,
) {
    val colors = VaakkuTheme.colors
    val type = VaakkuTheme.type
    val space = VaakkuTheme.space

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.sheet, RoundedCornerShape(space.radiusCard))
            .border(space.hairline, colors.rule, RoundedCornerShape(space.radiusCard))
            .padding(horizontal = space.base, vertical = space.lg),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = localized(R.string.session_listening, R.string.session_listening_en),
            style = type.headline,
            color = colors.ink,
        )
        Text(
            text = localized(
                R.string.session_counters,
                R.string.session_counters_en,
                spokenCount,
                documentCount,
            ),
            style = type.monoValue,
            color = colors.inkSoft,
            modifier = Modifier.padding(top = space.md),
        )
        if (failure != null) {
            Text(
                // The exception, verbatim. A missing model file and a mic held by
                // another app are both ordinary at an event, and the phone has to
                // be able to say which without a laptop attached.
                text = failure,
                style = type.mono,
                color = colors.inkFaint,
                modifier = Modifier.padding(top = space.base),
            )
        }
    }
}

/**
 * Three fingers held down, anywhere on the screen — the Debug overlay's way in
 * (§6.6 screen 6).
 *
 * ### Why this gesture and why on the Initial pass
 *
 * The overlay must be unreachable by accident, because it shows engineering
 * numbers — including a [app.vaakku.domain.model.ReasonCode], which is internal
 * by construction and must never be read as something said about a person. Three
 * simultaneous fingers held for most of a second is not a thing anyone does by
 * mistake on a phone lying flat.
 *
 * Events are observed on [PointerEventPass.Initial], before children see them,
 * and **nothing is consumed** — so the buttons underneath keep working normally
 * and a one-finger press behaves exactly as it would without this modifier.
 *
 * The loop polls rather than waiting only for events: three fingers held
 * perfectly still generate no further events, which is precisely the case that
 * has to fire.
 */
private fun Modifier.threeFingerHold(onTriggered: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var heldSince: Long? = null
        var fired = false
        while (true) {
            val event = withTimeoutOrNull(POLL_MS) { awaitPointerEvent(PointerEventPass.Initial) }
            if (event != null) {
                val down = event.changes.count { it.pressed }
                if (down == 0) break
                if (down >= FINGERS) {
                    if (heldSince == null) heldSince = System.currentTimeMillis()
                } else {
                    heldSince = null
                    fired = false
                }
            }
            val since = heldSince
            if (!fired && since != null && System.currentTimeMillis() - since >= HOLD_MS) {
                fired = true
                onTriggered()
            }
        }
    }
}

/** §6.6: the alert area is the top ~60% and the controls are the bottom third. */
private const val TOP_WEIGHT = 6f
private const val BOTTOM_SPACER_WEIGHT = 0.6f

/**
 * The floor for a sheet header, not its height.
 *
 * Every header on every screen is a Tamil title beside a "மூடு" button, and Tamil
 * sets longer than the English it was laid out against — `ஆவணத்தை ஸ்கேன் செய்`
 * wraps to two lines on the phone and a fixed 56.dp clipped the second one. The
 * headers keep the 56.dp Material rhythm when the title fits on one line and grow
 * when it does not.
 */
internal val HEADER_MIN_HEIGHT = 56.dp

private const val FINGERS = 3
private const val HOLD_MS = 700L
private const val POLL_MS = 60L
