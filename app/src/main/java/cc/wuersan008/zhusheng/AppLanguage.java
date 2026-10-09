package cc.wuersan008.zhusheng;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;

import java.util.Locale;

/**
 * Persists and applies the user's language choice.
 *
 * <p>By default the app follows the system locale. Android then picks the
 * matching {@code values-*} directory, and a locale outside the supported set
 * falls back to {@code values} (English). The user can override this from the
 * menu with any of the supported languages.</p>
 */
public final class AppLanguage {

    /** Follow the device locale (the default). */
    public static final int SYSTEM = 0;
    /** Force English. */
    public static final int ENGLISH = 1;
    /** Force Simplified Chinese. */
    public static final int CHINESE = 2;
    /** Force Traditional Chinese. */
    public static final int CHINESE_TRADITIONAL = 3;
    /** Force Polish. */
    public static final int POLISH = 4;
    /** Force Russian. */
    public static final int RUSSIAN = 5;
    /** Force Swedish. */
    public static final int SWEDISH = 6;

    /** The highest constant, so a stale saved value can be rejected. */
    private static final int MAX = SWEDISH;

    /**
     * All options, in the order they are shown to the user: the system default
     * first, then the supported languages in their fixed order.
     */
    public static final int[] ALL = {
            SYSTEM, CHINESE, CHINESE_TRADITIONAL, ENGLISH, POLISH, RUSSIAN, SWEDISH};

    private static final String PREFS = "zhusheng_settings";
    private static final String KEY_LANGUAGE = "language";

    /** The device locale captured at process start, before any in-app override. */
    private static Locale systemLocale;

    private AppLanguage() {
    }

    /** Called from the Application so the real system locale is captured first. */
    public static void captureSystemLocale() {
        if (systemLocale == null) {
            systemLocale = Locale.getDefault();
        }
    }

    /** The device locale, used for the "System default" option. */
    public static Locale systemLocale() {
        if (systemLocale == null) {
            systemLocale = Locale.getDefault();
        }
        return systemLocale;
    }

    /** Returns the saved language, defaulting to "System default". */
    public static int get(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int value = prefs.getInt(KEY_LANGUAGE, SYSTEM);
        if (value < SYSTEM || value > MAX) {
            value = SYSTEM;
        }
        return value;
    }

    /** Stores the language choice. */
    public static void set(Context context, int language) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_LANGUAGE, language)
                .apply();
    }

    /** Maps a language constant to the Locale that should be applied. */
    public static Locale localeFor(int language) {
        switch (language) {
            case ENGLISH:
                return Locale.ENGLISH;
            case CHINESE:
                return Locale.SIMPLIFIED_CHINESE;
            case CHINESE_TRADITIONAL:
                return Locale.TRADITIONAL_CHINESE;
            case POLISH:
                return new Locale("pl", "PL");
            case RUSSIAN:
                return new Locale("ru", "RU");
            case SWEDISH:
                return new Locale("sv", "SE");
            default:
                return systemLocale();
        }
    }

    /** Maps a language constant to its display name. */
    public static int labelRes(int language) {
        switch (language) {
            case ENGLISH:
                return R.string.language_english;
            case CHINESE:
                return R.string.language_chinese;
            case CHINESE_TRADITIONAL:
                return R.string.language_chinese_traditional;
            case POLISH:
                return R.string.language_polish;
            case RUSSIAN:
                return R.string.language_russian;
            case SWEDISH:
                return R.string.language_swedish;
            default:
                return R.string.language_system;
        }
    }

    /**
     * Applies the stored language to the given context's resources. Must be
     * called before the content view is created.
     */
    public static void apply(Context context) {
        Locale locale = localeFor(get(context));
        Locale.setDefault(locale);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(locale);
        } else {
            config.locale = locale;
        }
        context.getResources().updateConfiguration(
                config, context.getResources().getDisplayMetrics());
    }
}
