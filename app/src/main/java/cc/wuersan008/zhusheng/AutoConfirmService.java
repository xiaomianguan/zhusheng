package cc.wuersan008.zhusheng;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/**
 * Confirms the Android certificate installer's dialogs while the app is installing
 * certificates into the USER store, and works the certificate list of the system
 * settings while the app is removing them from it.
 *
 * <p>Without it, a batch of certificates costs one confirmation per certificate,
 * which is unbearable for a bundle of a hundred roots, and a removal without root
 * would be entirely manual. The service is off unless the user switches it on in the
 * accessibility settings, and even then it only acts between
 * {@link InstallerAutoConfirm#begin()} and {@link InstallerAutoConfirm#end()}, or
 * between {@link RemovalAutoConfirm#begin} and {@link RemovalAutoConfirm#end()}, so
 * it never clicks anything outside a flow this app started.
 *
 * <p>It needs Android 4.1 or newer. The accessibility framework of Android 4.0 can
 * focus and select a button but not click it - {@code performAction(ACTION_CLICK)}
 * there answers false, because clicking arrived with
 * {@code View.performAccessibilityAction} in 4.1 - which is why the app only offers
 * this service from 4.1 up and why 4.0 devices confirm the prompts by hand.
 *
 * <p>Android 4.1 has no {@code getWindows()} (4.4), so the dialog is looked for in
 * the root of the active window first and, when the framework does not hand that
 * out, through the node an accessibility event carries: the search climbs from there
 * to the window root and looks for the affirmative button.
 *
 * <p>The removal walks the same screen a person would: it taps the row of a
 * certificate in the list of user certificates, presses the Remove button on the
 * details screen that opens, and answers the confirmation that follows. Which of the
 * three is due is told from what is on screen at that moment, and only ever in the
 * list itself - the certificate's name is printed three more times on the details
 * screen, where one of the copies sits in a dropdown that is tappable, and tapping
 * that one would get nowhere.
 */
public class AutoConfirmService extends AccessibilityService {

    /**
     * The labels Android gives the affirmative button of these dialogs. The list is
     * the framework's own {@code android.R.string.ok}, which is "OK" in almost every
     * locale and "确定" / "確定" in Chinese, plus the wording the certificate list uses
     * for a removal. The button id below covers the rest of the locales.
     */
    private static final String[] CONFIRM_LABELS = {"ok", "yes", "remove", "\u786e\u5b9a",
            "\u78ba\u5b9a", "\u5220\u9664", "\u79fb\u9664"};

    /** The positive button of every framework dialog, whatever it is called. */
    private static final String POSITIVE_BUTTON_ID = "android:id/button1";

    /** The negative button of a dialog: having one is what makes it a question. */
    private static final String NEGATIVE_BUTTON_ID = ":id/button2";

    /** The list of user certificates in the settings, and the line that names one. */
    private static final String USER_LIST_ID = ":id/user_list";
    private static final String SUBJECT_ID = ":id/trusted_credential_subject_secondary";

    /** The button on the certificate details that removes the certificate. */
    private static final String REMOVE_BUTTON_ID = ":id/cert_remove_button";

    /**
     * The wording of those buttons, for releases that do not report view ids. The
     * languages are the ones this app speaks; the ids above cover everything else.
     */
    private static final String[] REMOVE_LABELS = {"remove", "delete", "\u79fb\u9664",
            "\u5220\u9664", "\u522a\u9664", "\u0443\u0434\u0430\u043b\u0438\u0442\u044c",
            "usu\u0144", "ta bort"};

    private static final String[] NEGATIVE_LABELS = {"no", "cancel", "\u5426", "\u53d6\u6d88",
            "\u4e0d", "\u043d\u0435\u0442", "\u043e\u0442\u043c\u0435\u043d\u0430", "anuluj",
            "avbryt", "nej"};

    /** How far the search climbs and descends, as a guard against a broken tree. */
    private static final int MAX_DEPTH = 30;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        InstallerAutoConfirm.setEnabled(true);
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        InstallerAutoConfirm.setEnabled(false);
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            // Below 4.1 a click cannot be performed at all; do not even look.
            return;
        }
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return;
        }
        if (RemovalAutoConfirm.isRunning()) {
            handleRemoval();
            return;
        }
        if (!InstallerAutoConfirm.isRunning()) {
            return;
        }
        AccessibilityNodeInfo button = findConfirm(getRootInActiveWindow());
        if (button == null) {
            // The event's own node reaches the same tree, and on some releases it is
            // the only thing that is handed out.
            AccessibilityNodeInfo source = event.getSource();
            if (source != null) {
                button = findConfirm(root(source));
            }
        }
        if (button != null) {
            button.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
    }

    /**
     * Works the certificate list while the app is driving a removal: it taps the row
     * of a certificate, presses the button that removes it and answers the question
     * that follows, in the order the screen offers them.
     *
     * <p>The app stops it by calling {@link RemovalAutoConfirm#end()} when it is back
     * in front, so a list the user opened for themselves is never edited. Until then
     * it only ever touches rows whose name is one of the loaded certificates - and
     * only inside the list of user certificates, which is what keeps the roots of the
     * SYSTEM tab, which cannot be removed at all, out of reach.
     */
    private void handleRemoval() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return;
        }
        // A certificate of the list: tapping its row opens the details screen.
        AccessibilityNodeInfo row = findRow(root, RemovalAutoConfirm.names());
        if (row != null) {
            row.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            return;
        }
        // The button on that screen that removes the certificate.
        AccessibilityNodeInfo remove = findRemoveButton(root);
        if (remove != null) {
            remove.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            return;
        }
        // The yes of the confirmation that follows.
        AccessibilityNodeInfo confirm = findConfirmation(root);
        if (confirm != null) {
            confirm.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            return;
        }
        // A release that keeps the button out of sight until the details are scrolled:
        // ask the container to move on, when there is one.
        AccessibilityNodeInfo scrollable = findScrollable(root);
        if (scrollable != null) {
            scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
        }
    }

    /**
     * The row of the first certificate whose name is one of {@code names}.
     *
     * <p>The settings screen labels a row with the certificate's common name, so that
     * is what the rows are matched against; the name is compared as a prefix as well,
     * because a long one is shortened in the list. What is returned is the row that
     * takes a tap, not the text inside it.
     *
     * <p>Only a name inside the list of user certificates counts. The same name is on
     * the details screen three times over - as the dropdown, as the subject and as the
     * issuer - and one of those is tappable, so a match without that check would keep
     * opening the dropdown instead of removing anything.
     */
    private AccessibilityNodeInfo findRow(AccessibilityNodeInfo node, List<String> names) {
        if (node == null) {
            return null;
        }
        if (matchesName(node, names) && insideUserList(node)) {
            AccessibilityNodeInfo target = clickable(node);
            if (target != null) {
                return target;
            }
        }
        int children = node.getChildCount();
        for (int i = 0; i < children; i++) {
            AccessibilityNodeInfo found = findRow(node.getChild(i), names);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Whether the node carries the name of one of the certificates. */
    private boolean matchesName(AccessibilityNodeInfo node, List<String> names) {
        CharSequence text = node.getText();
        if (text == null) {
            return false;
        }
        String label = text.toString().trim();
        for (String name : names) {
            if (name.length() > 0 && label.regionMatches(true, 0, name, 0, name.length())) {
                return true;
            }
        }
        return false;
    }

    /** Whether the node belongs to the list of user certificates. */
    private boolean insideUserList(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        for (int i = 0; i < MAX_DEPTH && current != null; i++) {
            String id = viewId(current);
            if (id.endsWith(USER_LIST_ID) || id.endsWith(SUBJECT_ID)) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * The button that removes the certificate whose details are on screen.
     *
     * <p>It is looked for by its id first, which says the same in every language, and
     * by its wording second, for the releases that do not report view ids.
     */
    private AccessibilityNodeInfo findRemoveButton(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }
        if (node.isClickable()
                && (viewId(node).endsWith(REMOVE_BUTTON_ID) || hasLabel(node, REMOVE_LABELS))) {
            return node;
        }
        int children = node.getChildCount();
        for (int i = 0; i < children; i++) {
            AccessibilityNodeInfo found = findRemoveButton(node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * The yes of a confirmation dialog, and of a confirmation dialog only.
     *
     * <p>The certificate details end with a lone OK. Pressing that one would close the
     * screen before its Remove button was ever reached, so a positive button is only
     * taken when the dialog offers a way out as well - which is what makes it a
     * question.
     */
    private AccessibilityNodeInfo findConfirmation(AccessibilityNodeInfo root) {
        if (!hasNegativeButton(root)) {
            return null;
        }
        return findConfirm(root);
    }

    private boolean hasNegativeButton(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        if (node.isClickable()
                && (viewId(node).endsWith(NEGATIVE_BUTTON_ID) || hasLabel(node, NEGATIVE_LABELS))) {
            return true;
        }
        int children = node.getChildCount();
        for (int i = 0; i < children; i++) {
            if (hasNegativeButton(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /** The first container that can be scrolled, if the screen has one. */
    private AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }
        if (node.isScrollable()) {
            return node;
        }
        int children = node.getChildCount();
        for (int i = 0; i < children; i++) {
            AccessibilityNodeInfo found = findScrollable(node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Whether the node's text is one of the given labels. */
    private boolean hasLabel(AccessibilityNodeInfo node, String[] labels) {
        CharSequence text = node.getText();
        if (text == null) {
            return false;
        }
        String label = text.toString().trim();
        for (String candidate : labels) {
            if (label.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The node's view id, when the platform reports them at all. Ids arrived with
     * Android 4.3; below that the labels above are all there is to go by.
     */
    private String viewId(AccessibilityNodeInfo node) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return "";
        }
        String id = node.getViewIdResourceName();
        return id == null ? "" : id;
    }

    /** The node itself when it takes a tap, otherwise the closest ancestor that does. */
    private AccessibilityNodeInfo clickable(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        for (int i = 0; i < MAX_DEPTH && current != null; i++) {
            if (current.isClickable()) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    @Override
    public void onInterrupt() {
    }

    /** The topmost node of the window the given node belongs to. */
    private AccessibilityNodeInfo root(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        for (int i = 0; i < MAX_DEPTH && current != null; i++) {
            AccessibilityNodeInfo parent = current.getParent();
            if (parent == null) {
                return current;
            }
            current = parent;
        }
        return current;
    }

    /** Depth-first search for the affirmative button of a certificate dialog. */
    private AccessibilityNodeInfo findConfirm(AccessibilityNodeInfo node) {
        return findConfirm(node, 0);
    }

    private AccessibilityNodeInfo findConfirm(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > MAX_DEPTH) {
            return null;
        }
        if (node.isClickable() && isAffirmative(node)) {
            return node;
        }
        int children = node.getChildCount();
        for (int i = 0; i < children; i++) {
            AccessibilityNodeInfo found = findConfirm(node.getChild(i), depth + 1);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Whether the node is the button that says yes to one of these dialogs.
     *
     * <p>The label is checked first, but labels are translated and the button of the
     * certificate list does not always say OK. From Android 4.3 the id of the
     * positive button is reported as well, and that one is the same in every locale.
     */
    private boolean isAffirmative(AccessibilityNodeInfo node) {
        if (hasLabel(node, CONFIRM_LABELS)) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return POSITIVE_BUTTON_ID.equals(node.getViewIdResourceName());
        }
        return false;
    }
}
