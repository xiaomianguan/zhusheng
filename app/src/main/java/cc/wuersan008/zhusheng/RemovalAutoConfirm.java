package cc.wuersan008.zhusheng;

import java.util.ArrayList;
import java.util.List;

/**
 * The switches {@link AutoConfirmService} reads while a removal is being driven
 * through the certificate list of the system settings.
 *
 * <p>Like {@link InstallerAutoConfirm} these are plain fields, because the activity
 * and the service share a process. {@code running} is the honest part: the service
 * only touches a row while a removal this app started is in progress, so a list
 * somebody opened themselves is never edited. {@code names} is what it matches rows
 * against - the common names of the certificates that are loaded here, which is how
 * the settings screen labels them too.
 */
final class RemovalAutoConfirm {

    private static volatile boolean running = false;
    private static volatile List<String> names = new ArrayList<String>();

    private RemovalAutoConfirm() {
    }

    /** Called by the activity just before it opens the certificate list. */
    static void begin(List<String> wanted) {
        names = wanted;
        running = true;
    }

    /** Called by the activity when it is back in front, however it ended. */
    static void end() {
        running = false;
        names = new ArrayList<String>();
    }

    static boolean isRunning() {
        return running;
    }

    /** The certificate names to look for, as the settings screen shows them. */
    static List<String> names() {
        return names;
    }
}
