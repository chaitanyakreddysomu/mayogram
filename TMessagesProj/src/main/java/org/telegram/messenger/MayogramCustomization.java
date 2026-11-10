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

    // --- General: message time format --------------------------------------

    public static boolean isTimeWithSecondsEnabled() {
        return prefs().getBoolean("mayo_time_with_seconds", false);
    }

    public static void setTimeWithSecondsEnabled(boolean enabled) {
        prefs().edit().putBoolean("mayo_time_with_seconds", enabled).apply();
    }

    // --- Local Premium: device-only cosmetic toggle, never touches the real
    // account's premium status or anything sent to the server --------------

    public static boolean isLocalPremiumEnabled() {
        return prefs().getBoolean("mayo_local_premium", false);
    }

    public static void setLocalPremiumEnabled(boolean enabled) {
        prefs().edit().putBoolean("mayo_local_premium", enabled).apply();
    }

    // --- Menu item visibility: generic per-key toggle, default shown. Keys
    // the user can't turn off (e.g. Mute/Search/Leave) never check this -
    // they're just always added, same as before this feature existed. -----

    public static final String MENU_HOME_GHOST_MODE = "home_ghost_mode";
    public static final String MENU_HOME_NEW_GROUP = "home_new_group";
    public static final String MENU_HOME_SCHEDULED = "home_scheduled";

    public static final String MENU_CHAT_ADMIN_RESTRICT = "chat_admin_restrict";
    public static final String MENU_CHAT_ADMIN_BOOST = "chat_admin_boost";
    public static final String MENU_CHAT_ADMIN_GO_TO_TOP = "chat_admin_go_to_top";
    public static final String MENU_CHAT_ADMIN_RECENT_ACTIONS = "chat_admin_recent_actions";
    public static final String MENU_CHAT_ADMIN_MEMBERS = "chat_admin_members";
    public static final String MENU_CHAT_ADMIN_INVITE_LINKS = "chat_admin_invite_links";

    public static final String MENU_CHAT_USER_GO_TO_TOP = "chat_user_go_to_top";
    public static final String MENU_CHAT_USER_REPORT = "chat_user_report";

    public static final String MENU_MSG_ADMIN_REPLY = "msg_admin_reply";
    public static final String MENU_MSG_ADMIN_COPY_LINK = "msg_admin_copy_link";
    public static final String MENU_MSG_ADMIN_SHOW_OWNER = "msg_admin_show_owner";
    public static final String MENU_MSG_ADMIN_SAVE_MUSIC = "msg_admin_save_music";
    public static final String MENU_MSG_ADMIN_EDIT = "msg_admin_edit";

    public static final String MENU_MSG_USER_REPLY = "msg_user_reply";
    public static final String MENU_MSG_USER_COPY_LINK = "msg_user_copy_link";
    public static final String MENU_MSG_USER_SHOW_OWNER = "msg_user_show_owner";
    public static final String MENU_MSG_USER_SAVE_MUSIC = "msg_user_save_music";
    public static final String MENU_MSG_USER_EDIT = "msg_user_edit";

    public static boolean isMenuItemVisible(String key) {
        return prefs().getBoolean("mayo_menu_" + key, true);
    }

    public static void setMenuItemVisible(String key, boolean visible) {
        prefs().edit().putBoolean("mayo_menu_" + key, visible).apply();
    }

    private static final String[] ALL_MENU_KEYS = {
            MENU_HOME_GHOST_MODE, MENU_HOME_NEW_GROUP, MENU_HOME_SCHEDULED,
            MENU_CHAT_ADMIN_RESTRICT, MENU_CHAT_ADMIN_BOOST, MENU_CHAT_ADMIN_GO_TO_TOP,
            MENU_CHAT_ADMIN_RECENT_ACTIONS, MENU_CHAT_ADMIN_MEMBERS, MENU_CHAT_ADMIN_INVITE_LINKS,
            MENU_CHAT_USER_GO_TO_TOP, MENU_CHAT_USER_REPORT,
            MENU_MSG_ADMIN_REPLY, MENU_MSG_ADMIN_COPY_LINK, MENU_MSG_ADMIN_SHOW_OWNER, MENU_MSG_ADMIN_SAVE_MUSIC, MENU_MSG_ADMIN_EDIT,
            MENU_MSG_USER_REPLY, MENU_MSG_USER_COPY_LINK, MENU_MSG_USER_SHOW_OWNER, MENU_MSG_USER_SAVE_MUSIC, MENU_MSG_USER_EDIT,
    };

    /** Puts every toggleable menu item back to shown (its default). */
    public static void resetAllMenuVisibility() {
        SharedPreferences.Editor editor = prefs().edit();
        for (String key : ALL_MENU_KEYS) {
            editor.remove("mayo_menu_" + key);
        }
        editor.apply();
    }

    /** Puts every Appearance setting (folder bar, avatars, home title) back to its default. */
    public static void resetAppearanceDefaults() {
        prefs().edit()
                .remove("mayo_avatar_radius_percent")
                .remove("mayo_folder_bar_color")
                .remove("mayo_folder_bar_radius")
                .remove("mayo_title_mode")
                .apply();
    }

    // --- Unique Followers scan result: per-account (unlike the settings
    // above), since different logged-in accounts own different channels. ---

    public static void saveFollowersResult(int account, int total, java.util.Set<Long> uniqueIds) {
        StringBuilder sb = new StringBuilder();
        for (long id : uniqueIds) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        MessagesController.getMainSettings(account).edit()
                .putInt("mayo_followers_total", total)
                .putInt("mayo_followers_unique", uniqueIds.size())
                .putLong("mayo_followers_last_scan", System.currentTimeMillis())
                .putString("mayo_followers_ids", sb.toString())
                .apply();
    }

    public static int getFollowersTotal(int account) {
        return MessagesController.getMainSettings(account).getInt("mayo_followers_total", 0);
    }

    public static int getFollowersUnique(int account) {
        return MessagesController.getMainSettings(account).getInt("mayo_followers_unique", 0);
    }

    public static long getFollowersLastScan(int account) {
        return MessagesController.getMainSettings(account).getLong("mayo_followers_last_scan", 0);
    }

    public static java.util.ArrayList<Long> getFollowersIds(int account) {
        String raw = MessagesController.getMainSettings(account).getString("mayo_followers_ids", "");
        java.util.ArrayList<Long> result = new java.util.ArrayList<>();
        if (!raw.isEmpty()) {
            for (String part : raw.split(",")) {
                try {
                    result.add(Long.parseLong(part));
                } catch (NumberFormatException ignore) {
                }
            }
        }
        return result;
    }
}
