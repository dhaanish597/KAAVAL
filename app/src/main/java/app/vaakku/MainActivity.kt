package app.vaakku

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.vaakku.ui.SetupScreen
import app.vaakku.ui.theme.VaakkuTheme

/**
 * The only activity in the app, for now.
 *
 * It hosts the Setup screen (§6.6). Session, Camera and Receipt screens arrive in
 * later phases; when they do they should be navigation destinations inside this
 * activity rather than new activities, because Session Mode must keep the process
 * alive across a screen-off cycle (build plan §6.4).
 *
 * There is deliberately no INTERNET permission and no networking code anywhere in
 * this app — see scripts/check_manifest.sh, which proves it on the merged manifest.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VaakkuTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SetupScreen(
                        onStartSession = {
                            // P2 wires this to the Session screen. Until that screen
                            // exists, doing nothing is better than a fake
                            // transition that would look like a working feature.
                        },
                        onOpenDevMenu = devMenuLauncher(),
                    )
                }
            }
        }
    }

    /**
     * Returns a launcher for the debug-only Dev menu, or null in a release build.
     *
     * The Dev menu lives in `app/src/debug/` and therefore does not exist in a
     * release APK. It is launched by *component name* (a string) rather than a
     * class reference, because main source referencing debug source would not
     * compile. Returning null in release makes the entry point unreachable there,
     * which is a stronger guarantee than hiding a button.
     */
    private fun devMenuLauncher(): (() -> Unit)? {
        if (!BuildConfig.DEBUG) return null
        return {
            runCatching {
                startActivity(
                    Intent().setClassName(packageName, DEV_MENU_ACTIVITY),
                )
            }
        }
    }

    private companion object {
        /** Fully-qualified name of the debug-only Dev menu activity. */
        const val DEV_MENU_ACTIVITY = "app.vaakku.dev.DevMenuActivity"
    }
}
