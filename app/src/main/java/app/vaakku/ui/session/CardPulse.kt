package app.vaakku.ui.session

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * One short pulse when a new card arrives — build plan §6.6 screen 2.
 *
 * ### Why this is deliberately the dullest possible buzz
 *
 * The phone is face-up on a table between two people. The buyer is listening,
 * not watching the screen, so *something* has to say "there is a line to read".
 * That something may not also say what the line is.
 *
 * [VibrationEffect.EFFECT_TICK] is the shortest primitive Android defines: a
 * single, uniform tap. It is not a pattern, it does not repeat, and it does not
 * escalate — three patterns say "this one is worse than the last one", and this
 * product does not grade anything (CLAUDE.md #1). It is the same tick for a
 * DIFFERS card and for a NOT_IN_DOCUMENT card, because the tick's whole meaning
 * is "look at the screen".
 *
 * And it is a vibration rather than a tone because CLAUDE.md #9 is absolute:
 * **no sound, ever.** A chime across a sales table is a public statement about
 * the person sitting opposite, made out loud, in front of them. The buzz is for
 * the one hand holding the phone.
 *
 * ### Failure
 *
 * Silent. A phone with the vibrator disabled, or a manufacturer that refuses the
 * effect, loses nothing that carries meaning — the card is on the screen either
 * way, and the screen is the product.
 */
internal object CardPulse {

    fun pulse(context: Context) {
        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        runCatching {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        }
    }

    /**
     * `VibratorManager` is the API 31+ way in; minSdk is 31, so there is no
     * legacy branch to keep and no deprecated call to suppress.
     */
    private fun vibrator(context: Context): Vibrator? =
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
}
