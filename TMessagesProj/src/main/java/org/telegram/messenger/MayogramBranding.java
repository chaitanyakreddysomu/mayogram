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

    private static Drawable verifiedDrawable;

    public static Drawable getVerifiedDrawable() {
        if (verifiedDrawable == null) {
            verifiedDrawable = ApplicationLoader.applicationContext
                    .getResources().getDrawable(R.drawable.mayogram_verified).mutate();
        }
        return verifiedDrawable;
    }

    private static Drawable channelIconDrawable;

    public static Drawable getChannelIconDrawable() {
        if (channelIconDrawable == null) {
            channelIconDrawable = ApplicationLoader.applicationContext
                    .getResources().getDrawable(R.drawable.mayogram_channel_icon).mutate();
        }
        return channelIconDrawable;
    }
}
