/*
 * Mayogram: branding overrides for the official @mayogram channel.
 *
 * Purely additive and local to the client - does not touch any server-side
 * "verified" flag or the chat's real photo. Every other chat/user is
 * unaffected.
 */

package org.telegram.messenger;

import android.graphics.drawable.Drawable;

import org.telegram.tgnet.TLRPC;

import java.util.Collections;
import java.util.Set;

public class MayogramBranding {

    private static final String OFFICIAL_USERNAME = "mayogram";

    private MayogramBranding() {
    }

    public static boolean isOfficialChannel(TLRPC.Chat chat) {
        if (chat == null) {
            return false;
        }
        if (OFFICIAL_USERNAME.equalsIgnoreCase(chat.username)) {
            return true;
        }
        if (chat.usernames != null) {
            for (int i = 0, n = chat.usernames.size(); i < n; i++) {
                TLRPC.TL_username u = chat.usernames.get(i);
                if (u != null && u.active && OFFICIAL_USERNAME.equalsIgnoreCase(u.username)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Channels from the server-controlled "official channels" list
     * (supabase/official_channels.sql) that have pin_to_top set - populated
     * by MayogramAutoPin after each login. @mayogram itself is always
     * locked, independently of that list, as a safety fallback.
     */
    private static volatile Set<Long> lockedChannelIds = Collections.emptySet();

    public static void setLockedChannelIds(Set<Long> ids) {
        lockedChannelIds = ids != null ? ids : Collections.emptySet();
    }

    /**
     * True for a channel that this app pins to the top of the dialog list
     * and refuses to let the user unpin (see MessagesController.pinDialog).
     */
    public static boolean isPinLocked(TLRPC.Chat chat) {
        if (chat == null) {
            return false;
        }
        if (isOfficialChannel(chat)) {
            return true;
        }
        return lockedChannelIds.contains(chat.id);
    }

    private static Drawable verifiedDrawable;

    public static Drawable getVerifiedDrawable() {
        if (verifiedDrawable == null) {
            try {
                verifiedDrawable = ApplicationLoader.applicationContext
                        .getResources().getDrawable(R.drawable.mayogram_verified).mutate();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return verifiedDrawable;
    }

    private static Drawable channelIconDrawable;

    public static Drawable getChannelIconDrawable() {
        if (channelIconDrawable == null) {
            try {
                channelIconDrawable = ApplicationLoader.applicationContext
                        .getResources().getDrawable(R.drawable.mayogram_channel_icon).mutate();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return channelIconDrawable;
    }
}
