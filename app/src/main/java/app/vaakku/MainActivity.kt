package app.vaakku

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vaakku.session.SessionEvidence
import app.vaakku.session.SessionPhase
import app.vaakku.session.SessionRuntime
import app.vaakku.session.SessionService
import app.vaakku.ui.AppLanguageState
import app.vaakku.ui.LocalAppLanguage
import app.vaakku.ui.SetupScreen
import app.vaakku.ui.session.SessionScreen
import app.vaakku.ui.theme.VaakkuTheme

/**
 * The only activity in the app.
 *
 * It hosts the Setup screen and the Session screen (§6.6). They are states of one
 * activity rather than two activities on purpose: Session Mode must survive a
 * screen-off cycle and a rotation without the microphone pipeline noticing (build
 * plan §6.4), and the pipeline itself lives in [SessionService] with its state in
 * [SessionRuntime], so the UI can be destroyed and rebuilt around it freely.
 *
 * There is deliberately no INTERNET permission and no networking code anywhere in
 * this app — see scripts/check_manifest.sh, which proves it on the merged manifest.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // The chosen UI language (Tamil + English / English only) is provided
            // once, here, so every screen underneath can read and change it via
            // LocalAppLanguage without threading a parameter through each one.
            // Held in `remember`, not `rememberSaveable`: it already persists itself
            // to SharedPreferences (see AppLanguage.kt) and reloads from there on
            // the next `AppLanguageState(...)` construction, so a second save
            // mechanism would be redundant.
            val languageState = remember { AppLanguageState(applicationContext) }

            // Which screen is showing. Derived from SessionRuntime rather than held
            // here: the session is the thing that exists, and a `var screen` kept
            // beside it is a second source of truth that can disagree with the
            // microphone. Re-entering the app from the notification while a session
            // is running therefore lands on the Session screen, which is right, and
            // `SessionRuntime.clear()` is all it takes to come back here.
            val session by SessionRuntime.state.collectAsStateWithLifecycle()

            CompositionLocalProvider(LocalAppLanguage provides languageState) {
                // Day explicitly, not isSystemInDarkTheme(): Day looks like paper and
                // photographs better under stage lighting, and the demo must not flip
                // because the phone happened to be in dark mode. A user-facing theme
                // setting can come later; until then this is a deliberate constant.
                VaakkuTheme(night = false) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        if (session.phase == SessionPhase.IDLE) {
                            SetupScreen(
                                onStartSession = { startSession() },
                                onOpenDevMenu = devMenuLauncher(),
                                allowOverride = BuildConfig.DEBUG,
                            )
                        } else {
                            // Back does not leave a *running* session. The mic is
                            // open and a notification says so; walking out with a
                            // stray swipe would leave the phone listening behind a
                            // screen that no longer says it. Once the session has
                            // ended, back is an ordinary way out.
                            BackHandler(enabled = true) {
                                if (session.phase == SessionPhase.ENDED) SessionRuntime.clear()
                            }
                            SessionScreen()
                        }
                    }
                }
            }
        }
    }

    /**
     * Mints the session and opens the microphone, in that order.
     *
     * [SessionRuntime.startSession] first so the id exists before anything can
     * write evidence against it, and because the service reports its own progress
     * back into the state this call initialises. The service is started from a
     * button press in a visible activity, which is the only way Android permits a
     * microphone-type foreground service to start — and the correct rule: a
     * session begins because a person tapped "start".
     *
     * The chosen [app.vaakku.ui.Domain] is not carried into the session yet. The
     * reconciler's rules are the same for all six domains in v1 (§5.7), and the
     * receipt is where the domain will need to be recorded — P6's problem, noted
     * here so it is not silently dropped.
     */
    private fun startSession() {
        SessionRuntime.startSession(
            sessionId = SessionEvidence.newSessionId(this, prefix = "session"),
            nowMs = System.currentTimeMillis(),
        )
        SessionService.start(this)
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
