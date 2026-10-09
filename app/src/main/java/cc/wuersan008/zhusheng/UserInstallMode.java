package cc.wuersan008.zhusheng;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * How a USER store install is run, and the user's choice of it.
 *
 * <p>Every certificate goes through the Android certificate installer either way;
 * what a person can decide is how the confirmations go, and that is what the three
 * modes are:</p>
 *
 * <ul>
 *   <li>{@link #BUNDLE} - the whole set is offered to the installer in a single
 *       intent first, as a credentials bundle, which an installer that understands
 *       it adds in one pass. Some 4.x builds read the bundle and quietly ignore it,
 *       and the app then asks about one certificate after the other.</li>
 *   <li>{@link #SINGLE} - the bundle is not tried at all, so the installer is asked
 *       about one certificate at a time. Slower, but it behaves the same on every
 *       build.</li>
 *   <li>{@link #AUTO} - the same run as {@link #BUNDLE}, with
 *       {@link AutoConfirmService} answering the prompts. It needs the service to be
 *       switched on, and Android 4.1 or newer.</li>
 * </ul>
 *
 * <p>What the two manual modes have in common is that the app does not tell the
 * service to start ({@link InstallerAutoConfirm#begin()} is not called), so a service
 * that is switched on stays out of them: the prompts are answered by whoever chose
 * the mode. The choice is remembered, and a device that never made one starts on the
 * mode the app behaved like before there was a choice - the service answers the
 * prompts when it is switched on, and otherwise the bundle is tried first.</p>
 */
final class UserInstallMode {

    /** One prompt for the whole set: the bundle first, one certificate at a time after. */
    static final int BUNDLE = 0;
    /** One prompt per certificate: the bundle is not tried. */
    static final int SINGLE = 1;
    /** The auto-confirm service answers the prompts. */
    static final int AUTO = 2;

    /** All modes, in the order they are offered. */
    static final int[] ALL = {BUNDLE, SINGLE, AUTO};

    private static final String PREFS = "zhusheng_settings";
    private static final String KEY = "user_install_mode";

    /** Sentinel for "never chosen", so that the default can still follow the service. */
    private static final int NONE = -1;

    private UserInstallMode() {
    }

    /** The mode an install runs in when the user has not chosen one. */
    static int defaultMode() {
        return InstallerAutoConfirm.isEnabled() ? AUTO : BUNDLE;
    }

    /** The mode to offer first: the one last chosen, or the default. */
    static int get(Context context) {
        int mode = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY, NONE);
        return mode == BUNDLE || mode == SINGLE || mode == AUTO ? mode : defaultMode();
    }

    /** Stores the mode choice. */
    static void set(Context context, int mode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY, mode)
                .apply();
    }

    /** The line under the radio buttons that spells out the selected mode. */
    static int noteRes(int mode) {
        switch (mode) {
            case SINGLE:
                return R.string.install_mode_single_note;
            case AUTO:
                return R.string.install_mode_auto_note;
            default:
                return R.string.install_mode_bundle_note;
        }
    }
}
