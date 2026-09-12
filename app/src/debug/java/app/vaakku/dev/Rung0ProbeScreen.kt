package app.vaakku.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.vaakku.asr.EvidenceExport
import app.vaakku.asr.Iso8601
import app.vaakku.asr.LanguageSupport
import app.vaakku.asr.RecognizerProbe
import app.vaakku.asr.Rung0Probe
import app.vaakku.asr.Rung0Report
import app.vaakku.ui.theme.Ink
import app.vaakku.ui.theme.InkMuted
import app.vaakku.ui.theme.MonoStyle
import app.vaakku.ui.theme.Paper
import app.vaakku.ui.theme.Rule
import app.vaakku.ui.theme.VaakkuTypography
import kotlinx.coroutines.launch

/**
 * The Rung-0 probe screen (build plan §6.3).
 *
 * This is the P0 go/no-go instrument for offline speech recognition. Read the
 * result like this:
 *
 *  - `isOnDeviceRecognitionAvailable()` false → engine 5 (`AndroidOnDevice`) is
 *    out on this device, and Rung 1 (sherpa-onnx) carries the whole ASR load.
 *  - Tamil `installed_on_device` present → we can transcribe offline today.
 *  - Tamil `supported_on_device` but not installed → a model download is needed,
 *    and it must happen BEFORE airplane mode goes on.
 *  - The default and on-device recognizers may be different services with
 *    different language sets, so both are shown. Engine 5 would use the
 *    on-device one; read Tamil support from that block, not the default.
 *
 * Debug-only, so the copy is plain English: the reader is the developer.
 */
@Composable
fun Rung0ProbeScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var report by remember { mutableStateOf<Rung0Report?>(null) }
    var busy by remember { mutableStateOf(false) }
    var statusLine by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        busy = true
        report = Rung0Probe.run(context)
        busy = false
    }

    // Run once on open so the screen is useful without a tap.
    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("Rung-0 probe", style = VaakkuTypography.titleLarge, color = Ink)
        Text(
            "On-device speech recognition availability",
            style = VaakkuTypography.labelMedium,
            color = InkMuted,
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = Rule)
        Spacer(Modifier.height(16.dp))

        val r = report
        if (r == null) {
            Mono(if (busy) "Probing…" else "No result yet.")
        } else {
            Section("device")
            Mono(r.deviceSummary)
            Mono("android_sdk: ${r.androidSdk}")
            Mono("captured_at: ${r.capturedAtIso}")

            Spacer(Modifier.height(14.dp))
            Section("platform")
            Mono("isRecognitionAvailable(): ${r.recognitionAvailable}")
            Mono("isOnDeviceRecognitionAvailable(): ${r.onDeviceRecognitionAvailable}")
            Mono("voice_recognition_service: ${r.defaultRecognizerRaw ?: "(null)"}")

            Spacer(Modifier.height(14.dp))
            Section("installed recognition services (${r.installedRecognitionServices.size})")
            if (r.installedRecognitionServices.isEmpty()) {
                Mono("(none visible)")
            } else {
                r.installedRecognitionServices.forEach { Mono("  $it") }
            }

            r.probes.forEach { p ->
                Spacer(Modifier.height(14.dp))
                ProbeBlock(p)
            }

            if (r.notes.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Section("notes")
                r.notes.forEach { Mono("• $it") }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = Rule)
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { scope.launch { refresh() } },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (busy) "Probing…" else "Refresh")
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        statusLine = "Requesting ta-IN model download…"
                        statusLine = Rung0Probe.triggerTamilDownload(context)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("triggerModelDownload(ta-IN)")
            }
            // The ordering caution belongs next to the button, not in a doc:
            // pressing it after airplane mode is on wastes the attempt.
            Text(
                "Needs a network — press BEFORE airplane mode goes on.",
                style = VaakkuTypography.labelSmall,
                color = InkMuted,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val fileName = "G0_rung0_${Iso8601.stamp()}.txt"
                        statusLine = EvidenceExport.writeText(context, fileName, r.toText())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Export to Download/Vaakku/evidence/")
            }

            statusLine?.let {
                Spacer(Modifier.height(10.dp))
                Mono(it)
            }
        }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("Close")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProbeBlock(p: RecognizerProbe) {
    Section("recognizer: ${p.label}")
    Mono("component: ${p.component ?: "(unresolved)"}")
    Mono("available: ${p.available}")
    if (p.error != null) {
        Mono("error: ${p.error}")
        return
    }
    LanguageBlock(p.tamil)
    LanguageBlock(p.english)
}

@Composable
private fun LanguageBlock(s: LanguageSupport) {
    Mono("  ${s.tag}:")
    if (s.error != null) {
        Mono("      error: ${s.error}")
        return
    }
    Mono("      ready_offline: ${s.readyOffline}   needs_download: ${s.needsDownload}")
    Mono("      supported: ${s.supportedOnDevice.render()}")
    Mono("      installed: ${s.installedOnDevice.render()}")
    Mono("      pending:   ${s.pendingOnDevice.render()}")
    Mono("      online:    ${s.onlineLanguages.render()}")
}

private fun List<String>.render(): String = joinToString(", ").ifEmpty { "(none)" }

@Composable
private fun Section(title: String) {
    Text(title, style = VaakkuTypography.labelMedium, color = InkMuted)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Mono(line: String) {
    Text(line, style = MonoStyle, color = Ink)
}
