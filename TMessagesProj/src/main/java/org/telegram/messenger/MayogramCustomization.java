/*
 * Mayogram: user-controlled appearance customization, reachable from
 * Settings -> Customise. Stored in the same SharedPreferences file as other
 * app-wide settings (MessagesController.getGlobalMainSettings()), so it
 * survives app updates like everything else there, and resets to defaults
 * on a fresh install (uninstall clears SharedPreferences) - by design, not
 * something that needs special handling.
 */
package org.telegram.messenger;

import android.content.SharedPreferences;

public class MayogramCustomization {

    public static final int TITLE_MODE_APP_NAME = 0;
    public static final int TITLE_MODE_FIRST_NAME = 1;
    public static final int TITLE_MODE_FULL_NAME = 2;

    private MayogramCustomization() {
    }

    private static SharedPreferences prefs() {
        return MessagesController.getGlobalMainSettings();
    }

    // --- Avatar shape: 100 = default circle, 0 = square -------------------

    public static int getAvatarRadiusPercent() {
        return prefs().getInt("mayo_avatar_radius_percent", 100);
    }

    public static void setAvatarRadiusPercent(int percent) {
        prefs().edit().putInt("mayo_avatar_radius_percent", percent).apply();
    }

    /** Scales a default (circle) avatar corner radius by the user's chosen shape. */
    public static int avatarRadius(int defaultRadiusPx) {
        int percent = getAvatarRadiusPercent();
        if (percent >= 100) {
            return defaultRadiusPx;
        }
        if (percent <= 0) {
            return 0;
        }
        return Math.round(defaultRadiusPx * (percent / 100f));
    }

    // --- Folder (chat filter) tabs bar: selected-pill color/radius --------

    /** 0 = use the app's theme color (default). */
    public static int getFolderBarColor() {
        return prefs().getInt("mayo_folder_bar_color", 0);
    }

    public static void setFolderBarColor(int color) {
        prefs().edit().putInt("mayo_folder_bar_color", color).apply();
    }

    public static float getFolderBarRadiusDp() {
        return prefs().getFloat("mayo_folder_bar_radius", 14f);
    }

    public static void setFolderBarRadiusDp(float dp) {
        prefs().edit().putFloat("mayo_folder_bar_radius", dp).apply();
    }

    // --- Home screen title: what shows next to the status emoji -----------

    public static int getTitleMode() {
        return prefs().getInt("mayo_title_mode", TITLE_MODE_FULL_NAME);
    }

    public static void setTitleMode(int mode) {
        prefs().edit().putInt("mayo_title_mode", mode).apply();
    }
}
