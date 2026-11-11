/*
 * Mayogram: scans every channel the current account owns and computes how
 * many unique people follow at least one of them - someone following three
 * owned channels only counts once. Network-heavy: pages through each
 * channel's full participant list via channels.getParticipants, so this
 * can take a while for large channels. Runs as long as the app process is
 * alive (not a true background service - if the app is killed mid-scan,
 * the scan stops and has to be started again).
 */
package org.telegram.messenger;

import android.app.PendingIntent;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class MayogramFollowersController {

    public interface ScanListener {
        void onProgress(int channelsDone, int channelsTotal, int uniqueSoFar);
        /** Fired once, right when a single owned channel finishes scanning. */
        void onChannelScanned(TLRPC.Chat chat, int channelFollowers, int channelsDone, int channelsTotal);
        void onComplete(int totalFollowers, int uniqueFollowers);
    }

    private static final int PAGE_LIMIT = 200;
    private static final int NOTIFICATION_ID = 93001;

    private static final HashMap<Integer, Boolean> scanningByAccount = new HashMap<>();
    private static final HashMap<Integer, ScanListener> listenerByAccount = new HashMap<>();

    public static boolean isScanning(int account) {
        return Boolean.TRUE.equals(scanningByAccount.get(account));
    }

    public static void setListener(int account, ScanListener listener) {
        listenerByAccount.put(account, listener);
    }

    public static void removeListener(int account, ScanListener listener) {
        if (listenerByAccount.get(account) == listener) {
            listenerByAccount.remove(account);
        }
    }

    /**
     * The in-memory user cache (MessagesController.getUser) is wiped on every
     * app restart, but these follower ids are rarely touched by any other
     * normal app activity, so after a restart they'd otherwise show as blank
     * "User 12345" placeholders until the next full rescan. This reloads just
     * the missing ones from local storage (already durable there since the
     * last scan) and calls onDone on the UI thread once they're ready to
     * display - no network request, no full rescan needed.
     */
    public static void ensureUsersCached(int account, List<Long> ids, Runnable onDone) {
        ArrayList<Long> missing = new ArrayList<>();
        MessagesController controller = MessagesController.getInstance(account);
        for (int i = 0; i < ids.size(); i++) {
            long id = ids.get(i);
            if (controller.getUser(id) == null) {
                missing.add(id);
            }
        }
        if (missing.isEmpty()) {
            onDone.run();
            return;
        }
        MessagesStorage.getInstance(account).getStorageQueue().postRunnable(() -> {
            ArrayList<TLRPC.User> loaded = new ArrayList<>();
            try {
                MessagesStorage.getInstance(account).getUsersInternal(missing, loaded);
            } catch (Exception e) {
                FileLog.e(e);
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (!loaded.isEmpty()) {
                    controller.putUsers(loaded, true);
                }
                onDone.run();
            });
        });
    }

    public static void startScan(int account) {
        if (isScanning(account)) {
            return;
        }
        scanningByAccount.put(account, true);

        // The local dialog list is paginated in from the server and may not be
        // fully synced yet (especially the archive folder, which most users
        // never scroll into) - an owned channel missing from it would silently
        // undercount. Make sure both the main and archive folders are fully
        // loaded before reading from them.
        ensureDialogsLoaded(account, 0, 0, () ->
            ensureDialogsLoaded(account, 1, 0, () ->
                beginScan(account)));
    }

    private static final int MAX_LOAD_DIALOGS_ATTEMPTS = 100;

    private static void ensureDialogsLoaded(int account, int folderId, int attempt, Runnable onDone) {
        MessagesController controller = MessagesController.getInstance(account);
        if (controller.isDialogsEndReached(folderId) || attempt >= MAX_LOAD_DIALOGS_ATTEMPTS) {
            onDone.run();
            return;
        }
        if (!controller.isLoadingDialogs(folderId)) {
            controller.loadDialogs(folderId, 0, 100, false);
        }
        AndroidUtilities.runOnUIThread(() -> ensureDialogsLoaded(account, folderId, attempt + 1, onDone), 300);
    }

    private static void beginScan(int account) {
        ArrayList<TLRPC.Chat> ownedChannels = new ArrayList<>();
        ArrayList<TLRPC.Dialog> dialogs = MessagesController.getInstance(account).getAllDialogs();
        for (int i = 0; i < dialogs.size(); i++) {
            TLRPC.Dialog dialog = dialogs.get(i);
            if (dialog != null && DialogObject.isChatDialog(dialog.id)) {
                TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialog.id);
                if (chat != null && chat.creator && ChatObject.isChannel(chat)) {
                    ownedChannels.add(chat);
                }
            }
        }

        ScanState state = new ScanState();
        state.account = account;
        state.channels = ownedChannels;
        scanNextChannel(state);
    }

    private static class ScanState {
        int account;
        List<TLRPC.Chat> channels;
        int channelIndex;
        int offset;
        int totalFollowers;
        int currentChannelFollowers;
        final HashSet<Long> uniqueFollowers = new HashSet<>();
    }

    private static void scanNextChannel(ScanState state) {
        if (state.channelIndex >= state.channels.size()) {
            finishScan(state);
            return;
        }
        state.offset = 0;
        fetchParticipantsPage(state);
    }

    private static void fetchParticipantsPage(ScanState state) {
        TLRPC.Chat chat = state.channels.get(state.channelIndex);
        TLRPC.TL_channels_getParticipants req = new TLRPC.TL_channels_getParticipants();
        req.channel = MessagesController.getInstance(state.account).getInputChannel(chat.id);
        req.filter = new TLRPC.TL_channelParticipantsRecent();
        req.offset = state.offset;
        req.limit = PAGE_LIMIT;
        ConnectionsManager.getInstance(state.account).sendRequest(req, (response, error) -> {
            if (response instanceof TLRPC.TL_channels_channelParticipants) {
                TLRPC.TL_channels_channelParticipants res = (TLRPC.TL_channels_channelParticipants) response;
                if (state.offset == 0) {
                    state.totalFollowers += res.count;
                    state.currentChannelFollowers = res.count;
                }
                // Cache the real user objects (name, avatar) the server already sent
                // us here, so the followers list can show them - no extra lookups.
                // Also persisted to local storage (not just the in-memory cache),
                // since the in-memory cache is wiped on every app restart but these
                // users are rarely touched by any other normal app activity.
                MessagesController.getInstance(state.account).putUsers(res.users, false);
                MessagesStorage.getInstance(state.account).putUsersAndChats(res.users, null, false, true);
                for (int i = 0; i < res.users.size(); i++) {
                    state.uniqueFollowers.add(res.users.get(i).id);
                }
                notifyProgress(state);
                if (res.participants.size() >= PAGE_LIMIT) {
                    state.offset += PAGE_LIMIT;
                    fetchParticipantsPage(state);
                } else {
                    notifyChannelScanned(state, chat, state.currentChannelFollowers);
                    state.channelIndex++;
                    scanNextChannel(state);
                }
            } else {
                // Couldn't read this channel's participant list (e.g. temporary
                // server error) - skip it rather than aborting the whole scan.
                notifyChannelScanned(state, chat, state.currentChannelFollowers);
                state.channelIndex++;
                scanNextChannel(state);
            }
        });
    }

    private static void notifyProgress(ScanState state) {
        ScanListener listener = listenerByAccount.get(state.account);
        if (listener != null) {
            int done = state.channelIndex;
            int total = state.channels.size();
            int unique = state.uniqueFollowers.size();
            AndroidUtilities.runOnUIThread(() -> listener.onProgress(done, total, unique));
        }
    }

    private static void notifyChannelScanned(ScanState state, TLRPC.Chat chat, int channelFollowers) {
        ScanListener listener = listenerByAccount.get(state.account);
        if (listener != null) {
            int done = state.channelIndex + 1;
            int total = state.channels.size();
            AndroidUtilities.runOnUIThread(() -> listener.onChannelScanned(chat, channelFollowers, done, total));
        }
    }

    private static void finishScan(ScanState state) {
        scanningByAccount.put(state.account, false);
        MayogramCustomization.saveFollowersResult(state.account, state.totalFollowers, state.uniqueFollowers);

        ScanListener listener = listenerByAccount.get(state.account);
        if (listener != null) {
            int total = state.totalFollowers;
            int unique = state.uniqueFollowers.size();
            AndroidUtilities.runOnUIThread(() -> listener.onComplete(total, unique));
        } else {
            showCompletionNotification(state.totalFollowers, state.uniqueFollowers.size());
        }
    }

    private static void showCompletionNotification(int totalFollowers, int uniqueFollowers) {
        NotificationsController.checkOtherNotificationsChannel();
        Intent intent = new Intent(ApplicationLoader.applicationContext, LaunchActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(ApplicationLoader.applicationContext, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ApplicationLoader.applicationContext, NotificationsController.OTHER_NOTIFICATIONS_CHANNEL)
                .setSmallIcon(R.drawable.notification)
                .setContentTitle("Unique Followers scan complete")
                .setContentText(uniqueFollowers + " unique followers across your channels")
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);
        try {
            NotificationManagerCompat.from(ApplicationLoader.applicationContext).notify(NOTIFICATION_ID, builder.build());
        } catch (SecurityException ignore) {
            // POST_NOTIFICATIONS not granted - the scan result is still saved
            // and visible next time the Unique Followers screen is opened.
        }
    }
}
