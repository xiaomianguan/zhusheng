package cc.wuersan008.zhusheng;

import android.app.Application;

/**
 * Captures the true device locale before any in-app language override is
 * applied, so the "System default" option can always restore it.
 */
public class ZhushengApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppLanguage.captureSystemLocale();
    }
}
