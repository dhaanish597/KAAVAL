package app.vaakku.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.vaakku.R
import app.vaakku.ui.theme.Ink
import app.vaakku.ui.theme.InkMuted
import app.vaakku.ui.theme.Paper
import app.vaakku.ui.theme.Rule
import kotlinx.coroutines.delay

/**
 * The Setup screen (build plan §6.6, screen 1).
 *
 * Pre-session checklist. Its job is to make the offline claim *visible* to the
 * person about to record a sales conversation, and to make the demo's first
 * promise checkable by anyone standing there — airplane mode really is on, the
 * permissions really are granted.
 *
 * Colours here encode nothing. A checklist row that is satisfied and one that is
 * not are both plain ink; only the words differ. That is CLAUDE.md #9 (no colour
 * coding of states) — red/amber/green in a checklist would read as a ruling, and
 * this product rules on no one.
 */
@Composable
fun SetupScreen(
    onStartSession: () -> Unit,
    onOpenDevMenu: (() -> Unit)?,
) {
    val context = LocalContext.current

    // Live device state. Re-read on a slow tick rather than once, because the
    // human toggles airplane mode while this very screen is open, and a stale
    // "not on" that never updates would look broken at exactly the wrong moment.
    var airplaneOn by remember { mutableStateOf(DeviceState.isAirplaneModeOn(context)) }
    var micGranted by remember { mutableStateOf(DeviceState.hasMicrophone(context)) }
    var cameraGranted by remember { mutableStateOf(DeviceState.hasCamera(context)) }

    // A plain poll, not a BroadcastReceiver: the value changes only when a human
    // flips a switch, and a receiver registration would add a lifecycle to manage
    // for no gain. 1 s is fast enough that the checklist feels alive.
    LaunchedEffect(Unit) {
        while (true) {
            airplaneOn = DeviceState.isAirplaneModeOn(context)
            micGranted = DeviceState.hasMicrophone(context)
            cameraGranted = DeviceState.hasCamera(context)
            delay(1_000)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        // Re-read from the system rather than trusting the map: the map reports
        // what happened to each request, but the rows should show the actual
        // current grant state.
        if (results.isNotEmpty()) {
            micGranted = DeviceState.hasMicrophone(context)
            cameraGranted = DeviceState.hasCamera(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        // --- Title. Long-press opens the debug Dev menu (§6.6 says long-press). ---
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayLarge,
            color = Ink,
            modifier = Modifier.pointerInput(onOpenDevMenu) {
                if (onOpenDevMenu != null) {
                    detectTapGestures(onLongPress = { onOpenDevMenu() })
                }
            },
        )
        Text(
            text = stringResource(R.string.app_subtitle),
            style = MaterialTheme.typography.titleMedium,
            color = InkMuted,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(Modifier.height(28.dp))
        HorizontalDivider(color = Rule)
        Spacer(Modifier.height(20.dp))

        // --- Evidence Mode disclosure (§8.4). Tamil first, English beneath it. ---
        // The disclosure is a promise to the person being recorded, so it is set
        // in body-large Tamil at the top, not buried as fine print.
        Text(
            text = stringResource(R.string.disclosure_evidence_mode),
            style = MaterialTheme.typography.bodyLarge,
            color = Ink,
        )
        Text(
            text = stringResource(R.string.disclosure_evidence_mode_en),
            style = MaterialTheme.typography.labelMedium,
            color = InkMuted,
            modifier = Modifier.padding(top = 6.dp),
        )

        Spacer(Modifier.height(28.dp))

        Text(
            text = stringResource(R.string.setup_checklist_header),
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
        )
        Spacer(Modifier.height(12.dp))

        // --- Row 1: airplane mode. LIVE. Gates Start. ---
        ChecklistRow(
            label = stringResource(R.string.setup_row_airplane),
            status = stringResource(if (airplaneOn) R.string.status_on else R.string.status_off),
        )

        // --- Row 2: mic + camera. LIVE, and actionable when not granted. ---
        ChecklistRow(
            label = stringResource(R.string.setup_row_mic_camera),
            status = stringResource(
                if (micGranted && cameraGranted) R.string.status_granted else R.string.status_not_granted,
            ),
            trailing = {
                if (!micGranted || !cameraGranted) {
                    OutlinedButton(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA),
                            )
                        },
                    ) {
                        Text("Grant", style = MaterialTheme.typography.labelMedium)
                    }
                }
            },
        )

        // --- Rows 3 and 4: honestly pending. ---
        // The ASR engine is chosen in P1/P2 and the NPU accelerator is proved by
        // logcat in P2. Printing a name here before either is true would be the
        // exact dishonesty CLAUDE.md #8 forbids, so these say pending until they
        // have something measured to report.
        ChecklistRow(
            label = stringResource(R.string.setup_row_asr_engine),
            status = stringResource(R.string.status_pending),
        )
        ChecklistRow(
            label = stringResource(R.string.setup_row_npu),
            status = stringResource(R.string.status_pending),
        )

        Spacer(Modifier.height(28.dp))

        // --- Start. Disabled until airplane mode is ON (§6.6). ---
        Button(
            onClick = onStartSession,
            enabled = airplaneOn,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.setup_start_session),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }

        if (!airplaneOn) {
            Text(
                text = stringResource(R.string.setup_start_blocked_hint),
                style = MaterialTheme.typography.labelMedium,
                color = InkMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(24.dp))

        // --- OriginOS survival hint (§6.7). ---
        Text(
            text = stringResource(R.string.setup_background_hint),
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted,
        )

        if (onOpenDevMenu != null) {
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = onOpenDevMenu) {
                Text(stringResource(R.string.dev_menu_open), style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

/**
 * One checklist line: a label on the left, its status on the right.
 *
 * Both halves are the same ink weight, so a satisfied row and an unsatisfied one
 * are distinguished only by the words. See the no-colour-coding note above.
 */
@Composable
private fun ChecklistRow(
    label: String,
    status: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = Ink,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        if (trailing != null) {
            trailing()
        } else {
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
