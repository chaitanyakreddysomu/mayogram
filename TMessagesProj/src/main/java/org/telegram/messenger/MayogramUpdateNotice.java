/*
 * Mayogram: when an already logged-in account opens the app for the first
 * time after an update (not a fresh login, not every app open), best-effort
 * navigate straight into @mayogram's current pinned message. Fails silently
 * on any error - this must never block or disrupt normal startup.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Bundle;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.LaunchActivity;

public class MayogramUpdateNotice {

    private static final String CHANNEL_USERNAME = "mayogram";
    private static final String PREFS = "mayogram_update_notice";
    private static final String KEY_LAST_SEEN_VERSION = "last_seen_version";

    private MayogramUpdateNotice() {
    }

    /**
     * Call once per cold start, right after the chat list is shown for an
     * already-activated account. Internally no-ops unless the installed
     * app version has changed since the last time this ran.
     */
    public static void maybeShowOnUpdate(int account, LaunchActivity launchActivity) {
        // Mirrors ApplicationLoader's own versionCode reading (see its
        // FileLog.d("buildVersion = ...") block): the installed APK's
        // versionCode is (gradle.properties APP_VERSION_CODE * 10 +
        // per-ABI digit), so dividing by 10 gives the comparable app
        // version regardless of which ABI split got installed/updated.
        int currentVersion;
        try {
            PackageInfo info = ApplicationLoader.applicationContext.getPackageManager()
                    .getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            currentVersion = info.versionCode / 10;
        } catch (Exception e) {
            FileLog.e(e);
            return;
        }

        SharedPreferences prefs = ApplicationLoader.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int lastSeenVersion = prefs.getInt(KEY_LAST_SEEN_VERSION, 0);
        prefs.edit().putInt(KEY_LAST_SEEN_VERSION, currentVersion).apply();

        if (lastSeenVersion == 0 || lastSeenVersion >= currentVersion) {
            // First launch ever on this install (nothing to compare against)
            // or no version change - not an update, don't navigate.
            return;
        }

        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = CHANNEL_USERNAME;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            if (error != null || !(response instanceof TLRPC.TL_contacts_resolvedPeer)) {
                return;
            }
            TLRPC.TL_contacts_resolvedPeer resolved = (TLRPC.TL_contacts_resolvedPeer) response;
            if (resolved.chats.isEmpty()) {
                return;
            }
            final TLRPC.Chat chat = resolved.chats.get(0);
            AndroidUtilities.runOnUIThread(() -> {
                MessagesController controller = MessagesController.getInstance(account);
                controller.putUsers(resolved.users, false);
                controller.putChats(resolved.chats, false);
                fetchPinnedAndOpen(account, chat, launchActivity);
            });
        });
    }

    private static void fetchPinnedAndOpen(int account, TLRPC.Chat chat, LaunchActivity launchActivity) {
        TLRPC.TL_channels_getFullChannel req = new TLRPC.TL_channels_getFullChannel();
        req.channel = MessagesController.getInputChannel(chat);
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            if (error != null || !(response instanceof TLRPC.TL_messages_chatFull)) {
                return;
            }
            TLRPC.TL_messages_chatFull chatFull = (TLRPC.TL_messages_chatFull) response;
            int pinnedMsgId = chatFull.full_chat != null ? chatFull.full_chat.pinned_msg_id : 0;
            if (pinnedMsgId == 0) {
                return;
            }
            AndroidUtilities.runOnUIThread(() -> {
                MessagesController controller = MessagesController.getInstance(account);
                controller.putUsers(chatFull.users, false);
                controller.putChats(chatFull.chats, false);
                try {
                    Bundle args = new Bundle();
                    args.putLong("chat_id", chat.id);
                    args.putInt("message_id", pinnedMsgId);
                    ChatActivity chatActivity = new ChatActivity(args);
                    launchActivity.presentFragment(chatActivity, false, false);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
        });
    }
}
