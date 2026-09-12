package app.vaakku.ui.session

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vaakku.R
import app.vaakku.domain.copy.CopyBuilder
import app.vaakku.domain.copy.ValuePhrase
import app.vaakku.domain.model.ClaimType
import app.vaakku.domain.model.DeltaState
import app.vaakku.domain.model.LedgerEntry
import app.vaakku.domain.model.Observation
import app.vaakku.domain.model.Provenance
import app.vaakku.session.SessionPhase
import app.vaakku.session.SessionRuntime
import app.vaakku.session.SessionState
import app.vaakku.ui.claimTypeLabel
import app.vaakku.ui.copyText
import app.vaakku.ui.localized
import app.vaakku.ui.theme.VaakkuTheme
import app.vaakku.ui.theme.VoiceDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * The ledger, in full — build plan §6.6 screen 4.
 *
 * Six rows, always: one per [ClaimType], whether or not anything is known about
 * it. That completeness is the screen's whole job. The Session screen shows one
 * card at a time and stays silent about everything else, which is right for a
 * conversation and leaves an obvious question — *did it even look at the rest?*
 * This screen answers it: all six are listed, and the ones with nothing to say
 * show an em dash and say plainly why.
 *
 * Nothing here decides a state. Every row's label comes from
 * [CopyBuilder.stateLabel] applied to the reconciler's own
 * [app.vaakku.domain.model.DeltaState], and PENDING and UNCERTAIN both resolve to
 * the same silent dash (CLAUDE.md #2) — this screen cannot tell them apart and
 * has no business doing so.
 *
 * @param onClose leave the ledger and go back to the card.
 */
@Composable
fun DetailsSheet(onClose: () -> Unit) {
    val colors = VaakkuTheme.colors
    val type = VaakkuTheme.type
    val space = VaakkuTheme.space

    val session by SessionRuntime.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<ClaimType?>(null) }

    // Back closes the ledger. The Provenance sheet below registers its own
    // handler afterwards, so while a row is open back closes that first.
    BackHandler(onBack = onClose)

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
                // See HEADER_MIN_HEIGHT in SessionScreen.kt.
                .heightIn(min = HEADER_MIN_HEIGHT)
                .padding(vertical = space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = localized(R.string.details_title, R.string.details_title_en),
                style = type.title,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) {
                Text(
                    text = localized(R.string.action_close, R.string.action_close_en),
                    style = type.label,
                    color = colors.ink,
                )
            }
        }
        HorizontalDivider(color = colors.rule)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.gutter),
        ) {
            Spacer(Modifier.height(space.md))
            ClaimType.entries.forEach { claimType ->
                LedgerRow(
                    claimType = claimType,
                    entry = session.ledger[claimType],
                    scanCompleted = session.scanCompleted,
                    onOpen = { open = claimType },
                )
            }
            Spacer(Modifier.height(space.xxl))
        }
    }

    val opened = open
    if (opened != null) {
        ProvenanceSheet(
            claimType = opened,
            entry = session.ledger[opened],
            session = session,
            onClose = { open = null },
        )
    }
}

/**
 * One claim type's row: its name, what the reconciler says about it, and the two
 * halves side by side — said above, written below, in the two voices the whole
 * product uses to tell them apart.
 */
@Composable
private fun LedgerRow(
    claimType: ClaimType,
    entry: LedgerEntry?,
    scanCompleted: Boolean,
    onOpen: () -> Unit,
) {
    val colors = VaakkuTheme.colors
    val type = VaakkuTheme.type
    val space = VaakkuTheme.space

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = space.sm)
            .background(colors.sheet, RoundedCornerShape(space.radiusCard))
            .border(space.hairline, colors.rule, RoundedCornerShape(space.radiusCard))
            .clickable(onClick = onOpen)
            .padding(horizontal = space.base, vertical = space.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = claimTypeLabel(claimType),
                style = type.caption,
                color = colors.inkSoft,
                modifier = Modifier.weight(1f),
            )
            Text(
                // A null entry means the reconciler has never had a reason to
                // create a row for this type. That is PENDING by any other name,
                // and it gets the same dash.
                text = copyText(CopyBuilder.stateLabel(entry?.state ?: SILENT_STATE)),
                style = type.label,
                color = colors.inkSoft,
            )
        }

        Spacer(Modifier.height(space.sm))
        Text(
            text = localized(R.string.details_heading_spoken, R.string.details_heading_spoken_en),
            style = type.micro,
            color = colors.inkFaint,
        )
        Text(
            text = entry?.spoken?.let { copyText(ValuePhrase.forAny(it.value)) }
                ?: localized(R.string.details_no_spoken, R.string.details_no_spoken_en),
            style = type.body,
            color = if (entry?.spoken != null) colors.ink else colors.inkFaint,
        )

        Spacer(Modifier.height(space.sm))
        Text(
            text = localized(R.string.details_heading_document, R.string.details_heading_document_en),
            style = type.micro,
            color = colors.inkFaint,
        )
        val written = entry?.written?.maxByOrNull { it.confidence }
        Text(
            text = when {
                written != null -> copyText(ValuePhrase.forAny(written.value))
                // "Not on the pages scanned so far" and "nothing has been scanned"
                // are different facts and the buyer can act on exactly one of them.
                scanCompleted -> localized(R.string.details_no_written, R.string.details_no_written_en)
                else -> localized(R.string.details_not_scanned, R.string.details_not_scanned_en)
            },
            style = type.body.copy(fontFamily = VoiceDocument),
            color = if (written != null) colors.ink else colors.inkFaint,
        )
    }
}

/**
 * Where one row came from — build plan §6.6 screen 4: the spoken span with its
 * timestamp, the crop of the document line beside it, the question to ask, and
 * **மறுபரிசீலனை**.
 *
 * ### Why the verbatim phrase is on screen
 *
 * Everywhere else the app shows a *normalised* value — "guaranteed 8%" — because
 * that is what can be compared. Here it shows the words actually heard, because
 * this is the screen where the buyer checks the app against their own memory of
 * the conversation. If the app misheard, this is where that becomes visible, and
 * an app that cannot be caught being wrong is worse than one that can.
 *
 * The audio itself does not exist: it was never written anywhere (CLAUDE.md #4).
 * The span is text the recogniser produced and nothing more.
 *
 * ### Why மறுபரிசீலனை disappears once the session has ended
 *
 * Re-check means "set this aside and bring it back the moment it is said again"
 * (§5.7 rule 9). Nothing will be said again after the microphone closes, so on an
 * ended session the button is a promise the app cannot keep. It also has a second
 * effect that matters at that moment: `UserRecheck` is a reconciler event, and an
 * event appended after the Receipt screen has built its receipt would change the
 * hash head the buyer is looking at. Hiding it keeps the event log final from the
 * moment the session ends — see [ReceiptScreen].
 */
@Composable
private fun ProvenanceSheet(
    claimType: ClaimType,
    entry: LedgerEntry?,
    session: SessionState,
    onClose: () -> Unit,
) {
    val colors = VaakkuTheme.colors
    val type = VaakkuTheme.type
    val space = VaakkuTheme.space

    val insets = WindowInsets.safeDrawing.asPaddingValues()

    BackHandler(onBack = onClose)

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
                // See HEADER_MIN_HEIGHT in SessionScreen.kt.
                .heightIn(min = HEADER_MIN_HEIGHT)
                .padding(vertical = space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = claimTypeLabel(claimType),
                style = type.title,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) {
                Text(
                    text = localized(R.string.action_close, R.string.action_close_en),
                    style = type.label,
                    color = colors.ink,
                )
            }
        }
        HorizontalDivider(color = colors.rule)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.gutter),
        ) {
            Spacer(Modifier.height(space.base))
            Text(
                text = copyText(CopyBuilder.stateLabel(entry?.state ?: SILENT_STATE)),
                style = type.label,
                color = colors.inkSoft,
            )

            // --- What was said, on canary: the buyer's own carbon copy. ---
            Spacer(Modifier.height(space.base))
            Text(
                text = localized(R.string.details_heading_spoken, R.string.details_heading_spoken_en),
                style = type.micro,
                color = colors.inkFaint,
            )
            val spoken = entry?.spoken
            if (spoken == null) {
                Text(
                    text = localized(R.string.details_no_spoken, R.string.details_no_spoken_en),
                    style = type.body,
                    color = colors.inkFaint,
                    modifier = Modifier.padding(top = space.xs),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = space.xs)
                        .background(colors.canary, RoundedCornerShape(space.radiusCard))
                        .padding(horizontal = space.base, vertical = space.md),
                ) {
                    Text(
                        text = copyText(ValuePhrase.forAny(spoken.value)),
                        style = type.bodyLg,
                        color = colors.ink,
                    )
                    val provenance = spoken.provenance
                    if (provenance is Provenance.Spoken) {
                        Text(
                            text = stringResource(
                                R.string.prov_heard_at,
                                clockOffset(provenance.startMs, session.startedAtMs),
                                provenance.span,
                            ),
                            style = type.mono,
                            color = colors.inkSoft,
                            modifier = Modifier.padding(top = space.sm),
                        )
                    }
                }
            }

            // --- What is written, on white, with the photograph of the line. ---
            Spacer(Modifier.height(space.base))
            Text(
                text = localized(R.string.details_heading_document, R.string.details_heading_document_en),
                style = type.micro,
                color = colors.inkFaint,
            )
            val written = entry?.written?.maxByOrNull { it.confidence }
            if (written == null) {
                Text(
                    text = if (session.scanCompleted) {
                        localized(R.string.details_no_written, R.string.details_no_written_en)
                    } else {
                        localized(R.string.details_not_scanned, R.string.details_not_scanned_en)
                    },
                    style = type.body,
                    color = colors.inkFaint,
                    modifier = Modifier.padding(top = space.xs),
                )
            } else {
                WrittenProvenance(written)
            }

            // --- The thing to do about it. ---
            Spacer(Modifier.height(space.lg))
            Text(
                text = localized(R.string.prov_ask, R.string.prov_ask_en),
                style = type.micro,
                color = colors.inkFaint,
            )
            Text(
                text = copyText(CopyBuilder.followUpFor(claimType)),
                style = type.bodyLg,
                color = colors.ink,
                modifier = Modifier.padding(top = space.xs),
            )

            Spacer(Modifier.height(space.xxl))
        }

        // --- Bottom third (§6.6): the one control on this screen, and only while
        //     there is still a session to re-check into. ---
        HorizontalDivider(color = colors.rule)
        if (session.phase != SessionPhase.ENDED) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = space.gutter, vertical = space.base),
                horizontalArrangement = Arrangement.spacedBy(space.md),
            ) {
                Button(
                    // "Set this one aside, and bring it back if it is said again"
                    // (§5.7 rule 9). Closing afterwards is the honest follow-through:
                    // this sheet's subject has just been put down.
                    onClick = {
                        SessionRuntime.recheck(claimType)
                        onClose()
                    },
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
                        text = localized(R.string.session_recheck, R.string.session_recheck_en),
                        style = type.label,
                    )
                }
            }
        }
    }
}

/** The document line as read, and the photograph of it if one was saved. */
@Composable
private fun WrittenProvenance(written: Observation) {
    val colors = VaakkuTheme.colors
    val type = VaakkuTheme.type
    val space = VaakkuTheme.space

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = space.xs)
            .background(colors.sheet, RoundedCornerShape(space.radiusCard))
            .border(space.hairline, colors.rule, RoundedCornerShape(space.radiusCard))
            .padding(horizontal = space.base, vertical = space.md),
    ) {
        Text(
            text = copyText(ValuePhrase.forAny(written.value)),
            style = type.bodyLg.copy(fontFamily = VoiceDocument),
            color = colors.ink,
        )

        val provenance = written.provenance
        if (provenance !is Provenance.Written) return@Column

        // The OCR line, verbatim and in mono, under the phrase derived from it —
        // the same "you can check us" argument as the spoken span above.
        Text(
            text = provenance.lineText,
            style = type.mono,
            color = colors.inkSoft,
            modifier = Modifier.padding(top = space.sm),
        )

        Spacer(Modifier.height(space.sm))
        Text(
            text = localized(R.string.prov_crop_label, R.string.prov_crop_label_en),
            style = type.micro,
            color = colors.inkFaint,
        )
        val crop = cropBitmap(provenance.cropFile)
        if (crop == null) {
            Text(
                // Crops are best-effort: `PageScanner` records null rather than
                // failing a scan when a write does not land (CLAUDE.md #2). Saying
                // so is better than an empty box that looks like a bug.
                text = localized(R.string.prov_crop_missing, R.string.prov_crop_missing_en),
                style = type.caption,
                color = colors.inkFaint,
                modifier = Modifier.padding(top = space.xs),
            )
        } else {
            Image(
                bitmap = crop,
                contentDescription = provenance.lineText,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp)
                    .padding(top = space.xs)
                    .border(space.hairline, colors.rule),
            )
        }
    }
}
/**
 * Decodes a saved crop off the main thread, or null if the file is gone.
 *
 * `produceState` rather than `remember { decode() }`: this reads the disk, and a
 * decode on the composition thread is a dropped frame at the exact moment the
 * buyer taps a row.
 */
@Composable
private fun cropBitmap(path: String?): ImageBitmap? {
    val loaded by produceState<ImageBitmap?>(initialValue = null, path) {
        val file = path?.let { File(it) }
        value = if (file == null || !file.isFile) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()
            }
        }
    }
    return loaded
}

/**
 * `mm:ss` from the start of the session — "00:42", the format §6.6 names.
 *
 * Wall-clock time of day would be the easier thing to print and the wrong one:
 * the buyer is placing a sentence inside *this conversation*, not inside their
 * afternoon. A negative or absent start falls back to the raw offset from zero
 * rather than printing something confidently wrong.
 */
private fun clockOffset(atMs: Long, startedAtMs: Long): String {
    val elapsed = (if (startedAtMs > 0) atMs - startedAtMs else atMs).coerceAtLeast(0)
    val totalSeconds = elapsed / 1000
    return String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}

/** PENDING, i.e. the dash. Named so the two call sites above read as one decision. */
private val SILENT_STATE = DeltaState.PENDING
