package cc.wuersan008.zhusheng;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * The notice shown once, on the first launch of a device the app was not designed
 * for.
 *
 * <p>zhusheng targets Android 4.0 - 6.0. Android 7.0 (API 24) stopped trusting
 * certificates installed into the user store for apps that target it or newer,
 * which takes the no-root install mode out of play for most apps on those
 * versions. Rather than letting the user download and install certificates that
 * silently change nothing, the app says so once, on the first launch, and leaves
 * the root mode on offer.</p>
 */
public final class LegacyNotice {

    /** The first Android version the app was not designed for: 7.0 (API 24). */
    private static final int FIRST_UNSUPPORTED_SDK = Build.VERSION_CODES.N;

    private static final String PREFS = "zhusheng_settings";
    private static final String KEY_SHOWN = "legacy_notice_shown";

    private LegacyNotice() {
    }

    /**
     * Shows the notice, at most once per installation, when the device runs Android
     * 7.0 or newer. Must be called once the content view exists, so that the dialog
     * lands on a drawn activity.
     */
    public static void showIfNeeded(Activity activity) {
        if (Build.VERSION.SDK_INT < FIRST_UNSUPPORTED_SDK) {
            return;
        }

        SharedPreferences prefs =
                activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_SHOWN, false)) {
            return;
        }
        // Recorded before the dialog is shown, so that a rotation or a restart while
        // it is up does not bring it back.
        prefs.edit().putBoolean(KEY_SHOWN, true).apply();

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.legacy_notice_title)
                .setMessage(R.string.legacy_notice_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        AppColors.applyToDialog(dialog, AppThemes.get(activity));
    }
}
