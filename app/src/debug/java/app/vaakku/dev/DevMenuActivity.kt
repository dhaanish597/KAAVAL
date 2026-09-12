package app.vaakku.dev

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.vaakku.ui.theme.VaakkuTheme

/**
 * Host for the debug-only Dev menu.
 *
 * Declared in `app/src/debug/AndroidManifest.xml`, so this class is simply absent
 * from a release APK. It is reached from MainActivity by component name behind a
 * BuildConfig.DEBUG guard.
 *
 * At P0 the Dev menu contains exactly one screen — the Rung-0 probe (§6.3). The
 * ASR bake-off, the NPU benchmark and the config reload (§6.6 item 7) are added in
 * the phases that build them; shipping empty placeholders for those would only
 * make the menu harder to read.
 */
class DevMenuActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VaakkuTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Rung0ProbeScreen(
                        onClose = { finish() },
                    )
                }
            }
        }
    }
}
