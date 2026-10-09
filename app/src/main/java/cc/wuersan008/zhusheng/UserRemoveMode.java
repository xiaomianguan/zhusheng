package cc.wuersan008.zhusheng;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * How a USER store removal is run, and the user's choice of it.
 *
 * <p>The user store has no API for removing a certificate, so what can be decided is
 * who does the deleting, and that is what the three modes are:</p>
 *
 * <ul>
 *   <li>{@link #ROOT} - the files under the user store's {@code cacerts-added}
 *       directory are deleted directly, by root. The only way that empties the store in
 *       one go, and the only one that needs root at all.</li>
 *   <li>{@link #MANUAL} - the certificate list of the system settings is opened and the
 *       user removes each certificate there. Needs no root.</li>
 *   <li>{@link #AUTO} - the same list, with {@link AutoConfirmService} tapping the rows
 *       and confirming what follows. Needs no root, needs the service switched on, and
 *       needs Android 4.1 or newer.</li>
 * </ul>
 *
 * <p>The two rootless modes are the reason the choice exists: a device without root has
 * no other way out of the user store, so {@link #ROOT} is not offered there - the
 * chooser greys it out - and the mode the chooser opens on follows what the device can
 * do. The choice is remembered, and one that cannot run on the device it is read back on
 * (root that is gone) is not honoured.</p>
 */
final class UserRemoveMode {

    /** Delete the files of the user store with root. */
    static final int ROOT = 0;
    /** Remove each certificate by hand in the settings. */
    static final int MANUAL = 1;
    /** The auto-confirm service removes them. */
    static final int AUTO = 2;

    /** All modes, in the order they are offered. */
    static final int[] ALL = {ROOT, MANUAL, AUTO};

    private static final String PREFS = "zhusheng_settings";
    private static final String KEY = "user_remove_mode";

    /** Sentinel for "never chosen", so that the default can still follow the device. */
    private static final int NONE = -1;

    private UserRemoveMode() {
    }

    /**
     * The mode a removal runs in when the user has not chosen one: root when the device
     * has it, and otherwise the automatic way when the service is switched on, because
     * that is the one that needs nothing from the user.
     */
    static int defaultMode(boolean rootAvailable) {
        if (rootAvailable) {
            return ROOT;
        }
        return InstallerAutoConfirm.isEnabled() ? AUTO : MANUAL;
    }

    /** The mode to offer first: the one last chosen, unless this device cannot run it. */
    static int get(Context context, boolean rootAvailable) {
        int mode = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY, NONE);
        if (mode == MANUAL || mode == AUTO || (mode == ROOT && rootAvailable)) {
            return mode;
        }
        return defaultMode(rootAvailable);
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
            case MANUAL:
                return R.string.remove_mode_manual_note;
            case AUTO:
                return R.string.remove_mode_auto_note;
            default:
                return R.string.remove_mode_root_note;
        }
    }
}
