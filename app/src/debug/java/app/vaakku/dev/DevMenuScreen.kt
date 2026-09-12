package app.vaakku.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.vaakku.ui.theme.MonoStyle
import app.vaakku.ui.theme.VaakkuTheme
import app.vaakku.ui.theme.VaakkuTypography

/** The screens the Dev menu can show. */
enum class DevScreen(val title: String, val subtitle: String) {
    MENU("VAAKKU Dev", ""),
    RUNG0("Rung-0 probe", "Does the platform recognise Tamil offline? (§6.3)"),
    LIVE_ASR("Live ASR", "Mic or clip → VAD → engine → claim ledger (§6.3)"),
    BAKEOFF("ASR bake-off", "Every engine × every clip, RTF + slot accuracy (§11.3)"),
    SCAN("Document scan", "Camera → ML Kit → clauses read from the page, + evidence (§6.4)"),
    REPLAY("Page replay", "Staged image file → the same OCR + extractor path, no camera (§6.4)"),
}

/**
 * The debug menu — build plan §6.6.
 *
 * Five instruments, in the order they were needed: the Rung-0 probe answered
 * "can the platform do this at all", Live ASR answers "does our pipeline work",
 * the bake-off answers "which engine, and is it fast enough on this phone", and
 * Document scan answers the same two questions for the written half — does the
 * camera → OCR → extractor path read the real printed prop, and how long does
 * ML Kit take to do it (§6.4, gate G3).
 *
 * Page replay is the scan screen's other half: it runs the identical
 * `PageScanner` over an image file already on the phone, so a clause the scan
 * screen failed to read can be tried again on a clean render and the extractor
 * can be told apart from the photograph. It never touches the camera, and says
 * so.
 *
 * Debug-only, so the copy is plain English: the reader is the developer holding
 * the phone, not the buyer.
 */
@Composable
fun DevMenuScreen(onOpen: (DevScreen) -> Unit, onClose: () -> Unit) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VaakkuTheme.colors.paper)
            .padding(
                start = 24.dp,
                end = 24.dp,
                top = 24.dp + insets.calculateTopPadding(),
                bottom = 24.dp + insets.calculateBottomPadding(),
            ),
    ) {
        Text("Dev menu", style = VaakkuTypography.titleLarge, color = VaakkuTheme.colors.ink)
        Text(
            "Debug build only — absent from a release APK.",
            style = VaakkuTypography.labelMedium,
            color = VaakkuTheme.colors.inkSoft,
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = VaakkuTheme.colors.rule)
        Spacer(Modifier.height(16.dp))

        DevScreen.entries.filter { it != DevScreen.MENU }.forEach { screen ->
            OutlinedButton(
                onClick = { onOpen(screen) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(screen.title)
            }
            Text(
                screen.subtitle,
                style = MonoStyle,
                color = VaakkuTheme.colors.inkSoft,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = VaakkuTheme.colors.rule)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("Close")
        }
    }
}
