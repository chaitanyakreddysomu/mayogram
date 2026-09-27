/*
 * Mayogram local-only channel appearance.
 *
 * Deliberately does not touch anything server-synced. Telegram's own channel
 * name color / reply color (ChannelColorActivity) and official channel
 * wallpaper (ChannelWallpaperActivity) are gated by chat.level / boostsStatus
 * on the server - that gate is real, not a client-side lock, because those
 * features propagate to every member's client. This class never reads or
 * writes chat.level, boostsStatus, or any TL_channels.* method; it is a
 * per-device, per-chat preference that changes nothing for anyone else.
 *
 * Stored in SharedPreferences (not the download manager's SQLite file - this
 * is small, simple key/value data with no querying needs).
 */

package org.telegram.messenger.appearance;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

public class ChannelAppearanceSettings {

    private static final String PREFS = "mayo_channel_appearance";

    public static final int[] SWATCH_COLORS = {
            0xFF3B82F6, // Blue
            0xFF22C55E, // Green
            0xFFF97316, // Orange
            0xFFEF4444, // Red
            0xFFA855F7, // Purple
    };
    /** Selected swatch index, or this value when a custom color was picked instead. */
    public static final int SWATCH_CUSTOM = -1;
    /** No color override selected (Telegram's own theme/default color renders as usual). */
    public static final int SWATCH_NONE = -2;

    public long dialogId;
    public int swatchIndex = SWATCH_NONE;
    public int customColor;
    public boolean replyLogoEnabled = true;
    /** Path to a copy of the user's chosen image under app-private storage, or null. */
    public String wallpaperPath;

    private ChannelAppearanceSettings(long dialogId) {
        this.dialogId = dialogId;
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(int account, long dialogId, String field) {
        return account + "_" + dialogId + "_" + field;
    }

    public static ChannelAppearanceSettings load(int account, long dialogId) {
        SharedPreferences p = prefs();
        ChannelAppearanceSettings s = new ChannelAppearanceSettings(dialogId);
        s.swatchIndex = p.getInt(key(account, dialogId, "swatch"), SWATCH_NONE);
        s.customColor = p.getInt(key(account, dialogId, "custom_color"), 0xFF3B82F6);
        s.replyLogoEnabled = p.getBoolean(key(account, dialogId, "reply_logo"), true);
        s.wallpaperPath = p.getString(key(account, dialogId, "wallpaper_path"), null);
        return s;
    }

    public void save(int account) {
        prefs().edit()
                .putInt(key(account, dialogId, "swatch"), swatchIndex)
                .putInt(key(account, dialogId, "custom_color"), customColor)
                .putBoolean(key(account, dialogId, "reply_logo"), replyLogoEnabled)
                .putString(key(account, dialogId, "wallpaper_path"), wallpaperPath)
                .apply();
    }

    public boolean hasAnyOverride() {
        return swatchIndex != SWATCH_NONE || wallpaperPath != null;
    }

    public int resolvedColor() {
        return swatchIndex == SWATCH_CUSTOM ? customColor
                : (swatchIndex >= 0 && swatchIndex < SWATCH_COLORS.length ? SWATCH_COLORS[swatchIndex] : 0);
    }
}
