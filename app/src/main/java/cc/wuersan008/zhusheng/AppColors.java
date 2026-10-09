package cc.wuersan008.zhusheng;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.PathShape;
import android.os.Build;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Persists and applies the colors of the app's theme.
 *
 * <p>Three colors can be chosen: the accent color of the Holo theme, and the
 * primary and accent colors of the Material theme. Each of them defaults to
 * {@link #DEFAULT}, which leaves the theme's own colors untouched, so the
 * out-of-the-box look is unchanged.</p>
 *
 * <p>How a color is applied depends on what the platform supports:</p>
 *
 * <ul>
 *   <li>Material on API 21+ is recolored through theme overlays
 *       ({@code android:colorPrimary} / {@code android:colorAccent}), which is
 *       exactly how Material itself defines those colors, so the action bar, the
 *       status bar and every widget follow along. The primary color is applied in
 *       the light scheme only: a dark theme keeps its action bar dark, as Material's
 *       own does.</li>
 *   <li>The Holo theme has no accent attribute at all, so its accent is applied
 *       to the individual views that wear it - the check boxes of the list, the
 *       progress bar and the buttons and widgets of a dialog - through the
 *       per-view tint APIs, or through a color filter on the versions that have
 *       no tint API. The action bar is deliberately left alone.</li>
 * </ul>
 */
public final class AppColors {

    /** Leaves the theme's own colors in place. */
    public static final int DEFAULT = 0;

    /** Height of the line Holo draws along the bottom of the dark action bar. */
    private static final float BAR_LINE_DP = 2f;

    /**
     * The accent colors offered by the Holo theme.
     *
     * <p>Holo's own accent - the cyan its check boxes, radio buttons and progress
     * bars are drawn in - is deliberately missing: that is what the picker's
     * "default" entry already stands for, so the two would be indistinguishable.</p>
     */
    public static final int[] HOLO_ACCENTS = {
            R.color.holo_accent_green,
            R.color.holo_accent_orange,
            R.color.holo_accent_purple,
            R.color.holo_accent_red,
    };

    /** The colors offered by the Material primary picker. */
    public static final int[] MATERIAL_COLORS = {
            R.color.mat_red,
            R.color.mat_pink,
            R.color.mat_purple,
            R.color.mat_indigo,
            R.color.mat_blue,
            R.color.mat_teal,
            R.color.mat_green,
            R.color.mat_orange,
            R.color.mat_brown,
            R.color.mat_blue_grey,
    };

    /**
     * The same palette for the dark color scheme, in the 200 tints - the same step
     * the accent picker takes there, so that the two rows look alike.
     *
     * <p>The primary itself is applied in the light scheme only: a dark theme keeps
     * its action bar dark (see {@link #applyTheme(Activity, int, int)}). These tints
     * are still what the primary row shows in the dark scheme, and what a choice
     * made there is stored as, so a color picked in one scheme is found again in
     * the other.</p>
     */
    public static final int[] MATERIAL_COLORS_DARK = {
            R.color.mat_red_200,
            R.color.mat_pink_200,
            R.color.mat_purple_200,
            R.color.mat_indigo_200,
            R.color.mat_blue_200,
            R.color.mat_teal_200,
            R.color.mat_green_200,
            R.color.mat_orange_200,
            R.color.mat_brown_200,
            R.color.mat_blue_grey_200,
    };

    /**
     * The colors offered by the Material accent picker: {@link #MATERIAL_COLORS}
     * without teal, plus the black / white accent, which has no primary counterpart.
     *
     * <p>Teal is the color Material draws its own accents in - the check boxes, the
     * radio buttons, the progress bar - so the picker's "default" entry already
     * stands for it, and offering it a second time would put the same color in the
     * row twice. Holo's own cyan is left out of {@link #HOLO_ACCENTS} for the same
     * reason.</p>
     *
     * <p>The black / white entry sits before the green, where the hues run on: it is
     * black in the light color scheme and white in the dark one, so it stays visible
     * either way (see {@link #MATERIAL_ACCENT_COLORS_DARK}).</p>
     */
    public static final int[] MATERIAL_ACCENT_COLORS = {
            R.color.mat_red,
            R.color.mat_pink,
            R.color.mat_purple,
            R.color.mat_indigo,
            R.color.mat_blue,
            R.color.mat_black,
            R.color.mat_green,
            R.color.mat_orange,
            R.color.mat_brown,
            R.color.mat_blue_grey,
    };

    /**
     * The same palette for the dark color scheme, in the 200 tints - the step
     * Material itself takes with its own accent, which is deep teal 500 in a light
     * theme and deep teal 200 in a dark one (see {@link #defaultAccent}). A 500
     * accent is hard to read against dark surfaces, which is what the tint is for.
     */
    public static final int[] MATERIAL_ACCENT_COLORS_DARK = {
            R.color.mat_red_200,
            R.color.mat_pink_200,
            R.color.mat_purple_200,
            R.color.mat_indigo_200,
            R.color.mat_blue_200,
            R.color.mat_white,
            R.color.mat_green_200,
            R.color.mat_orange_200,
            R.color.mat_brown_200,
            R.color.mat_blue_grey_200,
    };

    /** Names of {@link #HOLO_ACCENTS}, in the same order. */
    public static final int[] HOLO_ACCENT_NAMES = {
            R.string.color_green,
            R.string.color_orange,
            R.string.color_purple,
            R.string.color_red,
    };

    /** Names of {@link #MATERIAL_COLORS}, in the same order. */
    public static final int[] MATERIAL_COLOR_NAMES = {
            R.string.color_red,
            R.string.color_pink,
            R.string.color_purple,
            R.string.color_indigo,
            R.string.color_blue,
            R.string.color_teal,
            R.string.color_green,
            R.string.color_orange,
            R.string.color_brown,
            R.string.color_blue_grey,
    };

    /** Names of {@link #MATERIAL_ACCENT_COLORS}, in the same order. */
    public static final int[] MATERIAL_ACCENT_COLOR_NAMES = {
            R.string.color_red,
            R.string.color_pink,
            R.string.color_purple,
            R.string.color_indigo,
            R.string.color_blue,
            R.string.color_black_white,
            R.string.color_green,
            R.string.color_orange,
            R.string.color_brown,
            R.string.color_blue_grey,
    };

    /**
     * Theme overlays that turn the {@link #MATERIAL_COLORS} entry at the same
     * index into a Material primary color. They only exist from API 21 onward.
     */
    private static final int[] MATERIAL_PRIMARY_OVERLAYS = {
            R.style.ThemeOverlay_Zhusheng_Material_Primary_Red,
            R.style.ThemeOverlay_Zhusheng_Material_Primary_Pink,
            R.style.ThemeOverlay_Zhusheng_Material_Primary_Purple,
            R.style.ThemeOverlay_Zhusheng_Material_Primary_Indigo,
            R.style.ThemeOverlay_Zhusheng_Material_Primary_Blue,
            R.style.ThemeOverlay_Zhusheng_Material_Primary_Teal,
            R.style.ThemeOverlay_Zhusheng_Material_Primary_Green,
            R.style.ThemeOverlay_Zhusheng_Material_Primary_Orange,
            R.style.ThemeOverlay_Zhusheng_Material_Primary_Brown,
            R.style.ThemeOverlay_Zhusheng_Material_Primary_BlueGrey,
    };

    /**
     * The accent counterpart of {@link #MATERIAL_PRIMARY_OVERLAYS}, with the teal
     * entry dropped along with its swatch: {@code ThemeOverlay...Accent.Teal} would
     * only restate the accent the Material theme already has.
     */
    private static final int[] MATERIAL_ACCENT_OVERLAYS = {
            R.style.ThemeOverlay_Zhusheng_Material_Accent_Red,
            R.style.ThemeOverlay_Zhusheng_Material_Accent_Pink,
            R.style.ThemeOverlay_Zhusheng_Material_Accent_Purple,
            R.style.ThemeOverlay_Zhusheng_Material_Accent_Indigo,
            R.style.ThemeOverlay_Zhusheng_Material_Accent_Blue,
            R.style.ThemeOverlay_Zhusheng_Material_Accent_Black,
            R.style.ThemeOverlay_Zhusheng_Material_Accent_Green,
            R.style.ThemeOverlay_Zhusheng_Material_Accent_Orange,
            R.style.ThemeOverlay_Zhusheng_Material_Accent_Brown,
            R.style.ThemeOverlay_Zhusheng_Material_Accent_BlueGrey,
    };

    /** The {@link #MATERIAL_ACCENT_OVERLAYS} of the dark color scheme. */
    private static final int[] MATERIAL_ACCENT_OVERLAYS_DARK = {
            R.style.ThemeOverlay_Zhusheng_Material_Accent_Red_Dark,
            R.style.ThemeOverlay_Zhusheng_Material_Accent_Pink_Dark,
            R.style.ThemeOverlay_Zhusheng_Material_Accent_Purple_Dark,
            R.style.ThemeOverlay_Zhusheng_Material_Accent_Indigo_Dark,
            R.style.ThemeOverlay_Zhusheng_Material_Accent_Blue_Dark,
            R.style.ThemeOverlay_Zhusheng_Material_Accent_White_Dark,
            R.style.ThemeOverlay_Zhusheng_Material_Accent_Green_Dark,
            R.style.ThemeOverlay_Zhusheng_Material_Accent_Orange_Dark,
            R.style.ThemeOverlay_Zhusheng_Material_Accent_Brown_Dark,
            R.style.ThemeOverlay_Zhusheng_Material_Accent_BlueGrey_Dark,
    };

    private static final String PREFS = "zhusheng_settings";
    private static final String KEY_HOLO_ACCENT = "holo_accent";
    private static final String KEY_MATERIAL_PRIMARY = "material_primary";
    private static final String KEY_MATERIAL_ACCENT = "material_accent";

    private AppColors() {
    }

    // ------------------------------------------------------------- stored values

    /** The chosen Holo accent, or {@link #DEFAULT}. */
    public static int getHoloAccent(Context context) {
        return read(context, KEY_HOLO_ACCENT);
    }

    public static void setHoloAccent(Context context, int color) {
        write(context, KEY_HOLO_ACCENT, color);
    }

    /** The chosen Material primary color, or {@link #DEFAULT}. */
    public static int getMaterialPrimary(Context context) {
        return read(context, KEY_MATERIAL_PRIMARY);
    }

    public static void setMaterialPrimary(Context context, int color) {
        write(context, KEY_MATERIAL_PRIMARY, color);
    }

    /** The chosen Material accent color, or {@link #DEFAULT}. */
    public static int getMaterialAccent(Context context) {
        return read(context, KEY_MATERIAL_ACCENT);
    }

    public static void setMaterialAccent(Context context, int color) {
        write(context, KEY_MATERIAL_ACCENT, color);
    }

    private static int read(Context context, String key) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(key, DEFAULT);
    }

    private static void write(Context context, String key, int color) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(key, color)
                .apply();
    }

    /**
     * The color the app should paint its own accent-colored elements with, or
     * {@link #DEFAULT} when the theme's own color should be kept.
     */
    public static int chosenAccent(Context context, int theme) {
        switch (theme) {
            case AppThemes.HOLO:
                return getHoloAccent(context);
            case AppThemes.MATERIAL:
                return materialColor(context, MATERIAL_ACCENT_COLORS,
                        MATERIAL_ACCENT_COLORS_DARK, getMaterialAccent(context));
            default:
                return DEFAULT;
        }
    }

    /**
     * The stored color of a Material palette, in the colors of the current color
     * scheme, or {@link #DEFAULT} when nothing (or something unknown) is stored.
     *
     * <p>A choice is stored as the color that was on screen when it was made, so the
     * same choice shows up as the 500 in a light scheme and as the 200 tint in a dark
     * one. The theme's own widgets get this through the overlays; the app's own
     * accent-colored elements come here.</p>
     */
    private static int materialColor(Context context, int[] palette, int[] darkPalette, int stored) {
        int index = indexOfColor(context, palette, darkPalette, stored);
        if (index < 0) {
            return DEFAULT;
        }
        int[] shown = AppThemes.getScheme(context) == AppThemes.SCHEME_DARK
                ? darkPalette : palette;
        return resolve(context, shown[index]);
    }

    /**
     * The accent color the given theme itself draws its widgets in, i.e. what the
     * "default" entry of a color picker stands for.
     *
     * <p>Holo has no color attribute of its own, so its cyan is named directly.
     * Material and the device-default theme both read
     * {@code android:colorAccent}, which is the color their check boxes, progress
     * bars and dialog buttons are drawn in; before API 21 that attribute does not
     * exist and both themes are Holo underneath, so the cyan is the closest
     * match.</p>
     */
    public static int defaultAccent(Context context, int theme) {
        int scheme = AppThemes.getScheme(context);
        switch (theme) {
            case AppThemes.MATERIAL:
                return stockColor(context, theme, scheme, android.R.attr.colorAccent,
                        resolve(context, R.color.mat_teal));
            case AppThemes.DEVICE_DEFAULT:
                return stockColor(context, theme, scheme, android.R.attr.colorAccent,
                        resolve(context, R.color.zs_holo_accent));
            default:
                return resolve(context, R.color.zs_holo_accent);
        }
    }

    /**
     * The color the list's check boxes should be tinted with, or {@link #DEFAULT}.
     *
     * <p>Only the Holo theme needs this: the Material theme picks its check box
     * color up from android:colorAccent.</p>
     */
    public static int widgetTint(Context context, int theme) {
        return theme == AppThemes.HOLO ? getHoloAccent(context) : DEFAULT;
    }

    // ------------------------------------------------------------------ applying

    /**
     * Layers the color scheme and the chosen colors on top of the theme set with
     * {@link Activity#setTheme(int)}. Must be called after {@code setTheme()} and
     * before {@code super.onCreate()}, so that the colors are already in place
     * when the window and the content view are created.
     */
    public static void applyTheme(Activity activity, int theme, int scheme) {
        if (scheme == AppThemes.SCHEME_DARK) {
            activity.getTheme().applyStyle(R.style.ThemeOverlay_Zhusheng_Scheme_Dark, true);
        }

        // Only Material understands the color attributes, and only from API 21.
        if (theme != AppThemes.MATERIAL || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }

        boolean dark = scheme == AppThemes.SCHEME_DARK;

        // The primary color is a light-scheme color: Material's own dark theme
        // keeps its action bar dark and its status bar black, and Material Design
        // asks for the same, because a primary-colored bar would glare on top of a
        // dark content area. So a chosen primary is applied in the light scheme only
        // - in the dark one the theme's own dark bar and black status bar stay put,
        // whatever the picker holds. The picker row itself stays usable: the choice
        // is remembered and takes effect again in the light scheme.
        if (!dark) {
            int primary = indexOfColor(activity, MATERIAL_COLORS, MATERIAL_COLORS_DARK,
                    getMaterialPrimary(activity));
            if (primary >= 0) {
                activity.getTheme().applyStyle(MATERIAL_PRIMARY_OVERLAYS[primary], true);
            }
        }

        int accent = indexOfColor(activity, MATERIAL_ACCENT_COLORS, MATERIAL_ACCENT_COLORS_DARK,
                getMaterialAccent(activity));
        if (accent >= 0) {
            activity.getTheme().applyStyle(
                    (dark ? MATERIAL_ACCENT_OVERLAYS_DARK : MATERIAL_ACCENT_OVERLAYS)[accent],
                    true);
        }
    }

    /**
     * Recolors the app's own accent-colored elements. Must be called after
     * {@code setContentView()}, once those views exist.
     *
     * <p>The action bar itself is deliberately left alone: the accent belongs to
     * the widgets, not to the bar. Only the line the dark Holo scheme draws under
     * the bar is recolored, since Holo paints it in its own blue.</p>
     *
     * <p>The device info line is the app's own element, not one of the theme's, so
     * it wears an accent either way: the chosen one, or - when nothing was chosen -
     * the color the theme itself draws its widgets in, which is what the
     * "default" entry of a picker stands for.</p>
     */
    public static void applyToActivity(Activity activity, int theme) {
        int accent = chosenAccent(activity, theme);

        // The device info line is the app's own, not the theme's, so it always
        // wears an accent: the chosen one, or - when nothing was chosen - the one
        // the theme itself draws its widgets in, so that the two cannot clash.
        TextView info = (TextView) activity.findViewById(R.id.device_info);
        if (info != null) {
            info.setTextColor(accent == DEFAULT ? defaultAccent(activity, theme) : accent);
        }

        if (accent == DEFAULT) {
            // Nothing was chosen, so the rest of the theme stays exactly as it ships.
            return;
        }

        if (theme == AppThemes.HOLO) {
            tintActionBarLine(activity, accent);
        }

        ProgressBar progress = (ProgressBar) activity.findViewById(R.id.progress);
        if (progress != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                progress.setProgressTintList(ColorStateList.valueOf(accent));
                progress.setIndeterminateTintList(ColorStateList.valueOf(accent));
            } else {
                tintProgressBar(progress, accent);
            }
        }
    }

    /**
     * Recolors a progress bar on the versions without a tint API.
     *
     * <p>The bar is a layer list of a gray track and the colored bar itself, so
     * only the layer that carries the color is filtered and the track keeps the
     * color the theme gave it.</p>
     */
    private static void tintProgressBar(ProgressBar progress, int accent) {
        tintProgressLayer(progress.getProgressDrawable(), accent);
        tintProgressLayer(progress.getIndeterminateDrawable(), accent);
    }

    private static void tintProgressLayer(Drawable drawable, int accent) {
        if (!(drawable instanceof LayerDrawable)) {
            return;
        }
        tintGraphic(((LayerDrawable) drawable).findDrawableByLayerId(android.R.id.progress),
                accent);
    }

    /**
     * Recolors the accent-colored parts of a dialog.
     *
     * <p>Only the Holo theme needs this: Material paints its dialogs from
     * android:colorAccent and the device-default theme from the colors of the
     * device, but Holo has no such attribute. Its dialog buttons are therefore
     * colored directly - on every supported version - and the check and radio
     * buttons inside the dialog are tinted as well, through the tint API from API
     * 21 onward and through a color filter before that.</p>
     *
     * <p>Must be called after {@code show()}, once the dialog's views exist.</p>
     */
    public static void applyToDialog(AlertDialog dialog, int theme) {
        if (theme != AppThemes.HOLO) {
            return;
        }
        int accent = getHoloAccent(dialog.getContext());
        if (accent == DEFAULT) {
            return;
        }

        tintButton(dialog, DialogInterface.BUTTON_POSITIVE, accent);
        tintButton(dialog, DialogInterface.BUTTON_NEGATIVE, accent);
        tintButton(dialog, DialogInterface.BUTTON_NEUTRAL, accent);
        tintTitle(dialog, accent);

        // The rows of a single-choice list only exist once the dialog has been laid
        // out, so the widgets are tinted from the layout callback rather than here.
        final View decor = dialog.getWindow().getDecorView();
        decor.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    public void onGlobalLayout() {
                        tintWidgets(decor, accent);
                    }
                });
    }

    /** Recolors the text of one of a dialog's buttons, when the dialog has it. */
    private static void tintButton(AlertDialog dialog, int which, int accent) {
        Button button = dialog.getButton(which);
        if (button != null) {
            button.setTextColor(accent);
        }
    }

    /**
     * Recolors the title of a Holo dialog and the divider it draws underneath -
     * both are drawn in Holo's own blue, which has nothing to do with the accent.
     *
     * <p>The two views are looked up by name because the framework keeps their ids
     * private. A lookup that finds nothing (another platform version, another
     * dialog layout) simply leaves that part alone.</p>
     */
    private static void tintTitle(AlertDialog dialog, int accent) {
        TextView title = (TextView) dialog.findViewById(frameworkId("alertTitle"));
        if (title != null) {
            title.setTextColor(accent);
        }

        View divider = dialog.findViewById(frameworkId("titleDivider"));
        if (divider != null) {
            divider.setBackgroundColor(accent);
        }
    }

    /**
     * Recolors the line the dark Holo scheme draws along the bottom of the action
     * bar. The line is part of the graphic the bar paints itself with, so a line of
     * the accent color is laid over it: tinting that graphic would paint the whole
     * bar.
     *
     * <p>The light scheme's bar carries no colored line, so it is left alone.</p>
     */
    private static void tintActionBarLine(Activity activity, int accent) {
        if (AppThemes.getScheme(activity) != AppThemes.SCHEME_DARK) {
            return;
        }
        float density = activity.getResources().getDisplayMetrics().density;
        int[] ids = {frameworkId("action_bar"), frameworkId("action_bar_container")};
        for (int id : ids) {
            View bar = activity.findViewById(id);
            if (bar == null) {
                continue;
            }
            Drawable background = bar.getBackground();
            if (background != null) {
                bar.setBackgroundDrawable(lined(background, accent, density));
            }
            // Holo keeps the bar's own graphic - the one carrying the line - in a
            // field of the bar and paints it over the views inside the bar, so the
            // ordinary background is not the whole story.
            Drawable graphic = barGraphic(bar);
            if (graphic != null) {
                setBarGraphic(bar, lined(graphic, accent, density));
            }
        }
    }

    /** The given drawable with a line of the accent color along its bottom edge. */
    private static Drawable lined(Drawable background, int accent, float density) {
        return new LayerDrawable(new Drawable[]{
                background, new BottomLineDrawable(accent, density)});
    }

    /** The graphic the action bar paints itself with, kept in a field by Holo. */
    private static Drawable barGraphic(View bar) {
        try {
            Field field = bar.getClass().getDeclaredField("mBackground");
            field.setAccessible(true);
            return (Drawable) field.get(bar);
        } catch (Exception e) {
            return null;
        }
    }

    /** Replaces the graphic the action bar paints itself with. */
    private static void setBarGraphic(View bar, Drawable graphic) {
        try {
            Method method = bar.getClass().getMethod("setPrimaryBackground", Drawable.class);
            method.invoke(bar, graphic);
        } catch (Exception e) {
            try {
                Field field = bar.getClass().getDeclaredField("mBackground");
                field.setAccessible(true);
                field.set(bar, graphic);
                graphic.setCallback(bar);
            } catch (Exception ignored) {
                // the bar keeps its own line
            }
        }
    }

    /** A line along the bottom edge of whatever it is drawn into. */
    private static class BottomLineDrawable extends Drawable {

        private final Paint paint = new Paint();
        private final int thickness;

        BottomLineDrawable(int color, float density) {
            paint.setColor(color);
            thickness = Math.round(BAR_LINE_DP * density);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            canvas.drawRect(bounds.left, bounds.bottom - thickness, bounds.right,
                    bounds.bottom, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            paint.setColorFilter(filter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** The id of a framework resource, or 0 when this version does not have it. */
    private static int frameworkId(String name) {
        return Resources.getSystem().getIdentifier(name, "id", "android");
    }

    /**
     * Recolors the check or radio graphic of one button.
     *
     * <p>From API 21 the tint API does it. Below that the graphic the framework
     * draws - in Holo's blue - is recolored with a color filter: Holo has no color
     * attribute of its own, so there is nothing else that could be set. The filter
     * recolors the graphic but keeps its shape, so the checkmark of a check box
     * stays a hole in the colored square and shows the surface behind it.</p>
     */
    public static void tintButtonGraphic(CompoundButton button, int accent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setButtonTintList(ColorStateList.valueOf(accent));
        } else {
            tintGraphic(buttonDrawable(button), accent);
        }
    }

    /**
     * The check or radio graphic a button draws.
     *
     * <p>{@code getButtonDrawable} only exists from API 17, so before that the field
     * behind it is read directly. It has held the graphic since API 1, and nothing
     * else can reach it: without the field, Android 4.0 and 4.1 would have no way to
     * recolor the graphic the framework draws.</p>
     */
    private static Drawable buttonDrawable(CompoundButton button) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return button.getButtonDrawable();
        }
        try {
            Field field = CompoundButton.class.getDeclaredField("mButtonDrawable");
            field.setAccessible(true);
            return (Drawable) field.get(button);
        } catch (Exception e) {
            return null;
        }
    }

    /** Recolors a framework graphic in place, for the versions without a tint API. */
    private static void tintGraphic(Drawable graphic, int accent) {
        if (graphic != null) {
            graphic.setColorFilter(accent, PorterDuff.Mode.SRC_IN);
        }
    }

    /**
     * A checkmark centered on a square of the given size, drawn as a stroked path.
     *
     * <p>The path is authored in pixels over the size of the square, so no drawable
     * resource is needed: the app ships none, and a vector drawable would not work
     * on Android 4.x.</p>
     */
    public static Drawable checkmark(int color, int size, float stroke) {
        Path path = new Path();
        path.moveTo(size * 0.28f, size * 0.53f);
        path.lineTo(size * 0.44f, size * 0.68f);
        path.lineTo(size * 0.73f, size * 0.34f);

        ShapeDrawable mark = new ShapeDrawable(new PathShape(path, size, size));
        Paint paint = mark.getPaint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(color);
        paint.setAntiAlias(true);
        return mark;
    }

    /**
     * Recolors the check mark of a single-choice list. The mark is not one of a
     * text view's compound drawables, so - like a button's graphic - the field
     * behind it is read directly before API 23, where its tint API arrives.
     */
    private static void tintCheckMark(CheckedTextView check, int accent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            check.setCheckMarkTintList(ColorStateList.valueOf(accent));
            return;
        }
        try {
            Field field = CheckedTextView.class.getDeclaredField("mCheckMarkDrawable");
            field.setAccessible(true);
            tintGraphic((Drawable) field.get(check), accent);
        } catch (Exception e) {
            // The mark keeps the framework's color; the list still works.
        }
    }

    /**
     * Tints every check and radio button below the given view, along with the
     * links a text may contain: Material draws both from the accent color, Holo
     * from its own blue. A single-choice list marks its selection with a
     * CheckedTextView instead, which is tinted the same way.
     */
    private static void tintWidgets(View view, int accent) {
        if (view instanceof CompoundButton) {
            tintButtonGraphic((CompoundButton) view, accent);
        } else if (view instanceof CheckedTextView) {
            tintCheckMark((CheckedTextView) view, accent);
        } else if (view instanceof TextView) {
            ((TextView) view).setLinkTextColor(accent);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                tintWidgets(group.getChildAt(i), accent);
            }
        }
    }

    // ------------------------------------------------------------------- helpers

    /** Resolves a color resource. */
    public static int resolve(Context context, int colorRes) {
        return context.getResources().getColor(colorRes);
    }

    /**
     * Index of the stored color in a palette of color resources, or -1 when
     * nothing (or something unknown) is stored.
     *
     * <p>{@code darkPalette} is the same palette in its dark-scheme colors, or null
     * for a palette that has only one form (Holo). Both are searched, because a
     * choice is stored as the color that was on screen when it was made: a color
     * picked in one scheme is found again after switching to the other.</p>
     */
    public static int indexOfColor(Context context, int[] palette, int[] darkPalette, int color) {
        if (color == DEFAULT) {
            return -1;
        }
        for (int i = 0; i < palette.length; i++) {
            if (resolve(context, palette[i]) == color
                    || (darkPalette != null && resolve(context, darkPalette[i]) == color)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The color the given theme itself uses for the given framework attribute,
     * used to paint the "default" swatch of a color picker.
     *
     * <p>Resolving happens against a throw-away context that carries the plain
     * theme, so it reports the untouched color even when the running activity has
     * already been recolored. On platforms that do not define the attribute (the
     * Material colors before API 21) {@code fallback} is returned.</p>
     */
    public static int stockColor(Context context, int theme, int scheme, int attr, int fallback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return fallback;
        }
        Context base = new ContextThemeWrapper(context, AppThemes.styleRes(theme, scheme));
        TypedValue value = new TypedValue();
        if (base.getTheme().resolveAttribute(attr, value, true)
                && value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }
        return fallback;
    }
}
