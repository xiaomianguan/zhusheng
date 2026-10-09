# zhusheng

![slop badge](.github/assets/slop_badge.webp)

[简体中文](README.md) | [繁體中文](README_zh_TW.md) | English | [Polski](README_pl_PL.md) | [Русский](README_ru_RU.md) | [Svenska](README_sv_SE.md)

**zhusheng** restores system-wide TLS/HTTPS trust on old Android devices
(Android **4.0 - 6.0**, API 14 - 23) by downloading the official root
certificates published by the major TLS/SSL providers and installing them into
the Android certificate stores.

Old devices ship with an outdated set of trusted roots. When a site is served by
a CA that did not exist (or was not trusted) when the device was built - most
famously **Let's Encrypt** - every HTTPS request fails with a certificate error
and the device "cannot access the internet normally". zhusheng fixes this by
installing the missing roots.

* Works on **all architectures** (the app is pure Java / no native code).
* Runs on **Android 4.0 through 6.0**.
* Uses only **native Android UI components** - no AndroidX, no third-party
  libraries.

## How it works

1. **Download.** Pick the providers you need (or keep the recommended set) and
   tap *Download selected*. The certificates are fetched from the official CA
   endpoints listed below.
2. **Install.** Two modes are provided:
   * **SYSTEM store (root)** - the certificates are written as PEM files into
     `/system/etc/security/cacerts/<subject_hash>.<n>`, exactly like the
     preinstalled roots, then `/system` is remounted read-only again. A reboot
     applies the change. This makes them trusted by *every* app.
   * **USER store (no root)** - the certificates are installed through the
     Android certificate installer (`KeyChain.createInstallIntent`). On Android
     versions **below 7.0** certificates in the user store are trusted
     system-wide, so this also fixes HTTPS for all apps without root. The button
     asks how the confirmations of the installer should go, and offers the three
     ways of doing that (`UserInstallMode`, remembered for the next run):
     * **one prompt for the whole set** - the whole set is offered to the
       installer in **one** intent first, as a credentials bundle with a
       `CACERT_<n>` entry per certificate
       (`CertInstaller.userStoreBulkInstallIntent`). An installer that understands
       it adds every root in a single pass; some 4.x builds read the bundle and
       quietly ignore it, and the app then falls back to one certificate per
       dialog;
     * **one prompt per certificate** - the bundle is not tried at all, so the
       installer is asked about one certificate at a time. Slower than the first
       way, but it behaves the same on every installer;
     * **answer the prompts automatically** - the same run as the first way, with
       the **auto-confirm service** answering the prompts.

     Certificates the device **already trusts are left alone**, and duplicates
     between the sources are dropped (`TrustedRoots`), so a second run - or one
     that continues an interrupted install - has nothing left to confirm.

     The **auto-confirm service** is optional and needs **Android 4.1 or newer**.
     An app may not switch on its own accessibility service, so the user has to do
     it in the system accessibility settings - which is why the app explains what
     the service is, and quotes the name of the entry to look for, before it opens
     that page. It also warns that the service works by tapping through screens
     quickly, so the display can flash while it runs, and points anyone sensitive to
     flashing lights at the two ways that confirm by hand. The menu entry
     *Auto-confirm installs* does that, and so does picking the automatic way above
     without the service switched on; in the second case the install starts by
     itself once the user is back from the settings.
     While a run of this app is going on, the service clicks the *OK* button of the
     installer's dialogs - and, for a removal, the rows of the certificate list;
     outside such a run it does nothing at all, and the two ways that confirm by
     hand never tell it to start. On Android 4.0 the accessibility framework cannot
     click a button at all (`performAction(ACTION_CLICK)` arrived with
     `View.performAccessibilityAction` in 4.1), so a 4.0 device confirms the
     prompts by hand.

3. **Remove.** Both stores can be emptied again, so an install can be undone
   without a second device or a factory reset:
   * **SYSTEM store (root)** - deletes the files this app added, and only those.
     The app records the name of every file it copies (`system-installed.txt` in
     its private `files` directory) and checks each one against the certificate it
     is meant to hold before deleting it: a root the device shipped with and the
     same root inside one of the bundles can be the very same bytes, so the store
     alone cannot tell them apart, and a preinstalled root must never be deleted.
   * **USER store** - the user store has no API for removing a certificate, so
     **3b. Remove from USER store** asks which of three ways to take, the way 2b
     asks how the install prompts should go (`UserRemoveMode`, also remembered for
     the next run):
     * **Delete the files with root** - the files under
       `/data/misc/user/0/cacerts-added` (on older releases
       `/data/misc/keychain/cacerts-added`) are read directly. A file goes only
       when its content is one of the certificates that are loaded. This is the
       only way that needs root, and on a device without root it is greyed out and
       labelled as such, so it cannot be picked there.
     * **Remove by hand in the settings** - the certificate list of the system
       settings is opened and each certificate is removed there by hand: tap its
       row, press *Remove*, confirm. Nothing is deleted by the app, and it needs no
       root. The dialog spells the steps out and offers to open that screen; the
       screenshots for that guide are still to come.
     * **Remove automatically (accessibility)** - the same list, worked by the
       auto-confirm service (see below).

   The ways that delete anything - 3a and the root way of 3b - ask for
   confirmation first, and both of them need root: the user store because there is
   no API, not because of the way it is written.

   The third way leaves the removal to the **auto-confirm service**: the app
   opens that same certificate list and hands the names of the loaded certificates
   to the service, which taps the row of each one, presses *Remove* on the details
   screen that opens and answers the confirmation that follows. Only rows whose
   name matches one of those certificates are ever tapped, and it stops as soon as
   the app is in front again - the list belongs to the user once more. It needs the
   same service switched on and walks the settings screen of the release it runs
   on; on Android 5.0 and 6.0 that is a row tap, the *Remove* button under the
   certificate details and a *Yes*. Without the service switched on the app
   explains it and offers the page that switches it on, and asks for the removal
   again on the way back.

   Each run of a root script - the installs and the removals of both stores (2a, 3a
   and the root way of 3b) - ends in a dialog that quotes the output of the script
   exactly as it came back, because that output is what a bug report should
   contain, and beneath it explains the markers it recognises in the language the
   app is set to (`ScriptNotes`): `ZHUSHENG_COPIED=`, `ZHUSHENG_REMOVED=`,
   `ZHUSHENG_DEDUPED=`, `ZHUSHENG_DONE`, and the lines `no system-installed.txt`,
   `could not write`, `command timed out` and `exec failed`. The explanations follow
   the order of the markers in the output, a marker printed twice is explained
   once, and anything the app does not recognise is still quoted as it came back.
   Output that is empty altogether is answered with the one thing that matters
   then: the script printed nothing, which is what a refused request for root looks
   like. (On a device without root the dialog is never reached - 2a and 3a say that
   root is missing, and the root way of 3b is greyed out.)

On **Android 7.0 (API 24) and newer** the system no longer trusts user-installed
certificates for apps that target it or newer, so the *user store* mode has no
effect for most apps there; the *system store* mode may still work, but it needs
root, and versions above 6.0 are beyond what zhusheng officially supports (later
releases have changed the system certificate store further). The app explains this
once, on the **first launch**: `LegacyNotice` is called at the end of `onCreate()`
and flags the device in `SharedPreferences` as `legacy_notice_shown`.

The system-store file names are generated with the OpenSSL
`x509 -subject_hash_old` value (MD5 over the DER-encoded subject), which the app
reproduces in Java (`HashUtils.subjectHashOld`). This was validated to match
OpenSSL byte-for-byte.

## Official certificate sources

| Provider | URL |
| --- | --- |
| Mozilla CA Bundle (all major CAs) | `https://curl.se/ca/cacert.pem` |
| Google Trust Services (bundle) | `https://pki.goog/roots.pem` |
| Let's Encrypt ISRG Root X1 | `https://letsencrypt.org/certs/isrgrootx1.pem` |
| Let's Encrypt ISRG Root X2 | `https://letsencrypt.org/certs/isrg-root-x2.pem` |
| Google GTS Root R1-R4 | `https://pki.goog/repo/certs/gtsr1.pem` … `gtsr4.pem` |
| Amazon Root CA 1-4 | `https://www.amazontrust.com/repository/AmazonRootCA1.pem` … |
| DigiCert Global Root CA / G2 / G3 / Trusted G4 / EV | `https://cacerts.digicert.com/…​.crt.pem` |
| GlobalSign Root CA R1 / R3 / R6 | `https://secure.globalsign.com/cacert/root-r1.crt` … |
| Sectigo USERTrust / COMODO RSA / ECC | `http://crt.sectigo.com/…​.crt` |
| Entrust Root CA G2 | `https://web.entrust.com/root-certificates/entrust_g2_ca.cer` |
| Go Daddy Root CA G2 | `https://certs.godaddy.com/repository/gdroot-g2.crt` |
| Microsoft RSA / ECC Root CA 2017 | `https://www.microsoft.com/pkiops/certs/…​.crt` |

The **Mozilla CA Bundle** is the catch-all: it contains the roots of every major
CA (including Certum, SSL.com, IdenTrust, Buypass, D-TRUST, QuoVadis, Actalis,
HARICA, T-TeleSec, Verisign/Symantec, and many more), so a single download fixes
the vast majority of broken sites.

### Security of the download

Because the device trust store is exactly what is broken, a fully validated
HTTPS connection can fail for these endpoints (e.g. `letsencrypt.org` is served
by a Let's Encrypt certificate). An Android 4.0 device cannot complete the TLS
handshake these hosts ask for at all - which is the situation this app exists for
- so the download falls back to plain HTTP:

1. tries a **normally validated** HTTPS download first, and only if that fails
2. retries accepting any server certificate,
3. retries the same URL over **plain HTTP**, and
4. falls back on the **plain-HTTP copy** the source carries, for the hosts that
   answer only over https (a fixed Internet Archive snapshot of the official
   file).

None of that is what makes the download trustworthy - the payload is checked
afterwards, root by root:

* every certificate must be a **self-signed root** (subject == issuer and the
  signature verifies with its own key),
* a single-certificate source is checked against its **pinned SHA-256
  fingerprint**, and
* a bundle is checked against the **fingerprints of every root it is expected to
  hold** (`KnownRoots`), so a payload fetched over plain HTTP cannot smuggle in a
  root of its own.

A tampered download therefore cannot silently install a forged root.

## Themes

The app offers three selectable themes and two color schemes, chosen from the
**Theme** button in the top bar; the choice is remembered in `SharedPreferences`.
**Material is the default on Android 5.0 and newer** (it matches the system around
it); Android 4.x starts on Holo. Smartisan hardware starts on **Device default**
instead (`LaoLuo`), so the app opens wearing the manufacturer's own look on the ROM
it was made for. Once a theme has been picked on purpose, that
choice always wins.

| Option | API 14 - 20 (4.0 - 4.4W) | API 21+ (5.0 - 6.0) |
| --- | --- | --- |
| **Holo** | `Theme.Holo.Light.DarkActionBar` | `Theme.Holo.Light.DarkActionBar` |
| **Material** | unavailable (grayed out) | `Theme.Material.Light.DarkActionBar` |
| **Device default** | `Theme.DeviceDefault.Light.DarkActionBar` | `Theme.DeviceDefault.Light.DarkActionBar` |

* **Holo** is the original theme and is available on every supported version, but
  it is only the default on Android 4.x.
* **Material** uses the framework's Material Design 1 components, so it is only
  available on Android 5.0+, where it is also the **default**. On Android 4.x the
  option is **grayed out** in the picker with a note underneath explaining why,
  rather than silently falling back to Holo.
* **Device default** uses whatever theme the device manufacturer ships
  (TouchWiz, Sense, MIUI, ...) through `Theme.DeviceDefault`.

### Color scheme

Every theme comes in a **light** and a **dark** scheme:

* **Light** (the default) uses the theme's `DarkActionBar` variant - a light
  content area with a dark action bar - which is the original look of the app.
* **Dark** uses the fully dark variant of the same theme (`Theme.Holo`,
  `Theme.Material`, `Theme.DeviceDefault`).

The colors of the app's own surfaces (background, body text, secondary text,
list divider) are declared as the theme attributes `zsBackground`,
`zsTextPrimary`, `zsTextSecondary` and `zsDivider` (see
`res/values/attrs.xml`), so the content area does not stay white in the dark
scheme. The six base themes set the light values; the dark scheme overrides them
with `ThemeOverlay.Zhusheng.Scheme.Dark` through `Theme.applyStyle()`.

### Colors

The theme dialog also offers color pickers. The default entry (the first
swatch) means **keep the theme's own color**, so making no choice at all leaves
the look exactly as it was; Holo's default swatch shows the cyan the theme itself
draws with. The selected swatch carries a checkmark through the middle: white in
the light color scheme, black in the dark one, whose 200 tints are too pale for a
white checkmark. The one exception is the first, "default" swatch, which wears
whatever color the theme itself uses, so its checkmark is picked by brightness.
The version
line under the subtitle
(`device_info`) is the one
element of the app that always wears an accent, so with no choice it wears the
color the theme itself uses and cannot clash with the check boxes beside it.

| Theme | Colors | Where they apply |
| --- | --- | --- |
| **Holo** | accent (4 Holo colors) | everything the theme draws in its own cyan: the list check boxes, the radio buttons and check marks of a dialog, the progress bar, links, the dialog title and the divider under it, the dialog buttons, the blue line along the bottom of the dark action bar, and the version line under the subtitle (the parts that need a per-view tint need API 21+) |
| **Material** | primary (10 Material colors) + accent (10) | the primary colors the action bar and the status bar of the **light** scheme (the dark one always keeps the theme's own dark bar and status bar), the accent the check boxes, the progress bar, the dialog buttons, the version line under the subtitle, and so on |
| **Device default** | none | the colors come from the manufacturer's theme |

* **Material** colors are applied through theme overlays
  (`android:colorPrimary` / `colorPrimaryDark` / `colorAccent` /
  `colorControlActivated`) defined in `res/values-v21/color_overlays.xml` and
  layered onto the theme by `AppColors.applyTheme()` after `setTheme()` and
  before `super.onCreate()`. That is how Material itself defines those colors,
  so every widget follows along.
* **Material**'s **accent** follows the color scheme, exactly as the theme's own
  accent does: the light scheme uses the 500s and the dark one the **200 tints**
  (Material itself takes that step - its accent is deep teal 500 `#009688` in a
  light theme and deep teal 200 `#80CBC4` in a dark one). The **primary** is applied
  in the light scheme only: a dark theme keeps its action bar dark and its status bar
  black, as Material's own dark theme does and as Material Design asks (a
  primary-colored bar would glare on top of a dark content area). So a primary
  chosen here never recolors the dark bar - the row stays usable, though, and the
  choice takes effect again in the light scheme. The accent row offers one entry the
  primary row has not, a **black / white** one before the green: black in the light
  scheme, white in the dark one, so it is visible either way. The light scheme keeps
  the 500s, so it looks exactly as it did. A choice is stored as the color that was
  on screen when it was made, so the same choice reads as the 500 in one scheme and
  as the 200 in the other; `AppColors.indexOfColor()` looks it up in both palettes.
* **Holo** has no accent attribute, and the framework widgets would not read one
  anyway, so its accent is applied view by view: `setButtonTintList()` for the
  check boxes and radio buttons, `setProgressTintList()` for the progress bar,
  the same for the controls inside a dialog (including the rows of a
  single-choice list), and a plain color for the dialog title, the divider under
  it, the dialog buttons, any links and the version line under the subtitle. The
  version line is the one element that keeps an accent even when the default
  entry is left alone - then it wears the theme's own color, so it always
  matches the check boxes. The **action bar itself keeps the theme's black**, and
  only the blue line the dark scheme draws along its bottom is covered with the
  accent color. Android 4.x has no tint API, so there only the text follows the
  accent; the picker says so underneath.
* The Holo palette does **not** offer the theme's own cyan (`#0099CC`): that is
  what the default entry already stands for, so the two would be indistinguishable.
  The Material accent palette leaves teal (`#009688`) out for the same reason - it
  is the accent Material itself draws its widgets with - and in its place adds the
  black / white entry the primary row has not, so both rows offer ten colors (the
  accent row swaps teal for black / white; teal is a valid primary).

The definitions live in `res/values/styles.xml`, with the Android 5.0+ override
in `res/values-v21/styles.xml`. The active theme is applied in
`MainActivity.onCreate` via `setTheme()` before the content view is created.
Whether a theme is usable on the current device is decided by
`AppThemes.isAvailable()`; `get()` falls back to Holo when the saved theme is
not (for instance after restoring a backup from a 5.0+ device onto a 4.x one).

Switching the theme, the color scheme or a color restarts the activity with a
**fresh intent** (rather than
calling `recreate()`): Holo and Material use different action-bar
implementations, and `recreate()` makes the framework restore one action bar's
saved state into the other, which throws a `ClassCastException`
(`ActionBarView$SavedState cannot be cast to Toolbar$SavedState`) and crashes
the app.

## Interface

The main screen is one scrolling page: a column of views of which the flexible one
is the list of sources. `PageScrollView` gives that list the room left over on the
page - the screen less the header above it, the progress bar and the status line
below it, and the buttons under those - or the minimum the list asks for when there
is less than that, and it is then the list that scrolls while everything around it
stays put. Only when that room is smaller than the minimum does the page itself
scroll, and then only by the little that is over. `PageListView` is what makes that
possible: it reports the height of all of its rows rather than the single row the
framework measures a list to inside a scrolling page, and works its own minimum -
two rows - out from how tall a row turns out to be. The list's scrollbar is kept on
screen, so a row that ends exactly at its bottom edge is not the only sign that more
sources follow.

The page carries padding on the left and the right only, and the vertical spacing
belongs to the column inside. A `ScrollView` clamps its scroll to the height of its
content less its own padding, so a vertical padding on the page is room the content
never scrolls into: it stands still at the top and at the bottom of the scrollable
area while the content slides behind it, which reads as the page stopping short at
both ends.

The top bar carries the *Theme* button, and the menu behind it holds the language
chooser, the project website, GitHub, the About dialog and *Auto-confirm installs*
(from Android 4.1 up, where the accessibility framework can click a button).

## Localization

The UI ships in six languages: **简体中文**, **繁體中文**, **English**, **Polski**, **Русский** and **Svenska**.

* By default the app **follows the system locale**: Android picks the matching
  `res/values-*` directory, and a locale outside those six falls back to
  `res/values` (English). `zh-CN` uses `res/values-zh-rCN` (Simplified), `zh-TW`
  uses `res/values-zh-rTW` (Traditional), any other Chinese locale uses
  `res/values-zh` (Simplified), and `pl`, `ru` and `sv` use `res/values-pl`,
  `res/values-ru` and `res/values-sv`.
* A **Language** item in the menu lets the user override this with
  *System default* or any of the six languages, always in the same order:
  简体中文, 繁體中文, English, Polski, Русский, Svenska. The choice is stored in
  `SharedPreferences` and applied in `MainActivity.onCreate` through
  `AppLanguage.apply()` (an `updateConfiguration` override that works on
  API 14 - 23), followed by an activity restart.
* **Every** user-visible string comes from a resource, including the provider
  names and the notes of the certificate-source list: `CertCatalog` resolves
  them with `Context.getString()` while building the catalog. That build
  therefore happens after `AppLanguage.apply()` - a field initializer would run
  before the saved language is applied and would use the system locale instead.

The `values-zh-rCN` / `values-zh-rTW` / `values-zh` qualifiers (rather than
`b+zh+Hans` / `b+zh+Hant`) are used because BCP 47 script qualifiers are not
supported before Android 5.0 (API 21).

The README itself is translated into the same six languages, listed in the same
order in the switcher at the top: `README.md` (Simplified Chinese, the default),
`README_zh_TW.md`, `README_en_US.md`, `README_pl_PL.md`, `README_ru_RU.md` and
`README_sv_SE.md`.

## Building

Requirements: JDK 17 (or 21) and the Android SDK (build-tools 34, platform
android-33).

```bash
./gradlew assembleDebug      # -> app/build/outputs/apk/debug/app-debug.apk
```

`gradlew` / `gradlew.bat` find a suitable JDK themselves. Gradle 8.7's bundled
Groovy compiler cannot read the class files of a JDK newer than it knows, so a build
script that names any JDK type - the Boolean in `buildFeatures { buildConfig true }`
is enough - fails with "Unsupported class file major version 70"; a machine whose
default `java` is JDK 26 is exactly that. The wrapper therefore looks for 17 and then
21 in the usual places (`/usr/libexec/java_home` on macOS, Homebrew, the Linux
distributions' `/usr/lib/jvm`, the JDK bundled with Android Studio, the usual Windows
install folders) and points `JAVA_HOME` at what it finds. A `JAVA_HOME` that already
names a JDK of one of those versions is left alone, so a deliberate choice always
stands. When it finds nothing it says so, and the fix is to install JDK 17 or point
`JAVA_HOME` at one.

Those two blocks - the one in `gradlew`, and the one at the top of `gradlew.bat` - are
something Gradle's `wrapper` task knows nothing about, so regenerating the wrapper
throws them away. Run `./build-prep.sh` (or `build-prep.bat` on Windows) afterwards to
put them back; `./build-prep.sh --check` only reports, changing nothing, which is what
CI wants. The blocks themselves live in `tools/gradlew-jdk17.sh` and
`tools/gradlew-jdk17.bat`, so the two scripts share one copy.

The About dialog (top-bar menu -> About) lists the version name and code, the package
id, and the build time and build type (debug / release) under its text, so that a bug
report can name the build it came from. The build time is written into
`BuildConfig.BUILD_DATE` by `build.gradle` while the build is configured, so it names
the moment of the build rather than of the install; the cost is that the same commit
no longer produces a byte-identical APK.

## Project layout

```
app/src/main/java/cc/wuersan008/zhusheng/
  MainActivity.java     UI + download/install orchestration
  CertCatalog.java      the list of official certificate sources
  KnownRoots.java       the roots the two bundles are expected to hold
  CertSource.java       one catalog entry
  Downloader.java       download with the https-to-plain-http fallback chain
  CertParser.java       PEM / DER parsing (single certs and bundles)
  HashUtils.java        PEM encoding, SHA-256, subject_hash_old
  CertInstaller.java    SYSTEM store (root) + USER store install and removal
  ScriptNotes.java      explains the markers in the output of a root script
  TrustedRoots.java     what the device already trusts (AndroidCAStore)
  UserInstallMode.java  the three ways a USER store install can go + the choice
  UserRemoveMode.java   the three ways a USER store removal can go + the choice
  InstallerAutoConfirm.java  install-running / service-enabled flags
  RemovalAutoConfirm.java    removal-running flag + the names to look for
  AutoConfirmService.java    accessibility service: installer dialogs, certificate list
  RootUtils.java        su shell helpers
  SourceAdapter.java    ListView adapter
  PageListView.java     the source list, sized for a page that scrolls
  PageScrollView.java   the page: the list's room, the scrolling, the drags
  LegacyNotice.java     the once-per-install notice about Android 7.0+
  AppThemes.java        theme selection + persistence
  AppColors.java        the colors of the theme, applied per view or per overlay
  AppLanguage.java      language selection + persistence
  LaoLuo.java           Smartisan hardware detection (the default theme)
  ZhushengApp.java      Application (captures the real system locale)
```

## Limitations

* Writing to `/system/etc/security/cacerts` needs root. Without root use the
  USER store mode.
* Removing from the SYSTEM store needs root as well. In the user store only one of
  the three ways does: that one reads the files directly, because the store has no
  API for removing a certificate. The other two need no root at all - the
  walkthrough of the settings list, and the auto-confirm service doing the same
  thing by itself. That second one drives the settings screen of the release it
  runs on, so a device whose screen is laid out differently is left to the
  walkthrough.
* The auto-confirm service needs Android 4.1 or newer; on Android 4.0 every USER
  store prompt has to be confirmed by hand.
* Apps that pin certificates or bundle their own trust store are unaffected.

## License

This project is released under the [Unlicense](UNLICENSE), which dedicates it to
the public domain. You are free to copy, modify, publish, use, compile, sell or
distribute it, in source or binary form, for any purpose, commercial or
non-commercial, by any means, with no attribution required.

The software is provided "as is", without warranty of any kind. The full text is
in the [UNLICENSE](UNLICENSE) file.
