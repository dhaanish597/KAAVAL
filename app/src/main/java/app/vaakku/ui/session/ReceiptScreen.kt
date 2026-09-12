package app.vaakku.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vaakku.R
import app.vaakku.domain.model.ClaimType
import app.vaakku.receipt.ReceiptExport
import app.vaakku.receipt.ReceiptWriter
import app.vaakku.session.SessionRuntime
import app.vaakku.ui.localized
import app.vaakku.ui.theme.VaakkuTheme
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * End of session — build plan §6.6 screen 5.
 *
 * ### What this screen is about
 *
 * A file. Not the conversation. Every line on it describes the record: how long
 * the session was, which of the six topics came up at all, the hash of the
 * record, whether the phone could sign it, and where the folder now sits so it
 * can be copied off. There is no summary of what was said and no count of any
 * state — the Details ledger is where the six topics are read, and it is one tap
 * away. A screen that totalled up differences would be a finding about the
 * person on the other side of the table, which CLAUDE.md #1 forbids outright.
 *
 * ### The head is shown before the save, and it is the same head
 *
 * §6.6 lists the hash beside the session id and the duration, i.e. before
 * anything has been exported. So the receipt is built on arrival
 * ([ReceiptWriter.build], which is §7.1 and pure), its head is printed, and
 * **that same object** is handed to [ReceiptWriter.signAndExport] when the user
 * taps save. Signing does not touch the chain — `signedWith` attaches a block
 * that `head` is computed over, never from — so the value on screen is the value
 * in the file by construction, not because two builds agreed.
 *
 * The event log cannot grow underneath it either: the microphone is closed in
 * this phase, the scan sheet is not reachable from here, and the Details sheet
 * hides its re-check button once the session has ended (see `ProvenanceSheet`).
 *
 * ### Saving twice is allowed
 *
 * The button comes back as "மீண்டும் சேமி" rather than disabling itself. Export
 * replaces what it wrote last time (see [ReceiptExport]), a buyer who is not
 * sure whether the first tap worked should be able to simply tap again, and a
 * configuration change that loses the confirmation banner must not look like a
 * screen that has locked them out of their own record.
 *
 * ### A session that failed still comes here
 *
 * `SessionService.listen()` ends the session from a `finally`, so a missing model
 * file arrives in this phase exactly like an ordinary end, with
 * [app.vaakku.session.SessionState.failure] set. The alternative — routing a
 * failed session somewhere else — would mean a microphone that died in the fourth
 * minute cost the buyer the record of the first three. So the fault is shown
 * here, first, above a record that may be a record of nothing.
 */
@Composable
fun ReceiptScreen() {
    val context = LocalContext.current
    val colors = VaakkuTheme.colors
    val type = VaakkuTheme.type
    val space = VaakkuTheme.space
    val scope = rememberCoroutineScope()

    val session by SessionRuntime.state.collectAsStateWithLifecycle()

    var showDetails by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<ReceiptWriter.Outcome?>(null) }

    // Built once per session, from the event log captured at that moment. §7.1 is
    // a few SHA-256s over a few dozen short strings, so this is cheap enough to
    // do in composition — and it has to happen before the first frame, because
    // the head is on that frame.
    val receipt = remember(session.sessionId) {
        ReceiptWriter.build(session, SessionRuntime.eventLog())
    }

    // Read once, not per frame. `durationMs` needs a "now" for the case where the
    // end was never recorded; in this phase it always was, so the clock is only a
    // fallback — but a fallback that re-read the clock on every recomposition
    // would make the number drift while the screen sat still.
    val durationText = remember(session.startedAtMs, session.endedAtMs) {
        duration(session.durationMs(System.currentTimeMillis()))
    }

    val insets = WindowInsets.safeDrawing.asPaddingValues()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.paper)
            .padding(
                top = insets.calculateTopPadding(),
                bottom = insets.calculateBottomPadding(),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = space.gutter)
                // Min, not fixed: a Tamil title that wraps must not be clipped.
                .heightIn(min = HEADER_MIN_HEIGHT)
                .padding(vertical = space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = localized(R.string.receipt_title, R.string.receipt_title_en),
                style = type.title,
                color = colors.ink,
            )
        }
        HorizontalDivider(color = colors.rule)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.gutter),
        ) {
            Spacer(Modifier.height(space.base))

            // --- Why the session could not listen, if it could not. ---
            //
            // `SessionService.listen()` ends the session in a `finally`, so a
            // missing model file or a microphone held by another app arrives here
            // exactly like an ordinary end — with `failure` set. This screen is
            // then the only place that fault is visible, and a phone at an event
            // has to be able to say what went wrong without a laptop attached.
            //
            // It is first on the screen because everything below it describes a
            // record of a conversation the app may not have heard. The counters
            // are the honest qualifier: "பேச்சு 0" under a fault means nothing
            // was taken in, and the session details below are a record of a
            // session that listened to nothing. Silence because nothing differed
            // and silence because the app was broken must never look alike.
            val failure = session.failure
            if (failure != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.sheet, RoundedCornerShape(space.radiusCard))
                        .border(space.hairline, colors.rule, RoundedCornerShape(space.radiusCard))
                        .padding(horizontal = space.base, vertical = space.md),
                ) {
                    Text(
                        text = localized(R.string.session_mic_failed, R.string.session_mic_failed_en),
                        style = type.bodyLg,
                        color = colors.ink,
                    )
                    Text(
                        text = localized(
                            R.string.session_counters,
                            R.string.session_counters_en,
                            session.spokenCount,
                            session.documentCount,
                        ),
                        style = type.monoValue,
                        color = colors.inkSoft,
                        modifier = Modifier.padding(top = space.sm),
                    )
                    Text(
                        // The exception, verbatim — the same text the Session
                        // screen shows while a session is still trying to start.
                        text = failure,
                        style = type.mono,
                        color = colors.inkFaint,
                        modifier = Modifier.padding(top = space.sm),
                    )
                }
                Spacer(Modifier.height(space.base))
            }

            Field(
                label = localized(R.string.receipt_session_id, R.string.receipt_session_id_en),
                value = session.sessionId,
            )
            Field(
                label = localized(R.string.receipt_duration, R.string.receipt_duration_en),
                value = durationText,
            )
            Field(
                label = localized(R.string.receipt_topics, R.string.receipt_topics_en),
                value = localized(
                    R.string.receipt_topics_value,
                    R.string.receipt_topics_value,
                    session.ledger.size,
                    ClaimType.entries.size,
                ),
            )
            if (receipt != null) {
                Field(
                    label = localized(R.string.receipt_head, R.string.receipt_head_en),
                    value = shortHead(receipt.head),
                )
            }

            Spacer(Modifier.height(space.base))
            HorizontalDivider(color = colors.rule)
            Spacer(Modifier.height(space.base))

            // --- Where the files are, or will be. Before the save as well as
            //     after: it is the same folder either way, and the person who has
            //     to find it later should be able to read it off the screen.
            Field(
                label = localized(R.string.receipt_location, R.string.receipt_location_en),
                value = ReceiptExport.displayFolder(session.sessionId),
            )
            Text(
                text = localized(R.string.receipt_location_note, R.string.receipt_location_note_en),
                style = type.body,
                color = colors.inkSoft,
                modifier = Modifier.padding(top = space.xs),
            )

            val result = outcome
            if (result != null) {
                Spacer(Modifier.height(space.base))
                ExportReport(result)
            }

            Spacer(Modifier.height(space.xl))

            // --- §6.6: "a static free-look reminder". Shown whatever the session
            //     found — it is a statement of law about a policy document, and it
            //     is as true after a silent session as after a card.
            Text(
                text = localized(R.string.free_look_note, R.string.free_look_note_en),
                style = type.bodyLg,
                color = colors.ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.sheet, RoundedCornerShape(space.radiusCard))
                    .border(space.hairline, colors.rule, RoundedCornerShape(space.radiusCard))
                    .padding(horizontal = space.base, vertical = space.md),
            )

            Spacer(Modifier.height(space.xxl))
        }

        // --- The bottom third (§6.6): save, then the two ways out. ---
        HorizontalDivider(color = colors.rule)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = space.gutter, vertical = space.base),
        ) {
            Button(
                onClick = {
                    if (receipt == null || exporting) return@Button
                    exporting = true
                    scope.launch {
                        // signAndExport moves itself to Dispatchers.IO; this
                        // coroutine only holds the "saving…" flag and takes the
                        // result back on the main thread.
                        outcome = runCatching { ReceiptWriter.signAndExport(context, receipt) }.getOrNull()
                        exporting = false
                    }
                },
                enabled = receipt != null && !exporting,
                shape = RoundedCornerShape(space.radiusButton),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.ink,
                    contentColor = colors.onInk,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(space.touchTarget + space.sm),
            ) {
                Text(
                    text = when {
                        exporting -> localized(R.string.receipt_exporting, R.string.receipt_exporting_en)
                        outcome != null -> localized(R.string.receipt_export_again, R.string.receipt_export_again_en)
                        else -> localized(R.string.receipt_export, R.string.receipt_export_en)
                    },
                    style = type.label,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = space.gutter)
                .padding(bottom = space.base),
            horizontalArrangement = Arrangement.spacedBy(space.md),
        ) {
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
            OutlinedButton(
                // The ledger and the event log go with the session (see
                // SessionRuntime.clear). That is why this button sits next to
                // "Save" and not before it: leaving is the end of the record's
                // life inside the app, and the folder on disk is what survives.
                onClick = { SessionRuntime.clear() },
                shape = RoundedCornerShape(space.radiusButton),
                modifier = Modifier
                    .weight(1f)
                    .height(space.touchTarget + space.sm),
            ) {
                Text(
                    text = localized(R.string.action_close, R.string.action_close_en),
                    style = type.label,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (showDetails) {
        DetailsSheet(onClose = { showDetails = false })
    }
}

/** One `label / value` pair: the label in Tamil, the value in mono. */
@Composable
private fun Field(label: String, value: String) {
    val colors = VaakkuTheme.colors
    val type = VaakkuTheme.type
    val space = VaakkuTheme.space

    Column(modifier = Modifier.padding(bottom = space.md)) {
        Text(text = label, style = type.caption, color = colors.inkSoft)
        Text(
            text = value,
            style = type.monoValue,
            color = colors.ink,
            modifier = Modifier.padding(top = space.xs),
        )
    }
}

/**
 * What the save achieved, in the order it matters.
 *
 * The failure lines come first and are never collapsed into "something went
 * wrong": a receipt that saved without three of its crops is still worth having,
 * and the person holding the phone is the only one who can decide whether to
 * scan those pages again. Naming the files is what makes that decision possible.
 */
@Composable
private fun ExportReport(outcome: ReceiptWriter.Outcome) {
    val colors = VaakkuTheme.colors
    val type = VaakkuTheme.type
    val space = VaakkuTheme.space

    Column(modifier = Modifier.fillMaxWidth()) {
        if (!outcome.chain.passed) {
            // The app's own check of the receipt it just built. This should be
            // impossible; if it ever happens, the buyer must not find out from a
            // laptop in front of a grievance officer.
            Text(
                text = localized(R.string.receipt_chain_failed, R.string.receipt_chain_failed_en),
                style = type.body,
                color = colors.ink,
                modifier = Modifier.padding(bottom = space.sm),
            )
        }

        Text(
            text = if (outcome.export.receiptWritten) {
                localized(R.string.receipt_saved, R.string.receipt_saved_en, outcome.export.fileCount)
            } else {
                localized(R.string.receipt_export_failed, R.string.receipt_export_failed_en)
            },
            style = type.body,
            color = colors.ink,
        )

        if (outcome.export.failures.isNotEmpty()) {
            Text(
                text = localized(R.string.receipt_export_incomplete, R.string.receipt_export_incomplete_en),
                style = type.body,
                color = colors.inkSoft,
                modifier = Modifier.padding(top = space.sm),
            )
            outcome.export.failures.forEach { line ->
                Text(
                    text = line,
                    style = type.mono,
                    color = colors.inkFaint,
                    modifier = Modifier.padding(top = space.xs),
                )
            }
        }

        Text(
            text = if (outcome.signed) {
                localized(R.string.receipt_signed, R.string.receipt_signed_en)
            } else {
                localized(R.string.receipt_unsigned, R.string.receipt_unsigned_en)
            },
            style = type.body,
            color = colors.inkSoft,
            modifier = Modifier.padding(top = space.sm),
        )
    }
}

/**
 * `mm:ss`, or `h:mm:ss` once a session has run past the hour.
 *
 * The same format the Provenance sheet uses for a spoken timestamp, so the two
 * screens read as one record. A sales conversation is minutes long; the hour
 * case exists because a phone left on the table does not stop.
 */
internal fun duration(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(0)
    val hours = seconds / 3600
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, (seconds % 3600) / 60, seconds % 60)
    } else {
        String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
    }
}

/**
 * The first 16 characters of the head, in groups of four.
 *
 * Sixty-four characters of hex do not fit on a phone line in mono at a size
 * anybody can read, and this value exists to be compared by eye against the hash
 * printed on the grievance packet. Four-character groups are what makes that
 * comparison possible for a human; the label beside it says "first 16 of 64" so
 * the truncation is stated rather than implied. The full head is in the exported
 * `receipt.json` and on the packet.
 */
internal fun shortHead(head: String): String =
    head.take(SHORT_HEAD_CHARS).chunked(HEAD_GROUP).joinToString(" ")

private const val SHORT_HEAD_CHARS = 16
private const val HEAD_GROUP = 4
