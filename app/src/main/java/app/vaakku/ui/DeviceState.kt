package app.vaakku.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Reads the two pieces of live device state the Setup screen needs.
 *
 * Both are read on every recomposition rather than cached, because the human is
 * expected to toggle airplane mode *while looking at this screen* — a cached
 * value would leave the checklist stale exactly when it matters.
 */
object DeviceState {

    /**
     * True when the radio is off.
     *
     * `Settings.Global.AIRPLANE_MODE_ON` is the honest check: it is the flag the
     * system itself uses. It does not prove there is no Wi-Fi (a user can enable
     * Wi-Fi while airplane mode is on), which is why the app additionally declares
     * no INTERNET permission at all — see scripts/check_manifest.sh. Airplane mode
     * is the human-visible demonstration; the missing permission is the guarantee.
     */
    fun isAirplaneModeOn(context: Context): Boolean = runCatching {
        Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0,
        ) == 1
    }.getOrDefault(false)

    /** True when [permission] is already granted. Never prompts. */
    fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasMicrophone(context: Context): Boolean = hasPermission(context, Manifest.permission.RECORD_AUDIO)

    fun hasCamera(context: Context): Boolean = hasPermission(context, Manifest.permission.CAMERA)

    /** True when every runtime permission the product needs has been granted. */
    fun hasAllRuntimePermissions(context: Context): Boolean =
        hasMicrophone(context) && hasCamera(context)

    /**
     * A short hardware line for evidence files. Recorded because every ASR claim
     * in this project is device-specific — build plan §6.3 and the FINAL LOCKED
     * SPEC both hinge on SM8850/Hexagon v81 behaviour, so an evidence file that
     * does not say which device produced it is not evidence.
     */
    fun hardwareLine(): String {
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}"
        } else {
            "n/a"
        }
        return "${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} " +
            "(API ${Build.VERSION.SDK_INT}) | SoC $soc | abi ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}"
    }
}
