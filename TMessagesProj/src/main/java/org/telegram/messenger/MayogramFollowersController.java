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
        /** Fired once channel discovery finishes, right before per-channel scanning starts. */
        void onChannelsDiscovered(int channelCount);
        void onProgress(int channelsDone, int channelsTotal, int uniqueSoFar);
        /** Fired once, right when a single owned channel finishes scanning. */
        void onChannelScanned(TLRPC.Chat chat, int channelFollowers, int channelsDone, int channelsTotal);
        void onComplete(int totalFollowers, int uniqueFollowers);
    }

    private static final int PAGE_LIMIT = 200;
    private static final int NOTIFICATION_ID = 93001;

    private static final HashMap<Integer, Boolean> scanningByAccount = new HashMap<>();
    private static final HashMap<Integer, Boolean> discoveringByAccount = new HashMap<>();
    private static final HashMap<Integer, ScanListener> listenerByAccount = new HashMap<>();

    public static boolean isScanning(int account) {
        return Boolean.TRUE.equals(scanningByAccount.get(account));
    }

    /** True only during the first pass that finds every channel this account owns, before the per-channel follower fetch starts. */
    public static boolean isDiscoveringChannels(int account) {
        return Boolean.TRUE.equals(discoveringByAccount.get(account));
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
        discoveringByAccount.put(account, true);

        // The local dialog list is paginated in from the server and may not be
        // fully synced yet (especially the archive folder, which most users
        // never scroll into) - an owned channel missing from it would silently
        // undercount, and a stale partial list is exactly why the "owned
        // channels" count could come out different from one scan to the
        // next. Make sure both the main and archive folders are FULLY
        // loaded (not just "loaded enough") before reading from them.
        ensureAllDialogsLoaded(account, () -> beginScan(account));
    }

    /** Forces both the main and archive folders to fully paginate in before trusting getAllDialogs(). Reused anywhere a feature needs the complete dialog list, not just whatever's loaded so far. */
    public static void ensureAllDialogsLoaded(int account, Runnable onDone) {
        ensureDialogsLoaded(account, 0, 0, () ->
            ensureDialogsLoaded(account, 1, 0, onDone));
    }

    private static final int MAX_LOAD_DIALOGS_ATTEMPTS = 400;

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
        // The locally cached creator flag on a dialog's chat object isn't
        // always fresh (it's whatever was last echoed to this device) - that
        // staleness is the likely reason the owned-channel count has come
        // out different between runs. Every channel dialog is treated as a
        // candidate here, and ownership is decided only from a fresh
        // server answer (channels.getChannels), never from the local cache.
        ArrayList<TLRPC.Chat> candidateChannels = new ArrayList<>();
        ArrayList<TLRPC.Dialog> dialogs = MessagesController.getInstance(account).getAllDialogs();
        for (int i = 0; i < dialogs.size(); i++) {
            TLRPC.Dialog dialog = dialogs.get(i);
            if (dialog != null && DialogObject.isChatDialog(dialog.id)) {
                TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialog.id);
                if (chat != null && ChatObject.isChannel(chat)) {
                    candidateChannels.add(chat);
                }
            }
        }
        verifyOwnedChannels(account, candidateChannels, 0, new ArrayList<>());
    }

    private static final int VERIFY_OWNERSHIP_BATCH_SIZE = 100;

    private static void verifyOwnedChannels(int account, List<TLRPC.Chat> candidates, int offset, ArrayList<TLRPC.Chat> ownedSoFar) {
        if (offset >= candidates.size()) {
            finishDiscovery(account, ownedSoFar);
            return;
        }
        int end = Math.min(offset + VERIFY_OWNERSHIP_BATCH_SIZE, candidates.size());
        TLRPC.TL_channels_getChannels req = new TLRPC.TL_channels_getChannels();
        for (int i = offset; i < end; i++) {
            req.id.add(MessagesController.getInstance(account).getInputChannel(candidates.get(i).id));
        }
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            if (response instanceof TLRPC.TL_messages_chats) {
                TLRPC.TL_messages_chats res = (TLRPC.TL_messages_chats) response;
                MessagesController.getInstance(account).putChats(res.chats, false);
                for (int i = 0; i < res.chats.size(); i++) {
                    TLRPC.Chat chat = res.chats.get(i);
                    if (chat != null && chat.creator) {
                        ownedSoFar.add(chat);
                    }
                }
            }
            AndroidUtilities.runOnUIThread(() -> verifyOwnedChannels(account, candidates, end, ownedSoFar));
        });
    }

    private static void finishDiscovery(int account, ArrayList<TLRPC.Chat> ownedChannels) {
        discoveringByAccount.put(account, false);
        ScanListener listener = listenerByAccount.get(account);
        if (listener != null) {
            int count = ownedChannels.size();
            AndroidUtilities.runOnUIThread(() -> listener.onChannelsDiscovered(count));
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
