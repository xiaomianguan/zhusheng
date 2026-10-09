package cc.wuersan008.zhusheng;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Main screen: lists the official root-certificate sources, downloads the ones
 * the user selected and installs them into the SYSTEM store (root) or the USER
 * store (no root).
 */
public class MainActivity extends Activity {

    private static final int REQ_INSTALL_USER = 1001;
    private static final int REQ_INSTALL_BULK = 1002;
    private static final int MENU_THEME = 1;
    private static final int MENU_LANGUAGE = 2;
    private static final int MENU_WEBSITE = 3;
    private static final int MENU_GITHUB = 4;
    private static final int MENU_ABOUT = 5;
    private static final int MENU_REVOLUTION = 6;
    private static final int MENU_AUTO_CONFIRM = 7;

    /**
     * What a dialog that sends the user to the accessibility settings leaves behind,
     * so that the flow it interrupted can be picked up again on the way back (see
     * {@link #onResume}).
     */
    private static final int PENDING_NONE = 0;
    private static final int PENDING_AUTO_INSTALL = 1;
    private static final int PENDING_AUTO_REMOVAL = 2;

    /**
     * How long to wait before reading the auto-confirm switch after coming back from
     * the accessibility settings. The system connects the service to the app when it
     * is switched on, which takes a moment; asking right away would sometimes answer
     * "still off" about a service that was just enabled.
     */
    private static final int SERVICE_CONNECT_DELAY_MS = 500;

    /**
     * The settings screens that list the user certificates, most specific first. The
     * first one shows the USER tab alone, the second the screen with both tabs, and
     * the security page is the fallback for releases that have neither. They are
     * spelled out because the framework only exposes the older two as hidden
     * constants of the settings package.
     */
    private static final String ACTION_TRUSTED_CREDENTIALS_USER =
            "com.android.settings.TRUSTED_CREDENTIALS_USER";
    private static final String ACTION_TRUSTED_CREDENTIALS =
            "com.android.settings.TRUSTED_CREDENTIALS";

    /**
     * View ids for the color swatches of the theme picker. The swatches are built
     * at runtime, so their ids are made up; they only have to be unique within the
     * dialog and must not clash with a generated resource id.
     */
    private static final int ID_HOLO_SWATCH = 0x1000;
    private static final int ID_PRIMARY_SWATCH = 0x2000;
    private static final int ID_ACCENT_SWATCH = 0x3000;

    /** Size of a color swatch and the gap between two of them, in dp. */
    private static final int SWATCH_DP = 32;
    private static final int SWATCH_GAP_DP = 8;

    /** How many swatches fit on one line of the theme picker. */
    private static final int SWATCHES_PER_ROW = 6;

    /** The theme radio buttons of the picker, in the order of {@link AppThemes#ALL}. */
    private static final int[] THEME_OPTIONS = {
            R.id.theme_option_holo,
            R.id.theme_option_material,
            R.id.theme_option_device_default
    };

    /** The color-scheme radio buttons, in the order of {@link AppThemes#SCHEMES}. */
    private static final int[] SCHEME_OPTIONS = {
            R.id.theme_scheme_light,
            R.id.theme_scheme_dark
    };

    /**
     * The radio buttons of the USER store install chooser, in the order of
     * {@link UserInstallMode#ALL}.
     */
    private static final int[] INSTALL_MODE_OPTIONS = {
            R.id.install_mode_bundle,
            R.id.install_mode_single,
            R.id.install_mode_auto
    };

    /**
     * The radio buttons of the USER store removal chooser, in the order of
     * {@link UserRemoveMode#ALL}.
     */
    private static final int[] REMOVE_MODE_OPTIONS = {
            R.id.remove_mode_root,
            R.id.remove_mode_manual,
            R.id.remove_mode_auto
    };

    private static final String URL_WEBSITE = "https://zs.523008.cc";
    private static final String URL_GITHUB = "https://github.com/xiaomianguan/zhusheng";
    private static final String URL_REVOLUTION = "https://cdn.dribbble.com/userupload/23009584/"
            + "file/original-6260839b002dbf58248081c08a47d6e3.png";

    private final Handler ui = new Handler();
    /** The sources, filled in by {@link #onCreate} once the language is applied. */
    private final List<CertSource> sources = new ArrayList<CertSource>();
    private final List<X509Certificate> downloaded = new ArrayList<X509Certificate>();

    private SourceAdapter adapter;
    private TextView deviceInfo;
    private TextView statusView;
    private ProgressBar progress;
    private Button rebootButton;

    private volatile boolean busy = false;
    private volatile boolean rootAvailable = false;

    /**
     * How many roots the SYSTEM store holds that no app may read, as of the last
     * {@link #refreshDeviceInfo} (see {@link TrustedRoots#unreadableSystemRoots}).
     */
    private int unreadableRoots = 0;

    private final List<X509Certificate> userQueue = new ArrayList<X509Certificate>();
    private int userQueueIndex = 0;
    /**
     * The certificates a USER store install was asked for: what the download holds
     * minus what the device already trusts. Kept so that a bulk install can be
     * checked against it and the rest done one by one.
     */
    private final List<X509Certificate> bulkPending = new ArrayList<X509Certificate>();

    /**
     * Of the certificates the running USER store install was asked for, the ones the
     * SYSTEM store already holds. Neither the installer nor this app can put those into
     * the user store, so they are skipped and named to the user instead (see
     * {@link TrustedRoots#systemTrusted}).
     */
    private final List<X509Certificate> systemSkipped = new ArrayList<X509Certificate>();

    /** The mode the running USER store install was started in (see UserInstallMode). */
    private int userInstallMode = UserInstallMode.BUNDLE;

    /** What is waiting for the auto-confirm service, or {@link #PENDING_NONE}. */
    private int pendingAutoConfirm = PENDING_NONE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply the saved language, then the theme and its colors, before the
        // window/content is built.
        AppLanguage.apply(this);

        // The catalog carries localized provider names and notes, so it is
        // built here rather than in a field initializer: those run before the
        // saved language has been applied and would use the system locale.
        sources.addAll(CertCatalog.build(this));

        int theme = AppThemes.get(this);
        int scheme = AppThemes.getScheme(this);
        setTheme(AppThemes.styleRes(theme, scheme));
        AppColors.applyTheme(this, theme, scheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        deviceInfo = (TextView) findViewById(R.id.device_info);
        deviceInfo.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showUnreadableRootsNotice();
            }
        });
        statusView = (TextView) findViewById(R.id.status);
        progress = (ProgressBar) findViewById(R.id.progress);
        rebootButton = (Button) findViewById(R.id.btn_reboot);
        ListView listView = (ListView) findViewById(R.id.list_sources);

        adapter = new SourceAdapter(this, sources, AppColors.widgetTint(this, theme));
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                CertSource source = sources.get(position);
                source.selected = !source.selected;
                adapter.notifyDataSetChanged();
            }
        });

        refreshDeviceInfo();

        click(R.id.btn_select_all, new View.OnClickListener() {
            public void onClick(View v) {
                select(true, false);
            }
        });
        click(R.id.btn_select_recommended, new View.OnClickListener() {
            public void onClick(View v) {
                select(false, true);
            }
        });
        click(R.id.btn_select_none, new View.OnClickListener() {
            public void onClick(View v) {
                select(false, false);
            }
        });
        click(R.id.btn_download, new View.OnClickListener() {
            public void onClick(View v) {
                startDownload();
            }
        });
        click(R.id.btn_install_system, new View.OnClickListener() {
            public void onClick(View v) {
                startSystemInstall();
            }
        });
        click(R.id.btn_install_user, new View.OnClickListener() {
            public void onClick(View v) {
                startUserInstall();
            }
        });
        click(R.id.btn_remove_system, new View.OnClickListener() {
            public void onClick(View v) {
                confirmRemoval(true);
            }
        });
        click(R.id.btn_remove_user, new View.OnClickListener() {
            public void onClick(View v) {
                startUserRemoval();
            }
        });
        rebootButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                rebootDevice();
            }
        });

        detectRoot();
        AppColors.applyToActivity(this, theme);

        // On Android 7.0 and newer - which the app was not designed for - explain
        // once what still works there and what does not.
        LegacyNotice.showIfNeeded(this);
    }

    private void click(int id, View.OnClickListener listener) {
        findViewById(id).setOnClickListener(listener);
    }

    private void refreshDeviceInfo() {
        String text = "Android " + Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ")"
                + "  •  " + Build.CPU_ABI
                + "  •  " + (rootAvailable
                ? getString(R.string.status_root_yes)
                : getString(R.string.status_root_no));
        // A root the store lists but no app may read is a fault of the device rather
        // than of a run, and it is what makes an install look like it did nothing, so
        // it is named here - where the rest of what this device is gets named - and
        // explained when this line is tapped.
        unreadableRoots = TrustedRoots.unreadableSystemRoots();
        if (unreadableRoots > 0) {
            text += "  •  " + getString(R.string.device_info_unreadable_roots, unreadableRoots);
        }
        deviceInfo.setText(text);
    }

    /** Explains the unreadable roots this device has, if it has any. */
    private void showUnreadableRootsNotice() {
        if (unreadableRoots <= 0) {
            return;
        }
        AlertDialog notice = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_unreadable_roots_title)
                .setMessage(getString(R.string.dialog_unreadable_roots_message, unreadableRoots))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .show();
        AppColors.applyToDialog(notice, AppThemes.get(this));
    }

    private void detectRoot() {
        new Thread(new Runnable() {
            public void run() {
                final boolean root = RootUtils.isRootAvailable();
                ui.post(new Runnable() {
                    public void run() {
                        rootAvailable = root;
                        refreshDeviceInfo();
                    }
                });
            }
        }).start();
    }

    // ---------------------------------------------------------------- selection

    private void select(boolean all, boolean recommendedOnly) {
        for (CertSource source : sources) {
            if (all) {
                source.selected = true;
            } else if (recommendedOnly) {
                source.selected = source.recommended;
            } else {
                source.selected = false;
            }
        }
        adapter.notifyDataSetChanged();
    }

    private List<CertSource> selectedSources() {
        List<CertSource> list = new ArrayList<CertSource>();
        for (CertSource source : sources) {
            if (source.selected) {
                list.add(source);
            }
        }
        return list;
    }

    // ---------------------------------------------------------------- download

    private void startDownload() {
        if (busy) {
            return;
        }
        final List<CertSource> selected = selectedSources();
        if (selected.isEmpty()) {
            toast(R.string.toast_nothing_selected);
            return;
        }
        setBusy(true);
        synchronized (downloaded) {
            downloaded.clear();
        }
        for (CertSource source : sources) {
            source.reset();
        }
        adapter.notifyDataSetChanged();

        new Thread(new Runnable() {
            public void run() {
                final int total = selected.size();
                for (int i = 0; i < total; i++) {
                    final CertSource source = selected.get(i);
                    source.state = CertSource.STATE_DOWNLOADING;
                    postProgress(i, total);

                    try {
                        byte[] data = Downloader.download(source.url, source.httpUrl);
                        List<X509Certificate> certs = verify(source, CertParser.parse(data));
                        if (certs.isEmpty()) {
                            throw new Exception("no usable root certificates found");
                        }
                        synchronized (downloaded) {
                            downloaded.addAll(certs);
                        }
                        source.certCount = certs.size();
                        source.state = CertSource.STATE_DONE;
                    } catch (Exception e) {
                        source.state = CertSource.STATE_ERROR;
                        source.message = String.valueOf(e.getMessage());
                    }
                    postProgress(i + 1, total);
                }

                ui.post(new Runnable() {
                    public void run() {
                        setBusy(false);
                        int count;
                        synchronized (downloaded) {
                            count = downloaded.size();
                        }
                        statusView.setText(getString(R.string.status_download_complete, count));
                        toast(getString(R.string.toast_downloaded, count));
                    }
                });
            }
        }).start();
    }

    private void postProgress(final int done, final int total) {
        ui.post(new Runnable() {
            public void run() {
                adapter.notifyDataSetChanged();
                progress.setProgress(total == 0 ? 0 : (int) (100.0 * done / total));
            }
        });
    }

    /**
     * Keeps only certificates that are self-signed (a root) and that match what the
     * source expects: the pinned fingerprint for a single-certificate source, or one of
     * the pinned roots for a bundle. This is what protects against a tampered download,
     * which matters most for the attempts that run over plain HTTP.
     */
    private List<X509Certificate> verify(CertSource source, List<X509Certificate> parsed) {
        List<X509Certificate> good = new ArrayList<X509Certificate>();
        for (X509Certificate cert : parsed) {
            if (!isSelfSigned(cert)) {
                continue;
            }
            final String sha256 = HashUtils.sha256Hex(cert);
            if (source.expectedSha256 != null) {
                if (!source.expectedSha256.equalsIgnoreCase(sha256)) {
                    continue;
                }
            } else if (source.knownSha256 != null && !source.knownSha256.contains(sha256)) {
                continue;
            }
            good.add(cert);
        }
        return good;
    }

    private boolean isSelfSigned(X509Certificate cert) {
        try {
            if (!cert.getSubjectX500Principal().equals(cert.getIssuerX500Principal())) {
                return false;
            }
            cert.verify(cert.getPublicKey());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------- SYSTEM store install

    private void startSystemInstall() {
        if (busy) {
            return;
        }
        final List<X509Certificate> certs = snapshot();
        if (certs.isEmpty()) {
            toast(R.string.toast_need_download_first);
            return;
        }
        if (!rootAvailable) {
            toast(R.string.toast_root_missing);
            return;
        }
        setBusy(true);
        progress.setProgress(0);
        statusView.setText(getString(R.string.status_installing_system, certs.size()));

        new Thread(new Runnable() {
            public void run() {
                final CertInstaller.InstallReport report =
                        CertInstaller.installToSystemStore(MainActivity.this, certs);
                ui.post(new Runnable() {
                    public void run() {
                        setBusy(false);
                        progress.setProgress(100);
                        if (report.rootOk) {
                            statusView.setText(getString(R.string.status_system_done, report.copied));
                            rebootButton.setVisibility(View.VISIBLE);
                        } else {
                            statusView.setText(R.string.status_system_failed);
                        }
                        showLogDialog(getString(R.string.dialog_system_output), report.log);
                    }
                });
            }
        }).start();
    }

    // --------------------------------------------------------- USER store install

    /**
     * Asks how the confirmations of the installer should go, then installs.
     *
     * <p>The certificates always go through the Android certificate installer; the
     * three ways of doing that differ only in how many prompts it asks for and who
     * answers them, which is what the dialog names. It is asked on every run, with
     * the last answer already selected, because what a device needs depends on the
     * run rather than on a setting.</p>
     */
    private void startUserInstall() {
        if (busy) {
            return;
        }
        if (snapshot().isEmpty()) {
            toast(R.string.toast_need_download_first);
            return;
        }
        showUserInstallChooser();
    }

    /** Shows the three ways a USER store install can go, and runs the chosen one. */
    private void showUserInstallChooser() {
        final int chosen = UserInstallMode.get(this);
        View content = getLayoutInflater().inflate(R.layout.dialog_user_install, null);
        final RadioGroup group = (RadioGroup) content.findViewById(R.id.install_mode_group);
        final TextView note = (TextView) content.findViewById(R.id.install_mode_note);

        // The caption under the buttons names the selected mode, so it follows the
        // selection rather than the choice the dialog was opened with.
        group.check(INSTALL_MODE_OPTIONS[chosen]);
        note.setText(UserInstallMode.noteRes(chosen));
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup changed, int checkedId) {
                note.setText(UserInstallMode.noteRes(modeForCheckedId(checkedId)));
            }
        });

        AlertDialog chooser = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_user_install_title)
                .setView(content)
                .setPositiveButton(R.string.dialog_user_install_ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                installUserStore(
                                        modeForCheckedId(group.getCheckedRadioButtonId()));
                            }
                        })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .show();
        AppColors.applyToDialog(chooser, AppThemes.get(this));
    }

    /** Maps a checked radio button of the chooser to its mode constant. */
    private int modeForCheckedId(int checkedId) {
        for (int i = 0; i < INSTALL_MODE_OPTIONS.length; i++) {
            if (INSTALL_MODE_OPTIONS[i] == checkedId) {
                return UserInstallMode.ALL[i];
            }
        }
        return UserInstallMode.BUNDLE;
    }

    /**
     * Starts the install in the chosen mode, once the service that mode needs is on.
     *
     * <p>The automatic mode is the only one that needs anything from the user up
     * front, and it is also the only one that can be held back: without the service
     * switched on there would be nothing automatic about it, so the service is
     * explained and the page that switches it on is offered instead of a run that
     * would sit there waiting for taps. The mode is remembered for the way back (see
     * {@link #onResume}).</p>
     */
    private void installUserStore(int mode) {
        if (busy) {
            return;
        }
        UserInstallMode.set(this, mode);
        if (mode == UserInstallMode.AUTO && !InstallerAutoConfirm.isEnabled()) {
            showAutoConfirmIntro(PENDING_AUTO_INSTALL);
            return;
        }
        startUserStoreInstall(mode);
    }

    /**
     * Runs a USER store install in the given mode.
     *
     * <p>{@link UserInstallMode#SINGLE} goes straight to one certificate per dialog;
     * the other two offer the whole set to the installer in one intent first and fall
     * back to one certificate per dialog when it does not take that bundle. The
     * auto-confirm service is only told about the run in {@link UserInstallMode#AUTO},
     * so a mode whose label says the prompts are answered by hand really does leave
     * them alone.</p>
     *
     * <p>Certificates the SYSTEM store already holds are taken out of the run before
     * anything is started: the installer reports success for them without writing
     * anything, so asking for them anyway would cost confirmations and still leave a
     * user store that does not show them (see {@link TrustedRoots#systemTrusted}).
     */
    private void startUserStoreInstall(int mode) {
        userInstallMode = mode;
        List<X509Certificate> certs = snapshot();
        if (certs.isEmpty()) {
            toast(R.string.toast_need_download_first);
            return;
        }
        // Only what the device does not trust yet, so that a second run - or one that
        // follows a partial install - has nothing left to confirm.
        bulkPending.clear();
        bulkPending.addAll(TrustedRoots.missing(certs));

        // ... minus the certificates that are trusted as system roots. Android keeps
        // one copy of a certificate: asked to put one of those into the user store, the
        // installer reports success and writes nothing at all, so a run made of nothing
        // else would end with an empty user store and no error to explain it. They are
        // counted out here and named to the user below, because the one thing that no
        // prompt can change is where Android decided to keep them.
        systemSkipped.clear();
        systemSkipped.addAll(TrustedRoots.systemTrusted(certs));
        dropFingerprints(bulkPending, systemSkipped);

        userQueue.clear();
        userQueueIndex = 0;
        progress.setProgress(0);
        if (bulkPending.isEmpty()) {
            if (systemSkipped.isEmpty()) {
                statusView.setText(R.string.status_user_done);
            } else {
                statusView.setText(getString(R.string.status_user_nothing_system_trusted,
                        systemSkipped.size()));
                showSystemTrustedNotice();
            }
            return;
        }
        // Try the whole set in one intent first. The certificate installer accepts a
        // credentials bundle holding any number of CA certificates and adds them in a
        // single pass, which turns a bundle of a hundred roots into a single dialog.
        Intent bulk = null;
        if (mode != UserInstallMode.SINGLE) {
            try {
                bulk = CertInstaller.userStoreBulkInstallIntent(bulkPending);
            } catch (Exception e) {
                bulk = null;
            }
        }
        if (bulk != null) {
            if (mode == UserInstallMode.AUTO) {
                InstallerAutoConfirm.begin();
            }
            try {
                statusView.setText(installStatus(bulkPending.size()));
                startActivityForResult(bulk, REQ_INSTALL_BULK);
                return;
            } catch (Exception e) {
                // No certificate installer here: fall through to the one-by-one path.
            }
        }
        queueRemainingUserCerts();
    }

    /**
     * Installs whatever of the current set the device still does not trust, one
     * certificate at a time.
     *
     * <p>This runs after a bulk install as well as when there was none: an installer
     * that does not understand the bundle ignores it and returns normally, so the only
     * trustworthy answer to "what did it add?" is to ask the store.
     */
    private void queueRemainingUserCerts() {
        List<X509Certificate> missing = TrustedRoots.missing(bulkPending);
        userQueue.clear();
        userQueue.addAll(missing);
        userQueueIndex = 0;
        if (userQueue.isEmpty()) {
            finishUserInstall();
            return;
        }
        progress.setProgress(0);
        statusView.setText(installStatus(userQueue.size()));
        if (userInstallMode == UserInstallMode.AUTO) {
            InstallerAutoConfirm.begin();
        }
        installNextUserCert();
    }

    /** The status line of a USER store install: who is expected to answer the prompts. */
    private String installStatus(int count) {
        return getString(userInstallMode == UserInstallMode.AUTO
                ? R.string.status_installing_user_auto
                : R.string.status_installing_user, count);
    }

    private void installNextUserCert() {
        if (userQueueIndex >= userQueue.size()) {
            finishUserInstall();
            return;
        }
        X509Certificate cert = userQueue.get(userQueueIndex);
        try {
            Intent intent = CertInstaller.userStoreInstallIntent(cert);
            startActivityForResult(intent, REQ_INSTALL_USER);
        } catch (Exception e) {
            userQueueIndex++;
            installNextUserCert();
        }
    }

    /**
     * Ends a USER store install, however it went.
     *
     * <p>A run that carried certificates the installer will not copy says how many of
     * them there were: that count is the difference between what the user selected and
     * what the rest of the run reports, and it is the one part of it that no prompt
     * could have changed (see {@link #showSystemTrustedNotice}).
     */
    private void finishUserInstall() {
        InstallerAutoConfirm.end();
        progress.setProgress(100);
        statusView.setText(systemSkipped.isEmpty()
                ? getString(R.string.status_user_done)
                : getString(R.string.status_user_done_system_trusted, systemSkipped.size()));
    }

    /**
     * Names the certificates that the SYSTEM store already holds, and says why the
     * installer will not add them to the user store.
     *
     * <p>This is the answer to the run that looks like it worked and leaves the user
     * store empty: Android keeps a certificate in the store it was found in, so a
     * system root is not duplicated into the user store, and the certificate installer
     * reports success either way. Without this the only sign of it is a certificate
     * that never shows up in the user list of the settings, and an install that appears
     * not to have persisted.
     */
    private void showSystemTrustedNotice() {
        StringBuilder names = new StringBuilder();
        for (X509Certificate cert : systemSkipped) {
            if (names.length() > 0) {
                names.append('\n');
            }
            names.append("- ").append(CertInstaller.commonName(cert));
        }
        AlertDialog notice = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_system_trusted_title)
                .setMessage(getString(R.string.dialog_system_trusted_message,
                        trim(names.toString())))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .show();
        AppColors.applyToDialog(notice, AppThemes.get(this));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_INSTALL_BULK) {
            // Whatever the installer did with the bundle, the rest is done one by one.
            queueRemainingUserCerts();
            return;
        }
        if (requestCode == REQ_INSTALL_USER) {
            userQueueIndex++;
            progress.setProgress(userQueue.isEmpty()
                    ? 0 : (int) (100.0 * userQueueIndex / userQueue.size()));
            installNextUserCert();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Being back in front ends a removal the service was driving through the
        // certificate list: from here on that list is the user's again.
        if (RemovalAutoConfirm.isRunning()) {
            RemovalAutoConfirm.end();
            statusView.setText(R.string.status_auto_remove_done);
        }
        // The auto-confirm switch lives in the system settings, so the check mark
        // beside the menu entry has to be read again after coming back from there.
        invalidateOptionsMenu();
        if (pendingAutoConfirm != PENDING_NONE) {
            // A flow sent the user to the accessibility settings to switch the service
            // on; whatever it was doing is picked up again on the way back.
            ui.postDelayed(new Runnable() {
                public void run() {
                    continuePendingAutoConfirm();
                }
            }, SERVICE_CONNECT_DELAY_MS);
        }
    }

    /**
     * Picks up what was waiting for the auto-confirm service, now that the user is
     * back from the accessibility settings.
     *
     * <p>Only a service that really is switched on starts anything; a device that came
     * back with the switch still off is told so rather than left wondering why nothing
     * happened. A removal is asked for once more instead of simply running: it is
     * destructive, and the question the button always asks has not been answered
     * yet.</p>
     */
    private void continuePendingAutoConfirm() {
        int pending = pendingAutoConfirm;
        pendingAutoConfirm = PENDING_NONE;
        if (isFinishing()) {
            return;
        }
        if (!InstallerAutoConfirm.isEnabled()) {
            toast(R.string.toast_auto_confirm_still_off);
            return;
        }
        if (pending == PENDING_AUTO_INSTALL) {
            startUserStoreInstall(UserInstallMode.AUTO);
        } else if (pending == PENDING_AUTO_REMOVAL) {
            confirmAutomaticUserRemoval();
        }
    }

    // --------------------------------------------------------- USER store removal

    /**
     * Asks which of the three ways out of the user store to take, then runs it.
     *
     * <p>The user store has no API for removing a certificate, so the ways differ in
     * who does the deleting and in what that needs: root, a person working the
     * certificate list of the settings, or the auto-confirm service working that same
     * list. The dialog names all three, and it greys out root on a device that has
     * none - which is the one thing that cannot be talked around.</p>
     */
    private void startUserRemoval() {
        if (busy) {
            return;
        }
        if (snapshot().isEmpty()) {
            toast(R.string.toast_need_download_first);
            return;
        }
        showUserRemoveChooser();
    }

    /** Shows the three ways a USER store removal can go, and runs the chosen one. */
    private void showUserRemoveChooser() {
        final int chosen = UserRemoveMode.get(this, rootAvailable);
        View content = getLayoutInflater().inflate(R.layout.dialog_user_remove, null);
        final RadioGroup group = (RadioGroup) content.findViewById(R.id.remove_mode_group);
        final TextView note = (TextView) content.findViewById(R.id.remove_mode_note);
        RadioButton root = (RadioButton) content.findViewById(R.id.remove_mode_root);

        // A device without root cannot take the first way at all, so it is offered
        // greyed out and labelled as the one this device cannot do. The choice the
        // dialog opens on never lands there either (see UserRemoveMode#get).
        if (!rootAvailable) {
            root.setEnabled(false);
            root.setText(R.string.remove_mode_root_noroot);
        }

        // The caption under the buttons names the selected way, so it follows the
        // selection rather than the choice the dialog was opened with.
        group.check(REMOVE_MODE_OPTIONS[chosen]);
        note.setText(UserRemoveMode.noteRes(chosen));
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup changed, int checkedId) {
                note.setText(UserRemoveMode.noteRes(removeModeForCheckedId(checkedId)));
            }
        });

        AlertDialog chooser = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_user_remove_title)
                .setView(content)
                .setPositiveButton(R.string.dialog_user_remove_ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                removeUserStore(
                                        removeModeForCheckedId(group.getCheckedRadioButtonId()));
                            }
                        })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .show();
        AppColors.applyToDialog(chooser, AppThemes.get(this));
    }

    /** Maps a checked radio button of the removal chooser to its mode constant. */
    private int removeModeForCheckedId(int checkedId) {
        for (int i = 0; i < REMOVE_MODE_OPTIONS.length; i++) {
            if (REMOVE_MODE_OPTIONS[i] == checkedId) {
                return UserRemoveMode.ALL[i];
            }
        }
        return UserRemoveMode.ROOT;
    }

    /**
     * Runs the chosen way out of the user store.
     *
     * <p>Only the first way deletes anything here and now, and only it needs root; the
     * other two leave the deleting to the user or to the service, so they go through
     * the same doors the two buttons that used to stand here opened: the walkthrough of
     * the settings list, and the automatic removal, which asks its own confirmation and
     * explains the service when it is switched off. The choice is remembered for the
     * next run.</p>
     */
    private void removeUserStore(int mode) {
        if (busy) {
            return;
        }
        UserRemoveMode.set(this, mode);
        if (mode == UserRemoveMode.MANUAL) {
            showManualRemovalGuide();
        } else if (mode == UserRemoveMode.AUTO) {
            confirmAutomaticUserRemoval();
        } else {
            confirmRemoval(false);
        }
    }

    // ------------------------------------------------------------------ uninstall

    /**
     * Asks before anything is deleted, then removes the current selection from the
     * store it is told to use.
     *
     * <p>Removing is destructive in a way installing is not, so it is always
     * confirmed. Both stores are read by root - the user store has no API for
     * removing a certificate - so a device without root is told to do it by hand: the
     * SYSTEM store has no rootless way out at all, and the user store is left to the
     * walkthrough. The removal chooser greys the root way out on such a device, so
     * that second fallback is only reached when root is lost between the two.</p>
     */
    private void confirmRemoval(final boolean system) {
        if (busy) {
            return;
        }
        final List<X509Certificate> certs = snapshot();
        if (certs.isEmpty()) {
            toast(R.string.toast_need_download_first);
            return;
        }
        if (!rootAvailable) {
            if (system) {
                // The SYSTEM store has no rootless way out at all.
                toast(R.string.toast_remove_needs_root);
            } else {
                // The user store has one, and this is where it is explained.
                showManualRemovalGuide();
            }
            return;
        }
        AlertDialog confirm = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_remove_title)
                .setMessage(system ? R.string.dialog_remove_system_message
                        : R.string.dialog_remove_user_message)
                .setPositiveButton(R.string.dialog_remove_ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        startRemoval(system, certs);
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .show();
        AppColors.applyToDialog(confirm, AppThemes.get(this));
    }

    private void startRemoval(final boolean system, final List<X509Certificate> certs) {
        setBusy(true);
        progress.setProgress(0);
        statusView.setText(getString(system ? R.string.status_removing_system
                : R.string.status_removing_user, certs.size()));

        new Thread(new Runnable() {
            public void run() {
                final CertInstaller.InstallReport report = system
                        ? CertInstaller.removeFromSystemStore(MainActivity.this, certs)
                        : CertInstaller.removeFromUserStore(MainActivity.this, certs);
                ui.post(new Runnable() {
                    public void run() {
                        setBusy(false);
                        progress.setProgress(100);
                        if (!report.rootOk) {
                            statusView.setText(R.string.status_remove_failed);
                        } else if (report.removed == 0 && report.deduped == 0) {
                            statusView.setText(R.string.status_remove_nothing);
                        } else if (system) {
                            if (report.removed > 0 && report.deduped > 0) {
                                statusView.setText(getString(
                                        R.string.status_remove_system_done_deduped,
                                        report.removed, report.deduped));
                            } else if (report.deduped > 0) {
                                statusView.setText(getString(
                                        R.string.status_remove_system_deduped_only,
                                        report.deduped));
                            } else {
                                statusView.setText(getString(R.string.status_remove_system_done,
                                        report.removed));
                            }
                        } else {
                            statusView.setText(getString(R.string.status_remove_user_done,
                                    report.removed));
                        }
                        showLogDialog(getString(system ? R.string.dialog_remove_system_output
                                : R.string.dialog_remove_user_output), report.log);
                    }
                });
            }
        }).start();
    }

    /**
     * The rootless way out of the user store, and the one the removal chooser offers
     * under the name of removing by hand: a certificate cannot be written without root,
     * so it only goes away in the system settings. The dialog spells the steps out and
     * offers to open that screen, and it needs no root itself.
     */
    private void showManualRemovalGuide() {
        AlertDialog guide = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_manual_title)
                .setMessage(R.string.dialog_manual_message)
                .setPositiveButton(R.string.dialog_manual_open, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        openUserCredentialsSettings();
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .show();
        AppColors.applyToDialog(guide, AppThemes.get(this));
    }

    /**
     * Opens the screen that lists the user certificates. Which activity that is
     * differs between releases, so the candidates are tried from the most specific
     * to the most general; the last one is the security page, which at least puts
     * the list one tap away.
     */
    private void openUserCredentialsSettings() {
        Intent[] candidates = new Intent[]{
                new Intent(ACTION_TRUSTED_CREDENTIALS_USER),
                new Intent(ACTION_TRUSTED_CREDENTIALS),
                new Intent().setClassName("com.android.settings",
                        "com.android.settings.Settings$TrustedCredentialsSettingsActivity"),
                new Intent(Settings.ACTION_SECURITY_SETTINGS),
        };
        for (Intent candidate : candidates) {
            try {
                startActivity(candidate);
                return;
            } catch (Exception e) {
                // Not this one: try the next, more general candidate.
            }
        }
        toast(R.string.toast_no_settings_screen);
    }

    /**
     * Removes the loaded certificates from the user store without root: the app opens
     * the certificate list and the auto-confirm service taps the rows and confirms
     * what follows.
     *
     * <p>It is asked for first, like every removal, and it needs the service to be
     * switched on, because the app cannot switch on its own accessibility service.
     */
    private void confirmAutomaticUserRemoval() {
        if (busy) {
            return;
        }
        final List<X509Certificate> certs = snapshot();
        if (certs.isEmpty()) {
            toast(R.string.toast_need_download_first);
            return;
        }
        if (!InstallerAutoConfirm.isEnabled()) {
            // Not a bare pointer at the settings any more: that page says nothing
            // about the service, so the service is explained first - and the removal
            // is asked for again once the user is back (see #onResume).
            showAutoConfirmIntro(PENDING_AUTO_REMOVAL);
            return;
        }
        AlertDialog confirm = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_auto_remove_title)
                .setMessage(R.string.dialog_auto_remove_message)
                .setPositiveButton(R.string.dialog_auto_remove_ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                startAutomaticUserRemoval(certs);
                            }
                        })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .show();
        AppColors.applyToDialog(confirm, AppThemes.get(this));
    }

    /**
     * Hands the names of the loaded certificates to the service and opens the list.
     *
     * <p>What the service matches against is the common name of a certificate, because
     * that is what the settings screen labels a row with. The removal stays active
     * until this screen is in front again (see {@link #onResume}), so the list is only
     * ever edited while this app is driving it.
     */
    private void startAutomaticUserRemoval(List<X509Certificate> certs) {
        List<String> names = new ArrayList<String>();
        for (X509Certificate cert : certs) {
            String name = CertInstaller.commonName(cert);
            if (name.length() > 0 && !names.contains(name)) {
                names.add(name);
            }
        }
        if (names.isEmpty()) {
            toast(R.string.toast_need_download_first);
            return;
        }
        RemovalAutoConfirm.begin(names);
        statusView.setText(getString(R.string.status_auto_remove, names.size()));
        openUserCredentialsSettings();
    }

    // -------------------------------------------------------------------- reboot

    private void rebootDevice() {
        toast(R.string.toast_rebooting);
        new Thread(new Runnable() {
            public void run() {
                RootUtils.runRoot("reboot");
            }
        }).start();
    }

    // ---------------------------------------------------------------------- menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem themeItem = menu.add(0, MENU_THEME, 0, R.string.menu_theme);
        // Show the theme chooser as a text button in the top bar (no icon drawables).
        themeItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS
                | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        menu.add(0, MENU_LANGUAGE, 1, R.string.menu_language);
        menu.add(0, MENU_WEBSITE, 2, R.string.menu_website);
        menu.add(0, MENU_GITHUB, 3, R.string.menu_github);
        menu.add(0, MENU_ABOUT, 4, R.string.menu_about);
        // The check mark shows whether the auto-confirm service is on. An app cannot
        // switch on its own accessibility service, so the entry only opens the
        // settings page where the user does it. It is offered from Android 4.1 up,
        // because the accessibility framework of 4.0 cannot click a button at all.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            MenuItem autoConfirm = menu.add(0, MENU_AUTO_CONFIRM, 5, R.string.menu_auto_confirm);
            autoConfirm.setCheckable(true);
            autoConfirm.setChecked(InstallerAutoConfirm.isEnabled());
        }
        if (LaoLuo.isSmartisan()) {
            menu.add(0, MENU_REVOLUTION, 6, R.string.menu_revolution);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_THEME:
                showThemeChooser();
                return true;
            case MENU_LANGUAGE:
                showLanguageChooser();
                return true;
            case MENU_WEBSITE:
                openUrl(URL_WEBSITE);
                return true;
            case MENU_GITHUB:
                openUrl(URL_GITHUB);
                return true;
            case MENU_ABOUT:
                showAbout();
                return true;
            case MENU_AUTO_CONFIRM:
                showAutoConfirmMenu();
                return true;
            case MENU_REVOLUTION:
                openUrl(URL_REVOLUTION);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /** Shows the About dialog with links to the project website and GitHub repo. */
    private void showAbout() {
        View content = getLayoutInflater().inflate(R.layout.dialog_about, null);
        TextView links = (TextView) content.findViewById(R.id.about_links);
        links.setText(getString(R.string.about_website_label, URL_WEBSITE) + "\n"
                + getString(R.string.about_github_label, URL_GITHUB));
        // The build the app is running, so that a bug report can name it: version
        // name and code, the package id, and when and how it was built.
        TextView technical = (TextView) content.findViewById(R.id.about_technical);
        technical.setText(getString(R.string.about_technical,
                BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, getPackageName(),
                BuildConfig.BUILD_DATE, BuildConfig.BUILD_TYPE));
        AlertDialog aboutDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.about_title)
                .setView(content)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        AppColors.applyToDialog(aboutDialog, AppThemes.get(this));
    }

    /**
     * Opens the system accessibility settings, the only place the auto-confirm
     * service can be switched on: an app is not allowed to enable its own
     * accessibility service, and rightly so.
     */
    /**
     * The menu entry of the auto-confirm service: with the service off it explains
     * what the service is and offers the page that switches it on; with the service on
     * it says so and offers the same page to switch it off again.
     *
     * <p>The check mark beside the entry is all the menu itself says about the service,
     * which is not much to go on - hence a dialog on both sides of the switch.</p>
     */
    private void showAutoConfirmMenu() {
        if (!InstallerAutoConfirm.isEnabled()) {
            showAutoConfirmIntro(PENDING_NONE);
            return;
        }
        AlertDialog on = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_auto_confirm_title)
                .setMessage(R.string.dialog_auto_confirm_on_message)
                .setPositiveButton(R.string.dialog_auto_confirm_open,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                openAutoConfirmSettings();
                            }
                        })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .show();
        AppColors.applyToDialog(on, AppThemes.get(this));
    }

    /**
     * Explains the auto-confirm service before the settings page that switches it on.
     *
     * <p>The app may not switch on its own accessibility service, so all it can do is
     * send the user to the page where that happens - and that page says nothing about
     * what the service is for, or which entry of a long list of services belongs to
     * this app. This dialog says both. It is also where a flow that needs the service
     * leaves what it was doing: {@code pending} is picked up again when the settings
     * page is closed (see {@link #onResume}), so that flow does not have to be started
     * over by hand.</p>
     *
     * <p>What the service looks like while it works is worth saying before it is
     * switched on: it taps through screens in quick succession, so the display can
     * flash repeatedly, which is not something a reader with photosensitive epilepsy
     * should have to find out about in the middle of an install. The dialog therefore
     * ends with that warning, and names the two ways that confirm by hand as the ones
     * to take instead - neither of them needs a service.</p>
     *
     * @param pending {@link #PENDING_AUTO_INSTALL} or {@link #PENDING_AUTO_REMOVAL} for
     *                a flow that should continue afterwards, {@link #PENDING_NONE} for
     *                the menu entry, which continues nothing
     */
    private void showAutoConfirmIntro(final int pending) {
        AlertDialog intro = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_auto_confirm_title)
                .setMessage(getString(R.string.dialog_auto_confirm_message,
                        getString(R.string.accessibility_service_label))
                        + "\n\n" + getString(R.string.dialog_auto_confirm_flash_warning))
                .setPositiveButton(R.string.dialog_auto_confirm_open,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                pendingAutoConfirm = pending;
                                openAutoConfirmSettings();
                            }
                        })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .show();
        AppColors.applyToDialog(intro, AppThemes.get(this));
    }

    private void openAutoConfirmSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            // No page to come back from, so nothing can be waiting for one either.
            pendingAutoConfirm = PENDING_NONE;
            toast(R.string.toast_no_accessibility_settings);
        }
    }

    /** Opens a URL in the user's browser. */
    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast(getString(R.string.toast_no_browser, url));
        }
    }

    /** Shows the language picker and applies the selection. */
    private void showLanguageChooser() {
        final int[] options = AppLanguage.ALL;
        CharSequence[] labels = new CharSequence[options.length];
        int current = AppLanguage.get(this);
        int checked = 0;
        for (int i = 0; i < options.length; i++) {
            labels[i] = getString(AppLanguage.labelRes(options[i]));
            if (options[i] == current) {
                checked = i;
            }
        }

        AlertDialog picker = new AlertDialog.Builder(this)
                .setTitle(R.string.language_title)
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        int chosen = options[which];
                        if (chosen != AppLanguage.get(MainActivity.this)) {
                            AppLanguage.set(MainActivity.this, chosen);
                            restartActivity();
                        }
                    }
                })
                .show();
        AppColors.applyToDialog(picker, AppThemes.get(this));
    }

    /** Shows the theme picker and applies the selection. */
    private void showThemeChooser() {
        View content = getLayoutInflater().inflate(R.layout.dialog_theme, null);

        final RadioGroup themeGroup = (RadioGroup) content.findViewById(R.id.theme_group);
        final RadioGroup schemeGroup = (RadioGroup) content.findViewById(R.id.theme_scheme_group);
        final RadioGroup holoColors = (RadioGroup) content.findViewById(R.id.theme_holo_colors);
        final RadioGroup materialPrimary =
                (RadioGroup) content.findViewById(R.id.theme_material_primary);
        final RadioGroup materialAccent =
                (RadioGroup) content.findViewById(R.id.theme_material_accent);

        final View holoSection = content.findViewById(R.id.theme_colors_holo);
        final View materialSection = content.findViewById(R.id.theme_colors_material);
        final View deviceNote = content.findViewById(R.id.theme_device_default_note);

        // Material only exists from Android 5.0 onward. On older versions show
        // the option grayed out with a note underneath explaining why, instead of
        // offering a "Material" theme that would really just be Holo.
        if (!AppThemes.isAvailable(AppThemes.MATERIAL)) {
            content.findViewById(R.id.theme_option_material).setEnabled(false);
            content.findViewById(R.id.theme_material_note).setVisibility(View.VISIBLE);
        }

        int theme = AppThemes.get(this);
        int scheme = AppThemes.getScheme(this);
        themeGroup.check(THEME_OPTIONS[theme]);
        schemeGroup.check(SCHEME_OPTIONS[scheme]);

        // The swatch rows: a "keep the theme's own color" swatch first, then the
        // palette. The Holo theme has a single accent; Material has a primary and
        // an accent color. The Material accent row leaves teal out: that is the
        // color Material itself accents with, so the "default" swatch already
        // stands for it and a teal swatch next to it would be the same color twice.
        fillSwatches(holoColors, ID_HOLO_SWATCH, AppColors.HOLO_ACCENTS, null,
                AppColors.HOLO_ACCENT_NAMES, AppColors.getHoloAccent(this),
                AppColors.defaultAccent(this, AppThemes.HOLO),
                (TextView) content.findViewById(R.id.theme_holo_caption));

        fillSwatches(materialPrimary, ID_PRIMARY_SWATCH, AppColors.MATERIAL_COLORS,
                AppColors.MATERIAL_COLORS_DARK, AppColors.MATERIAL_COLOR_NAMES,
                AppColors.getMaterialPrimary(this),
                stockColor(AppThemes.MATERIAL, scheme, android.R.attr.colorPrimary,
                        R.color.mat_blue),
                (TextView) content.findViewById(R.id.theme_material_primary_caption));

        fillSwatches(materialAccent, ID_ACCENT_SWATCH, AppColors.MATERIAL_ACCENT_COLORS,
                AppColors.MATERIAL_ACCENT_COLORS_DARK, AppColors.MATERIAL_ACCENT_COLOR_NAMES,
                AppColors.getMaterialAccent(this),
                AppColors.defaultAccent(this, AppThemes.MATERIAL),
                (TextView) content.findViewById(R.id.theme_material_accent_caption));

        showColorSections(theme, holoSection, materialSection, deviceNote);
        themeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                showColorSections(themeForCheckedId(checkedId),
                        holoSection, materialSection, deviceNote);
            }
        });

        AlertDialog picker = new AlertDialog.Builder(this)
                .setTitle(R.string.theme_title)
                .setView(content)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        applyThemeChoice(themeGroup, schemeGroup, holoColors,
                                materialPrimary, materialAccent);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        AppColors.applyToDialog(picker, theme);
    }

    /**
     * Fills a RadioGroup with one swatch per palette entry, preceded by a swatch
     * that keeps the theme's own color, and checks the current choice.
     *
     * <p>Each swatch is a RadioButton without its radio graphic - the color itself
     * is the control. They are laid out in rows of {@link #SWATCHES_PER_ROW},
     * because a RadioGroup puts all of its children on a single line and eleven
     * swatches would not fit. The rows are plain LinearLayouts, which a RadioGroup
     * does not track, so the selection is driven explicitly through
     * {@link RadioGroup#check(int)}.</p>
     *
     * @param darkPalette the same palette in the dark-scheme colors, or null for a
     *                    palette that has only one form
     * @param idBase id of the first swatch; swatches are numbered from there
     */
    private void fillSwatches(final RadioGroup group, final int idBase, int[] palette,
                              int[] darkPalette, final int[] names, int selected,
                              int defaultColor, final TextView caption) {
        group.removeAllViews();

        // The dark color scheme shows the same palette in its dark colors; the
        // caption and the stored choice follow whatever is on screen.
        int[] shown = darkPalette != null && AppThemes.getScheme(this) == AppThemes.SCHEME_DARK
                ? darkPalette : palette;

        // The gap under a row separates it from the row below, so the last one goes
        // without it: the caption that names the choice follows directly underneath,
        // and an extra margin there reads as a separate component rather than as part
        // of the picker.
        int lastRow = palette.length / SWATCHES_PER_ROW;

        LinearLayout row = null;
        for (int i = 0; i <= palette.length; i++) {
            if (i % SWATCHES_PER_ROW == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                group.addView(row);
            }

            int color = (i == 0) ? defaultColor : AppColors.resolve(this, shown[i - 1]);
            // The checkmark is white on the light palettes and black on the
            // dark-scheme Material ones, whose 200 tints are far too pale to carry a
            // white checkmark. The "default" swatch is whatever color the theme
            // itself uses, so its checkmark follows that color instead.
            int checkmark = i == 0 ? contrastingColor(color)
                    : (shown == darkPalette ? Color.BLACK : Color.WHITE);

            final RadioButton swatch = new RadioButton(this);
            swatch.setId(idBase + i);
            // The radio graphic has to be replaced rather than cleared: before API 17
            // setButtonDrawable(null) does nothing at all, which left the blue circle
            // the framework draws on top of every tile. A transparent drawable is
            // drawn as nothing on every version.
            swatch.setButtonDrawable(new ColorDrawable(Color.TRANSPARENT));
            swatch.setBackgroundDrawable(swatchDrawable(color, checkmark));
            swatch.setMinimumWidth(0);
            swatch.setMinimumHeight(0);

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(dp(SWATCH_DP), dp(SWATCH_DP));
            params.rightMargin = dp(SWATCH_GAP_DP);
            params.bottomMargin = i / SWATCHES_PER_ROW < lastRow ? dp(SWATCH_GAP_DP) : 0;
            row.addView(swatch, params);

            final RadioGroup swatchGroup = group;
            swatch.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    swatchGroup.check(swatch.getId());
                    setColorCaption(caption, names, swatch.getId() - idBase);
                }
            });
        }

        // The stored color is matched against both forms of the palette, so a choice
        // made in one color scheme is found again in the other.
        int checked = AppColors.indexOfColor(this, palette, darkPalette, selected) + 1;
        group.check(idBase + checked);
        setColorCaption(caption, names, checked);
    }

    /** Names the color of swatch {@code index} below its row. */
    private void setColorCaption(TextView caption, int[] names, int index) {
        caption.setText(index <= 0 || index > names.length
                ? R.string.color_default
                : names[index - 1]);
    }

    /**
     * A square of the given color, carrying a checkmark while it is the selected
     * swatch. The checkmark wears the color it is handed: the light palettes are
     * saturated enough to carry white, the dark-scheme Material ones are pale enough
     * to need black (see {@link #fillSwatches}). An outline was tried first, but it
     * disappears against a light dialog.
     */
    private Drawable swatchDrawable(int color, int checkmarkColor) {
        GradientDrawable fill = new GradientDrawable();
        fill.setShape(GradientDrawable.RECTANGLE);
        fill.setColor(color);
        fill.setCornerRadius(dp(4));

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_checked},
                new LayerDrawable(new Drawable[]{fill, checkMark(checkmarkColor)}));
        states.addState(new int[]{}, fill);
        return states;
    }

    /**
     * Black or white, whichever reads better on top of the given color. Used for the
     * "default" swatch, which is whatever color the theme itself happens to use.
     */
    private int contrastingColor(int color) {
        int luminance = (int) (0.299 * Color.red(color)
                + 0.587 * Color.green(color)
                + 0.114 * Color.blue(color));
        return luminance > 149 ? Color.BLACK : Color.WHITE;
    }

    /**
     * A checkmark centered on a swatch, drawn as a stroked path (see
     * {@link AppColors#checkmark}).
     */
    private Drawable checkMark(int color) {
        return AppColors.checkmark(color, dp(SWATCH_DP), dp(3));
    }

    /** Converts dp to pixels. */
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** The color of the checked swatch, or {@link AppColors#DEFAULT} for the first one. */
    private int swatchColor(RadioGroup group, int idBase, int[] palette, int[] darkPalette) {
        int index = group.getCheckedRadioButtonId() - idBase;
        if (index <= 0 || index > palette.length) {
            return AppColors.DEFAULT;
        }
        // The color on screen is the one that gets stored, so that it can be matched
        // again - and shown as checked - in either color scheme.
        int[] shown = darkPalette != null && AppThemes.getScheme(this) == AppThemes.SCHEME_DARK
                ? darkPalette : palette;
        return AppColors.resolve(this, shown[index - 1]);
    }

    /** Maps a checked theme radio button to its theme constant. */
    private int themeForCheckedId(int checkedId) {
        for (int i = 0; i < THEME_OPTIONS.length; i++) {
            if (THEME_OPTIONS[i] == checkedId) {
                return AppThemes.ALL[i];
            }
        }
        return AppThemes.HOLO;
    }

    /** Maps a checked color-scheme radio button to its scheme constant. */
    private int schemeForCheckedId(int checkedId) {
        for (int i = 0; i < SCHEME_OPTIONS.length; i++) {
            if (SCHEME_OPTIONS[i] == checkedId) {
                return AppThemes.SCHEMES[i];
            }
        }
        return AppThemes.SCHEME_LIGHT;
    }

    /**
     * Shows the color pickers that belong to the given theme and hides the others.
     * The device-default theme owns no colors the app could pick, so it comes with
     * a short note.
     */
    private void showColorSections(int theme, View holo, View material, View deviceNote) {
        holo.setVisibility(theme == AppThemes.HOLO ? View.VISIBLE : View.GONE);
        material.setVisibility(theme == AppThemes.MATERIAL ? View.VISIBLE : View.GONE);
        deviceNote.setVisibility(theme == AppThemes.DEVICE_DEFAULT ? View.VISIBLE : View.GONE);
    }

    /** The color the untouched given theme uses for the given attribute. */
    private int stockColor(int theme, int scheme, int attr, int fallbackRes) {
        return AppColors.stockColor(this, theme, scheme, attr,
                AppColors.resolve(this, fallbackRes));
    }

    /** Stores the choices made in the theme picker and restarts if they changed. */
    private void applyThemeChoice(RadioGroup themeGroup, RadioGroup schemeGroup,
                                  RadioGroup holoColors, RadioGroup materialPrimary,
                                  RadioGroup materialAccent) {
        int theme = themeForCheckedId(themeGroup.getCheckedRadioButtonId());
        int scheme = schemeForCheckedId(schemeGroup.getCheckedRadioButtonId());
        int holoAccent = swatchColor(holoColors, ID_HOLO_SWATCH, AppColors.HOLO_ACCENTS, null);
        int primary = swatchColor(materialPrimary, ID_PRIMARY_SWATCH, AppColors.MATERIAL_COLORS,
                AppColors.MATERIAL_COLORS_DARK);
        int accent = swatchColor(materialAccent, ID_ACCENT_SWATCH,
                AppColors.MATERIAL_ACCENT_COLORS, AppColors.MATERIAL_ACCENT_COLORS_DARK);

        boolean changed = theme != AppThemes.get(this)
                || scheme != AppThemes.getScheme(this)
                || holoAccent != AppColors.getHoloAccent(this)
                || primary != AppColors.getMaterialPrimary(this)
                || accent != AppColors.getMaterialAccent(this);
        if (!changed) {
            return;
        }

        AppThemes.set(this, theme);
        AppThemes.setScheme(this, scheme);
        AppColors.setHoloAccent(this, holoAccent);
        AppColors.setMaterialPrimary(this, primary);
        AppColors.setMaterialAccent(this, accent);
        restartActivity();
    }

    /**
     * Restarts the activity so a newly selected theme or language takes effect.
     *
     * <p>This deliberately relaunches the activity with a fresh intent instead of
     * calling {@link #recreate()}. When the theme changes between one that uses the
     * framework action bar (Holo) and one that uses a Toolbar (Material), the
     * framework saves the old action bar's view state and tries to restore it into
     * the new one, which throws a ClassCastException
     * ("ActionBarView$SavedState cannot be cast to Toolbar$SavedState") and kills
     * the app. A brand-new activity carries no saved view state, so that cannot
     * happen.</p>
     */
    private void restartActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
        finish();
    }

    // ------------------------------------------------------------------ helpers

    private List<X509Certificate> snapshot() {
        synchronized (downloaded) {
            return new ArrayList<X509Certificate>(downloaded);
        }
    }

    /**
     * Drops from {@code list} every certificate whose fingerprint is one of {@code
     * dropped}'s.
     *
     * <p>By fingerprint rather than by identity, because the certificates come from a
     * downloaded bundle, from a file the user picked and from the store itself, and
     * only the encoded content says whether two of them really are the same
     * certificate. {@code X509Certificate#equals} is not dependable across those
     * sources on the releases this app targets.
     */
    private static void dropFingerprints(List<X509Certificate> list,
                                         List<X509Certificate> dropped) {
        if (dropped.isEmpty()) {
            return;
        }
        Set<String> fingerprints = new HashSet<String>();
        for (X509Certificate cert : dropped) {
            fingerprints.add(HashUtils.sha256Hex(cert));
        }
        for (Iterator<X509Certificate> it = list.iterator(); it.hasNext(); ) {
            if (fingerprints.contains(HashUtils.sha256Hex(it.next()))) {
                it.remove();
            }
        }
    }

    private void setBusy(boolean value) {
        busy = value;
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_LONG).show();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private String trim(String text) {
        if (text == null) {
            return "";
        }
        text = text.trim();
        return text.length() > 600 ? text.substring(0, 600) + "…" : text;
    }

    /**
     * Shows what a root script did: its output as it came back, followed by the app's
     * explanation of the markers a reader cannot be expected to know.
     *
     * <p>The output is quoted rather than translated - the markers are what a bug report
     * should contain - and the explanation is what makes it readable in the language the
     * app is set to (see {@link ScriptNotes}). The explanation is appended after the
     * {@link #trim} cut, so a long output never eats it.
     */
    private void showLogDialog(String title, String log) {
        String output = log == null ? "" : log.trim();
        StringBuilder message = new StringBuilder(output.isEmpty() ? "(no output)" : trim(output));
        String notes = ScriptNotes.explain(this, output);
        if (notes.length() > 0) {
            message.append("\n\n").append(getString(R.string.output_note_heading))
                    .append('\n').append(notes);
        }
        AlertDialog logDialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message.toString())
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .show();
        AppColors.applyToDialog(logDialog, AppThemes.get(this));
    }
}
