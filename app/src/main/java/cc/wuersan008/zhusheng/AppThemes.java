package cc.wuersan008.zhusheng;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * Persists and resolves the user's theme choice.
 *
 * <p>A theme has two parts: the theme itself (Holo, Material or the device
 * default) and its color scheme (light or dark). Both are stored in
 * SharedPreferences and applied in {@code Activity.onCreate} before the content
 * view is created.</p>
 */
public final class AppThemes {

    /**
     * Holo - the original theme, available on every supported version and the
     * default one on Android 4.x, which has no Material.
     */
    public static final int HOLO = 0;
    /** Material Design 1 - available, and the default, from Android 5.0 (API 21). */
    public static final int MATERIAL = 1;
    /** The theme supplied by the device manufacturer. */
    public static final int DEVICE_DEFAULT = 2;

    /** All options, in the order they are shown to the user. */
    public static final int[] ALL = {HOLO, MATERIAL, DEVICE_DEFAULT};

    /**
     * Light color scheme: the DarkActionBar variant of the theme, i.e. a light
     * content area with a dark action bar. This is the original look and the
     * default.
     */
    public static final int SCHEME_LIGHT = 0;
    /** Dark color scheme: the fully dark variant of the theme. */
    public static final int SCHEME_DARK = 1;

    /** All color schemes, in the order they are shown to the user. */
    public static final int[] SCHEMES = {SCHEME_LIGHT, SCHEME_DARK};

    private static final String PREFS = "zhusheng_settings";
    private static final String KEY_THEME = "theme";
    private static final String KEY_SCHEME = "color_scheme";

    /**
     * Sentinel for "no theme was ever chosen", so that a device whose user never
     * touched the picker can be told apart from one where Holo was picked on
     * purpose - the two would otherwise both read as {@link #HOLO}.
     */
    private static final int NONE = -1;

    private AppThemes() {
    }

    /**
     * True if the given theme can be used on the device the app is running on.
     *
     * <p>Material only exists from Android 5.0 (API 21) onward. On Android 4.x
     * the option is still listed in the picker, but grayed out, so the user is
     * told why rather than being offered a theme that would silently look like
     * Holo.</p>
     */
    public static boolean isAvailable(int theme) {
        return theme != MATERIAL || Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    /**
     * The theme used until the user picks one: Material on Android 5.0 and newer,
     * where it exists, and Holo on Android 4.x, where it does not. Smartisan
     * hardware starts on {@link #DEVICE_DEFAULT} instead, so that the app opens
     * wearing the manufacturer's own look on the ROM it was made for.
     */
    private static int defaultTheme() {
        if (LaoLuo.isSmartisan()) {
            return DEVICE_DEFAULT;
        }
        return isAvailable(MATERIAL) ? MATERIAL : HOLO;
    }

    /**
     * Returns the saved theme, or {@link #defaultTheme()} when none was ever chosen.
     *
     * <p>A choice the user made is always kept, including Holo: the stored value is
     * only replaced when it is unknown or names a theme this device does not have
     * (for instance a Material choice restored onto an Android 4.x device).</p>
     */
    public static int get(Context context) {
        SharedPreferences prefs =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int value = prefs.getInt(KEY_THEME, NONE);
        if (value < HOLO || value > DEVICE_DEFAULT || !isAvailable(value)) {
            value = defaultTheme();
        }
        return value;
    }

    /** Stores the theme choice. */
    public static void set(Context context, int theme) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_THEME, theme)
                .apply();
    }

    /** Returns the saved color scheme, defaulting to light. */
    public static int getScheme(Context context) {
        SharedPreferences prefs =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int value = prefs.getInt(KEY_SCHEME, SCHEME_LIGHT);
        if (value < SCHEME_LIGHT || value > SCHEME_DARK) {
            value = SCHEME_LIGHT;
        }
        return value;
    }

    /** Stores the color scheme. */
    public static void setScheme(Context context, int scheme) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_SCHEME, scheme)
                .apply();
    }

    /** Maps a theme and a color scheme to the style resource applied with setTheme(). */
    public static int styleRes(int theme, int scheme) {
        boolean dark = scheme == SCHEME_DARK;
        switch (theme) {
            case MATERIAL:
                return dark
                        ? R.style.Theme_Zhusheng_Material_Dark
                        : R.style.Theme_Zhusheng_Material;
            case DEVICE_DEFAULT:
                return dark
                        ? R.style.Theme_Zhusheng_DeviceDefault_Dark
                        : R.style.Theme_Zhusheng_DeviceDefault;
            default:
                return dark
                        ? R.style.Theme_Zhusheng_Holo_Dark
                        : R.style.Theme_Zhusheng_Holo;
        }
    }

    /** Maps a theme constant to its display name. */
    public static int labelRes(int theme) {
        switch (theme) {
            case MATERIAL:
                return R.string.theme_material;
            case DEVICE_DEFAULT:
                return R.string.theme_device_default;
            default:
                return R.string.theme_holo;
        }
    }

    /** Maps a color scheme constant to its display name. */
    public static int schemeLabelRes(int scheme) {
        return scheme == SCHEME_DARK
                ? R.string.theme_scheme_dark
                : R.string.theme_scheme_light;
    }
}

