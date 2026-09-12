package app.vaakku.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vaakku.R
import app.vaakku.asr.Rung0Probe
import app.vaakku.ui.theme.VaakkuTheme
import kotlinx.coroutines.delay

/**
 * Session setup — design spec frame 07.
 *
 * The last screen before a real conversation starts. Two jobs:
 *
 *  1. Pick the document kind, which arms the right claim types.
 *  2. Make the offline promise *checkable by anyone standing there*. The Start
 *     button stays disabled while the radio is on, so the app refuses to run
 *     online rather than merely claiming it doesn't. That is a design decision
 *     with a pitch payoff — it is enforced by the product, not asserted in a slide.
 *
 * Nothing here colour-codes a state. The one chromatic element on the screen is
 * the amber offline chip, and it marks the radio, not a claim. See the note in
 * `ui/theme/Color.kt` for why that does not reopen the door CLAUDE.md #9 closes.
 *
 * @param allowOverride when true, a long-press can bypass the airplane-mode gate.
 *   Pass `BuildConfig.DEBUG`. In a release build this must be false.
 */
@Composable
fun SetupScreen(
    onStartSession: (Domain) -> Unit,
    onOpenDevMenu: (() -> Unit)?,
    allowOverride: Boolean = false,
) {
    val context = LocalContext.current
    val colors = VaakkuTheme.colors
    val type = VaakkuTheme.type
    val space = VaakkuTheme.space

    var selected by rememberSaveable { mutableStateOf(Domain.INSURANCE) }
    var counterparty by rememberSaveable { mutableStateOf("") }

    // Live device state. Re-read on a slow tick rather than once, because the
    // human toggles airplane mode while this very screen is open, and a stale
    // "not on" that never updates would look broken at exactly the wrong moment.
    var airplaneOn by remember { mutableStateOf(DeviceState.isAirplaneModeOn(context)) }
    var micGranted by remember { mutableStateOf(DeviceState.hasMicrophone(context)) }
    var cameraGranted by remember { mutableStateOf(DeviceState.hasCamera(context)) }

    // A plain poll, not a BroadcastReceiver: the value changes only when a human
    // flips a switch, and a receiver registration would add a lifecycle to manage
    // for no gain. 1 s is fast enough that the row feels alive.
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
        // what happened to each request, but the row should show the actual
        // current grant state.
        if (results.isNotEmpty()) {
            micGranted = DeviceState.hasMicrophone(context)
            cameraGranted = DeviceState.hasCamera(context)
        }
    }

    val permissionsOk = micGranted && cameraGranted

    // Debug-only escape hatch (build plan §6.6). Development happens on a laptop
    // with the phone online — adb needs the radio — so the gate has to be
    // bypassable there and nowhere else. [allowOverride] is false in a release
    // build, so `overridden` can never become true in one.
    var overridden by rememberSaveable { mutableStateOf(false) }
    val gateSatisfied = airplaneOn || (allowOverride && overridden)
    val canStart = gateSatisfied && permissionsOk

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.paper),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.gutter)
                // Bottom inset clears the fixed Start button so the last row is
                // never trapped underneath it.
                .padding(top = space.safeTop, bottom = 112.dp),
        ) {
            // --- Title. Long-press opens the debug Dev menu. ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.setup_title),
                    style = type.title,
                    color = colors.ink,
                    modifier = Modifier.pointerInput(onOpenDevMenu) {
                        if (onOpenDevMenu != null) {
                            detectTapGestures(onLongPress = { onOpenDevMenu() })
                        }
                    },
                )
            }

            // --- 1. Domain picker ---
            Text(
                text = stringResource(R.string.setup_choose_document),
                style = type.caption,
                color = colors.inkSoft,
                modifier = Modifier.padding(top = space.xs),
            )
            Spacer(Modifier.height(space.sm + 2.dp))
            DomainGrid(selected = selected, onSelect = { selected = it })
            Text(
                text = stringResource(selected.source),
                style = type.caption,
                color = colors.inkFaint,
                modifier = Modifier.padding(top = space.md),
            )

            Spacer(Modifier.height(space.base))
            HorizontalDivider(color = colors.rule)

            // --- 2. Language: locked in v1, shown so the scope is legible. ---
            FieldRow(
                label = stringResource(R.string.setup_language),
                value = stringResource(R.string.setup_language_value),
                trailing = {
                    Text(
                        text = stringResource(R.string.setup_locked),
                        style = type.mono,
                        color = colors.inkFaint,
                    )
                },
            )

            // --- 3. Counterparty. Goes in the grievance packet header. ---
            Column(modifier = Modifier.padding(vertical = space.base)) {
                Row {
                    Text(
                        text = stringResource(R.string.setup_counterparty),
                        style = type.caption,
                        color = colors.inkSoft,
                    )
                    Text(
                        text = " · " + stringResource(R.string.setup_counterparty_optional),
                        style = type.caption,
                        color = colors.inkFaint,
                    )
                }
                BasicTextField(
                    value = counterparty,
                    onValueChange = { counterparty = it },
                    textStyle = type.bodyLg.copy(color = colors.ink),
                    cursorBrush = SolidColor(colors.ink),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = space.sm)
                        .heightIn(min = 46.dp),
                    decorationBox = { inner ->
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 34.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (counterparty.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.setup_counterparty_hint),
                                        style = type.bodyLg,
                                        color = colors.inkFaint,
                                    )
                                }
                                inner()
                            }
                            HorizontalDivider(color = colors.ruleStrong)
                        }
                    },
                )
            }
            HorizontalDivider(color = colors.rule)

            // --- 4. Permissions. Real, and actionable when missing. ---
            FieldRow(
                label = stringResource(R.string.setup_permissions),
                value = stringResource(
                    if (permissionsOk) R.string.status_granted else R.string.status_not_granted,
                ),
                trailing = {
                    if (!permissionsOk) {
                        TextButton(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.RECORD_AUDIO,
                                        Manifest.permission.CAMERA,
                                    ),
                                )
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.setup_grant),
                                style = type.label,
                                color = colors.ink,
                            )
                        }
                    }
                },
            )

            // --- 5. Offline check. The row the whole demo rests on. ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Tapping opens the real settings panel. The prototype
                    // simulates this with a toggle; on the phone it has to be the
                    // actual switch, because the point is that it is really on.
                    .clickable {
                        context.startActivity(
                            Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                    .padding(vertical = space.base),
            ) {
                Text(
                    text = stringResource(R.string.setup_offline_check),
                    style = type.caption,
                    color = colors.inkSoft,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = space.sm + 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            if (airplaneOn) R.string.status_on else R.string.status_off,
                        ),
                        style = type.bodyLg,
                        color = if (airplaneOn) colors.ink else colors.brandDeep,
                        modifier = Modifier.weight(1f),
                    )
                    if (airplaneOn) {
                        OfflineChip()
                    }
                }
                Text(
                    text = stringResource(R.string.setup_offline_help),
                    style = type.caption,
                    color = colors.inkFaint,
                    modifier = Modifier.padding(top = space.sm + 2.dp),
                )
            }
            HorizontalDivider(color = colors.rule)

            // --- 6. ASR engine (build plan §6.6). ---
            // Read once, not polled: the answer is a ROM property and cannot change
            // while this screen is open.
            val asr = remember { Rung0Probe.quickAvailability(context) }
            FieldRow(
                label = stringResource(R.string.setup_row_asr_engine),
                value = if (asr.onDeviceAvailable) {
                    asr.defaultRecognizerPackage ?: stringResource(R.string.setup_asr_available)
                } else {
                    stringResource(R.string.setup_asr_unavailable)
                },
                note = stringResource(R.string.setup_asr_note),
            )

            // --- 7. Accelerator (build plan §6.6). ---
            // CLAUDE.md #8: this row will read "not measured" until logcat proves a
            // dispatch. It is wired to nothing on purpose — there is nothing to
            // wire it to yet, and a placeholder that guessed would be a false claim.
            FieldRow(
                label = stringResource(R.string.setup_row_npu),
                value = stringResource(R.string.setup_npu_unmeasured),
                note = stringResource(R.string.setup_npu_note),
            )
            HorizontalDivider(color = colors.rule)

            // --- Evidence Mode disclosure (build plan §8.4). ---
            // Not in the prototype frame, but the build plan requires it and the
            // build plan wins. It sits last because it is a promise about what is
            // *about to* happen, read immediately before Start.
            Spacer(Modifier.height(space.lg))
            Text(
                text = stringResource(R.string.disclosure_evidence_mode),
                style = type.bodyTamil,
                color = colors.ink,
            )
            Text(
                text = stringResource(R.string.disclosure_evidence_mode_en),
                style = type.caption,
                color = colors.inkSoft,
                modifier = Modifier.padding(top = space.xs + 2.dp),
            )

            Spacer(Modifier.height(space.lg))
            Text(
                text = stringResource(R.string.setup_background_hint),
                style = type.caption,
                color = colors.inkFaint,
            )

            if (onOpenDevMenu != null) {
                Spacer(Modifier.height(space.base))
                TextButton(onClick = onOpenDevMenu, contentPadding = PaddingValues(0.dp)) {
                    Text(
                        text = stringResource(R.string.dev_menu_open),
                        style = type.label,
                        color = colors.inkSoft,
                    )
                }
            }
        }

        // --- Start. Fixed to the bottom, in the thumb zone. ---
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(colors.paper)
                .padding(horizontal = space.gutter)
                .padding(top = space.md, bottom = space.safeBottom),
        ) {
            Button(
                onClick = { onStartSession(selected) },
                enabled = canStart,
                shape = RoundedCornerShape(space.radiusButton),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.ink,
                    contentColor = colors.onInk,
                    disabledContainerColor = colors.rule,
                    disabledContentColor = colors.inkFaint,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            ) {
                Text(
                    text = stringResource(R.string.setup_start_session),
                    style = type.label.copy(fontSize = 17.sp),
                )
            }
            if (!canStart) {
                Text(
                    text = stringResource(R.string.setup_start_blocked_hint),
                    style = type.caption,
                    color = colors.inkFaint,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = space.sm),
                    textAlign = TextAlign.Center,
                )
            }
            // The override is attached to its own line rather than to the disabled
            // button: whether a disabled Material button lets a long-press reach an
            // ancestor is an implementation detail that has changed between Compose
            // versions, and a dev escape hatch that silently stops working would
            // cost debugging time at the worst moment.
            if (allowOverride && !gateSatisfied) {
                Text(
                    text = stringResource(R.string.setup_debug_override),
                    style = type.caption,
                    color = colors.inkFaint,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = space.sm)
                        .pointerInput(Unit) {
                            detectTapGestures(onLongPress = { overridden = true })
                        },
                    textAlign = TextAlign.Center,
                )
            }
            if (allowOverride && overridden && !airplaneOn) {
                Text(
                    text = stringResource(R.string.setup_debug_override_armed),
                    style = type.caption,
                    color = colors.inkFaint,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = space.sm),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 3 × 2 grid of document kinds. Hairline-separated, like cells on a form. */
@Composable
private fun DomainGrid(selected: Domain, onSelect: (Domain) -> Unit) {
    val colors = VaakkuTheme.colors
    val space = VaakkuTheme.space
    val rows = Domain.entries.chunked(3)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(space.hairline, colors.rule, RoundedCornerShape(space.radiusButton))
            .background(colors.rule, RoundedCornerShape(space.radiusButton)),
        verticalArrangement = Arrangement.spacedBy(space.hairline),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(space.hairline),
            ) {
                row.forEach { domain ->
                    DomainCell(
                        domain = domain,
                        isSelected = domain == selected,
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DomainCell(
    domain: Domain,
    isSelected: Boolean,
    onSelect: (Domain) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaakkuTheme.colors
    val type = VaakkuTheme.type
    val label = stringResource(domain.label)

    // Selection is carried by an ink fill, not a hue: the selected cell inverts.
    // That reads at a glance, survives greyscale, and cannot be mistaken for a
    // status colour.
    val bg = if (isSelected) colors.ink else colors.sheet
    val fg = if (isSelected) colors.onInk else colors.ink

    Column(
        modifier = modifier
            .height(60.dp)
            .background(bg)
            .clickable { onSelect(domain) }
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = domain.mark, style = type.mono.copy(fontSize = 11.sp), color = fg)
        Spacer(Modifier.height(4.dp))
        Text(text = label, style = type.caption, color = fg, textAlign = TextAlign.Center)
    }
}

/**
 * The airplane chip — the one pill-shaped element in the app.
 *
 * It earns that exception: it is the proof of the on-device claim, and in the
 * demo the reader's eye needs to find it instantly.
 */
@Composable
private fun OfflineChip() {
    val colors = VaakkuTheme.colors
    val type = VaakkuTheme.type
    Box(
        modifier = Modifier
            .height(28.dp)
            .background(colors.brand, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.setup_offline_chip),
            style = type.micro,
            color = colors.onBrand,
        )
    }
}

/**
 * The recurring structural device: a label, a value on a baseline, a rule under
 * it. A claim *is* a form field, so it is drawn as one — and an empty field with
 * no value is exactly how NOT_IN_DOCUMENT should look later.
 *
 * [note] exists for the rows whose value could be misread as a claim the app is
 * making about itself (see the ASR and accelerator rows).
 */
@Composable
private fun FieldRow(
    label: String,
    value: String,
    note: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = VaakkuTheme.colors
    val type = VaakkuTheme.type
    val space = VaakkuTheme.space

    Column(modifier = Modifier.padding(vertical = space.base)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = type.caption, color = colors.inkSoft)
                Text(
                    text = value,
                    style = type.bodyLg,
                    color = colors.ink,
                    modifier = Modifier.padding(top = space.xs + 2.dp),
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(space.md))
                Box(modifier = Modifier.padding(bottom = space.xs + 2.dp)) { trailing() }
            }
        }
        if (note != null) {
            Text(
                text = note,
                style = type.caption,
                color = colors.inkFaint,
                modifier = Modifier.padding(top = space.xs + 2.dp),
            )
        }
    }
}
