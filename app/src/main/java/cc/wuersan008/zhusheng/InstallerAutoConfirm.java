package cc.wuersan008.zhusheng;

/**
 * The two switches {@link AutoConfirmService} reads.
 *
 * <p>The service and the activity live in the same process, so plain fields are
 * enough to talk to it. {@code running} is what keeps the service honest: it only
 * touches a dialog while the app is installing certificates, so a dialog somewhere
 * else on the device is never clicked. {@code enabled} is set by the service itself
 * when the system connects to it, which is also how the app knows whether the user
 * has switched it on.
 */
final class InstallerAutoConfirm {

    private static volatile boolean running = false;
    private static volatile boolean enabled = false;

    private InstallerAutoConfirm() {
    }

    /** Called by the activity when a USER store install starts. */
    static void begin() {
        running = true;
    }

    /** Called by the activity when that install is over, however it ended. */
    static void end() {
        running = false;
    }

    static boolean isRunning() {
        return running;
    }

    static void setEnabled(boolean value) {
        enabled = value;
    }

    /** Whether the service is switched on and connected to the system. */
    static boolean isEnabled() {
        return enabled;
    }
}
