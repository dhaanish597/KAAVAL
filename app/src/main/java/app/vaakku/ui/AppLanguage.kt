package app.vaakku.ui

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
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
 * Provided once, near the activity root (see `MainActivity`).
 *
 * `compositionLocalOf`, not `staticCompositionLocalOf`, though here the two are
 * equivalent: the *object* provided never changes, and readers subscribe to the
 * snapshot state inside [AppLanguageState.value] rather than to this local. The
 * non-static flavour is kept because it is the safe default if a future caller
 * ever provides a different state object down the tree — static would then skip
 * recomposing the readers that need to know.
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

/** [localized] for a string with `%n$…` placeholders. */
@Composable
fun localized(@StringRes taRes: Int, @StringRes enRes: Int, vararg args: Any): String {
    val language = LocalAppLanguage.current.value
    return stringResource(if (language == AppLanguage.ENGLISH_ONLY) enRes else taRes, *args)
}

/**
 * [localized] for a `<plurals>`, where [count] both selects the quantity form and
 * fills the string's one placeholder.
 *
 * The Receipt screen showed "1 files saved." on a session with a single page —
 * the same failure the duration phrases already use plurals to avoid (see the
 * comment above `v_years` in strings.xml). `%1$d` is passed as the format
 * argument as well as the quantity because every plurals resource here reads
 * "<count> <noun>"; a form that does not use its argument simply ignores it.
 */
@Composable
fun localizedPlural(@PluralsRes taRes: Int, @PluralsRes enRes: Int, count: Int): String {
    val language = LocalAppLanguage.current.value
    return pluralStringResource(
        if (language == AppLanguage.ENGLISH_ONLY) enRes else taRes,
        count,
        count,
    )
}
