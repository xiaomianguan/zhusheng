package cc.wuersan008.zhusheng;

import android.os.Build;

import java.util.Locale;

/**
 * Detects Smartisan hardware, whose ROMs ship the manufacturer theme, so that the app
 * can open wearing it (see {@link AppThemes}).
 *
 * <p>No single property identifies every device, so three are checked. The build
 * fingerprint names either the vendor or, on builds signed by its parent
 * company, the build host {@code DELTAINNO}. The {@code ro.product.manufacturer}
 * and {@code ro.product.brand} properties are checked as well, because they
 * survive a ROM rebuild that replaces the fingerprint: an OD105 running an AOSP
 * based ROM reports {@code Rock/odin/odin:7.1.1/...} while still declaring
 * {@code ro.product.manufacturer=smartisan}.</p>
 */
public final class LaoLuo {

    private static final String SMARTISAN = "smartisan";
    private static final String DELTAINNO = "deltainno";

    private LaoLuo() {
    }

    /** True if the app is running on Smartisan hardware. */
    public static boolean isSmartisan() {
        return isSmartisanFingerprint(Build.FINGERPRINT)
                || isSmartisanVendor(Build.MANUFACTURER)
                || isSmartisanVendor(Build.BRAND);
    }

    /**
     * Matches a build fingerprint: Smartisan OS builds name the vendor, while
     * builds produced by DELTAINNO name the build host instead.
     */
    static boolean isSmartisanFingerprint(String fingerprint) {
        String value = lower(fingerprint);
        return value.contains(SMARTISAN) || value.contains(DELTAINNO);
    }

    /**
     * Matches a manufacturer or brand product property, either of which keeps
     * naming the vendor even when the fingerprint has been replaced.
     */
    static boolean isSmartisanVendor(String value) {
        return lower(value).contains(SMARTISAN);
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }
}
