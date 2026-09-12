package app.vaakku.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.vaakku.R
import app.vaakku.domain.model.ClaimType
import app.vaakku.session.SessionState
import app.vaakku.ui.localized
import app.vaakku.ui.theme.VaakkuTheme
import java.util.Locale

/**
 * The engineering read-out — build plan §6.6 screen 6. Reached only by a
 * three-finger hold on the Session screen, and only in a debug build.
 *
 * ### Why this is gated twice
 *
 * Everything on this screen is a number the app measured about *itself*: decode
 * milliseconds, real-time factor, segment count, OCR milliseconds, and the
 * reconciler's internal [app.vaakku.domain.model.ReasonCode]. `ReasonCode` in
 * particular is defined as internal by construction — `Types.kt` says it "must
 * never describe a person" — and the reason it is safe to render here is that
 * "here" does not exist in a release build and is not reachable by accident in a
 * debug one.
 *
 * ### Why it exists
 *
 * CLAUDE.md #7 and #8: a gate passes on measured numbers, and no label may claim
 * something that was not measured. During a demo the laptop is not attached, so
 * the only way to answer "is the engine actually keeping up?" is to be able to
 * read it off the phone. Everything here is a fact about the software; none of
 * it is a statement about the conversation.
 *
 * Deliberately in English and deliberately in mono: it is a log, not copy.
 */
@Composable
internal fun DebugOverlay(session: SessionState, onClose: () -> Unit) {
    val colors = VaakkuTheme.colors
    val type = VaakkuTheme.type
    val space = VaakkuTheme.space

    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val telemetry = session.telemetry

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
                text = "Debug",
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
                .padding(horizontal = space.gutter, vertical = space.base),
        ) {
            Field("session", session.sessionId.ifEmpty { "—" })
            Field("phase", session.phase.name)
            Field("engine", telemetry.engineName.ifEmpty { "—" })
            Field("failure", session.failure ?: "—")

            Gap()
            Field("segments", telemetry.segmentCount.toString())
            Field("spoken obs", session.spokenCount.toString())
            Field("last audio ms", telemetry.lastAudioMs.toString())
            Field("last decode ms", telemetry.lastDecodeMs.toString())
            // Real-time factor: decode time over audio time. Below 1.0 means the
            // engine is keeping up with speech; §11.5 is written against it.
            Field("rtf", String.format(Locale.US, "%.2f", telemetry.lastRtf))
            Field("seg quality", String.format(Locale.US, "%.2f", telemetry.lastSegmentQuality))

            Gap()
            Field("pages", session.pageCount.toString())
            Field("written obs", session.documentCount.toString())
            Field("last ocr ms", telemetry.lastOcrMs.toString())
            Field("scan completed", session.scanCompleted.toString())

            Gap()
            Field("card epoch", session.cardEpoch.toString())
            Field("card", session.card?.let { "${it.type} ${it.entry.state}" } ?: "—")
            Field("last reason", telemetry.lastReason?.name ?: "—")

            Gap()
            // The whole ledger at once, which no user-facing screen ever shows:
            // the Session screen shows one card and Details shows six rows without
            // their internals. Here the internals are the point.
            ClaimType.entries.forEach { claimType ->
                val entry = session.ledger[claimType]
                Field(
                    claimType.name,
                    entry?.let {
                        "${it.state} ${it.reason} n=${it.mentionCount}" +
                            " sp=${if (it.spoken != null) 1 else 0} wr=${it.written.size}" +
                            if (it.dismissed) " set-aside" else ""
                    } ?: "—",
                )
            }

            Gap()
            Field("last segment", telemetry.lastSegmentText.ifEmpty { "—" })

            Spacer(Modifier.height(space.xxl))
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    val colors = VaakkuTheme.colors
    val type = VaakkuTheme.type
    val space = VaakkuTheme.space
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = space.xs)) {
        Text(
            text = label,
            style = type.mono,
            color = colors.inkFaint,
            modifier = Modifier.width(LABEL_WIDTH),
        )
        Text(text = value, style = type.mono, color = colors.ink, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Gap() {
    val colors = VaakkuTheme.colors
    val space = VaakkuTheme.space
    Spacer(Modifier.height(space.sm))
    HorizontalDivider(color = colors.rule)
    Spacer(Modifier.height(space.sm))
}

private val LABEL_WIDTH = 120.dp
