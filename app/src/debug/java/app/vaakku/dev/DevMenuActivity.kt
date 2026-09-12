package app.vaakku.dev

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import app.vaakku.ui.theme.VaakkuTheme

/**
 * Host for the debug-only Dev menu.
 *
 * Declared in `app/src/debug/AndroidManifest.xml`, so this class is simply absent
 * from a release APK. It is reached from MainActivity by component name behind a
 * BuildConfig.DEBUG guard.
 *
 * Four screens as of P3: the Rung-0 probe (§6.3), Live ASR (§6.3), the ASR
 * bake-off (§11.3), Document scan (§6.4) and Page replay, which re-runs the scan
 * screen's own OCR + extractor path over an image file already on the phone. The
 * NPU benchmark and the config reload (§6.6 item 7) land in the phases that build
 * them; empty placeholders would only make the menu harder to read.
 *
 * Navigation is one `var` rather than a nav library. There are six destinations
 * and no deep links, and a dependency added for the Dev menu would end up in the
 * release APK for nothing.
 */
class DevMenuActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The bake-off takes minutes and the RTF it measures is only honest if
        // the phone is not throttling behind a locked screen, so the screen is
        // held on for the whole Dev session.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            VaakkuTheme {
                var screen by remember { mutableStateOf(DevScreen.MENU) }

                Surface(modifier = Modifier.fillMaxSize()) {
                    when (screen) {
                        DevScreen.MENU -> DevMenuScreen(
                            onOpen = { screen = it },
                            onClose = { finish() },
                        )
                        DevScreen.RUNG0 -> Rung0ProbeScreen(
                            onClose = { screen = DevScreen.MENU },
                        )
                        DevScreen.LIVE_ASR -> LiveAsrScreen(
                            onClose = { screen = DevScreen.MENU },
                        )
                        DevScreen.BAKEOFF -> AsrBakeoffScreen(
                            onClose = { screen = DevScreen.MENU },
                        )
                        DevScreen.SCAN -> DocumentScanScreen(
                            onClose = { screen = DevScreen.MENU },
                        )
                        DevScreen.REPLAY -> PageReplayScreen(
                            onClose = { screen = DevScreen.MENU },
                        )
                    }
                }
            }
        }
    }
}
