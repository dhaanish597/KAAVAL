package app.vaakku.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource

/**
 * The two UI-language modes (human's request, post-v1 — see STATUS.md decision
 * log). `TAMIL_ENGLISH` is the v1 default and shows Tamil-first strings with an
 * English gloss; `ENGLISH_ONLY` shows the English companion string everywhere on
 * this screen instead.
 *
 * This is a *display* setting only. It does not touch ASR: the product still
 * listens for code-switched Tamil–English speech regardless of this flag (build
 * plan §1) — a buyer's own display preference is a different axis from what the
 * seller says out loud.
 */
enum class AppLanguage {
    TAMIL_ENGLISH,
    ENGLISH_ONLY,
    ;

    companion object {
        val DEFAULT = TAMIL_ENGLISH
    }
}

/**
 * Persists the chosen [AppLanguage] across app restarts.
 *
 * Plain `SharedPreferences`, not DataStore: one enum value is the entire state,
 * and this app has no other preference yet that would justify the heavier
 * dependency.
 */
private object LanguagePrefs {
    private const val FILE = "vaakku_prefs"
    private const val KEY_LANGUAGE = "app_language"

    fun load(context: Context): AppLanguage {
        val stored = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
        return AppLanguage.entries.firstOrNull { it.name == stored } ?: AppLanguage.DEFAULT
    }

    fun save(context: Context, language: AppLanguage) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.name)
            .apply()
    }
}

/**
 * Holds the current [AppLanguage] as Compose state and persists every change.
 * Read with `.value`; write with `.set(...)` so a write can never forget to
 * persist.
 */
class AppLanguageState(context: Context) {
    private val appContext = context.applicationContext

    var value by mutableStateOf(LanguagePrefs.load(appContext))
        private set

    fun set(language: AppLanguage) {
        if (language == value) return
        value = language
        LanguagePrefs.save(appContext, language)
    }
}

/**
 * Provided once, near the activity root (see `MainActivity`). `staticCompositionLocalOf`
 * is deliberate: the language changes on an explicit tap, not every frame, so the
 * extra recomposition-skipping cost of `compositionLocalOf` buys nothing here.
 */
val LocalAppLanguage = compositionLocalOf<AppLanguageState> {
    error("No AppLanguageState provided — wrap the content in CompositionLocalProvider(LocalAppLanguage provides ...)")
}

/**
 * Reads the current [AppLanguage] and resolves to [taRes] or [enRes] accordingly.
 * The one call every user-facing string on this screen should go through instead
 * of a bare `stringResource`, so that flipping the Setup screen's language row
 * actually changes what is on screen.
 */
@Composable
fun localized(@StringRes taRes: Int, @StringRes enRes: Int): String {
    val language = LocalAppLanguage.current.value
    return stringResource(if (language == AppLanguage.ENGLISH_ONLY) enRes else taRes)
}
