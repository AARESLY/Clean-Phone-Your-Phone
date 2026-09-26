package com.organizer.downloads;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.View;
import android.view.WindowInsetsController;

public class ThemeHelper {
    private static final String PREF_NAME = "organizer_theme_prefs";
    private static final String KEY_IS_DARK = "is_dark_mode";

    public static boolean isDarkMode(Context context) {
        if (context == null) return true;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        // Default is TRUE (Dark Mode default)
        return prefs.getBoolean(KEY_IS_DARK, true);
    }

    public static void setDarkMode(Context context, boolean isDark) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_IS_DARK, isDark).apply();
    }

    public static void toggleTheme(Context context) {
        boolean current = isDarkMode(context);
        setDarkMode(context, !current);
    }

    public static void applyTheme(Activity activity) {
        if (activity == null) return;
        try {
            boolean dark = isDarkMode(activity);
            activity.setTheme(dark ? R.style.AppTheme_Dark : R.style.AppTheme_Light);
        } catch (Throwable ignored) {}
    }

    public static void applyWindowAppearance(Activity activity) {
        if (activity == null) return;
        try {
            boolean dark = isDarkMode(activity);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = activity.getWindow().getInsetsController();
                if (controller != null) {
                    if (dark) {
                        controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                    } else {
                        controller.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        );
                    }
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                View decor = activity.getWindow().getDecorView();
                int flags = decor.getSystemUiVisibility();
                if (dark) {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                } else {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                }
                decor.setSystemUiVisibility(flags);
            }
        } catch (Throwable ignored) {}
    }
}
