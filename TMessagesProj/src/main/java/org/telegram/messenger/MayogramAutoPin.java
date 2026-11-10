/*
 * Mayogram: after a successful login, auto-join (and optionally pin to the
 * very top, unpinnably) every channel in the server-controlled "official
 * channels" list - see supabase/official_channels.sql. The list itself can
 * be changed from Supabase at any time (add/remove channels, toggle
 * pinning) without an app update. Every official channel is also added to a
 * local "mayogram" folder (chat filter), created automatically if it
 * doesn't exist yet.
 *
 * Runs once per login (see the call site in LoginActivity's onAuthSuccess),
 * best-effort throughout - any failure (no network, Supabase not
 * configured, a channel missing or already left) is silently ignored and
 * never blocks or disrupts login. If the Supabase list can't be fetched at
 * all, falls back to the single hardcoded @mayogram channel so behavior
 * degrades gracefully instead of doing nothing.
 */

package org.telegram.messenger;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MayogramAutoPin {

    private static final String FALLBACK_CHANNEL_USERNAME = "mayogram";

    private static final class OfficialChannel {
        final String username;
        final boolean pinToTop;
        OfficialChannel(String username, boolean pinToTop) {
            this.username = username;
            this.pinToTop = pinToTop;
        }
    }

    private MayogramAutoPin() {
    }

    public static void autoJoinOfficialChannels(int account) {
        Utilities.globalQueue.postRunnable(() -> {
            List<OfficialChannel> channels = fetchOfficialChannelsBlocking();
            boolean fetchedFromSupabase = channels != null && !channels.isEmpty();
            if (!fetchedFromSupabase) {
                // Supabase not configured, unreachable, or the list is empty -
                // fall back to the one channel this app has always shipped with.
                // Not a real "current list", so don't run removal reconciliation
                // below off the back of this fallback - that would wipe every
                // other official channel out of the folder on a transient
                // network failure.
                channels = new ArrayList<>();
                channels.add(new OfficialChannel(FALLBACK_CHANNEL_USERNAME, true));
            }
            final List<OfficialChannel> finalChannels = channels;
            final boolean finalFetchedFromSupabase = fetchedFromSupabase;
            AndroidUtilities.runOnUIThread(() -> {
                Set<Long> lockedIds = new HashSet<>();
                Set<Long> resolvedIds = finalFetchedFromSupabase ? java.util.Collections.synchronizedSet(new HashSet<>()) : null;
                java.util.concurrent.atomic.AtomicInteger remaining = new java.util.concurrent.atomic.AtomicInteger(finalChannels.size());
                // Mayogram: firing contacts.resolveUsername for every channel in
                // the same tick trips Telegram's flood control on rapid repeated
                // username resolves, so only the first request (always @mayogram,
                // sort_order 0) succeeds and the rest fail - this was previously
                // invisible because errors were swallowed without logging.
                // Staggering them a few hundred ms apart avoids the flood wait.
                for (int i = 0; i < finalChannels.size(); i++) {
                    OfficialChannel channel = finalChannels.get(i);
                    long delay = i * 400L;
                    Runnable task = () -> resolveJoinAndMaybePin(account, channel, lockedIds, resolvedIds, remaining);
                    if (delay == 0) {
                        task.run();
                    } else {
                        AndroidUtilities.runOnUIThread(task, delay);
                    }
                }
            });
        });
    }

    private static List<OfficialChannel> fetchOfficialChannelsBlocking() {
        if (TextUtils.isEmpty(BuildConfig.SUPABASE_URL) || TextUtils.isEmpty(BuildConfig.SUPABASE_ANON_KEY)) {
            return null;
        }
        HttpURLConnection connection = null;
        try {
            URL url = new URL(BuildConfig.SUPABASE_URL + "/rest/v1/rpc/get_official_channels");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY);
            connection.setRequestProperty("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY);

            OutputStream out = connection.getOutputStream();
            out.write("{}".getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                return null;
            }
            JSONArray array = new JSONArray(readAll(connection.getInputStream()));
            List<OfficialChannel> result = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject row = array.getJSONObject(i);
                String username = row.optString("username", null);
                if (username == null || username.trim().length() == 0) {
                    continue;
                }
                result.add(new OfficialChannel(username.trim(), row.optBoolean("pin_to_top", true)));
            }
            return result;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readAll(InputStream stream) throws Exception {
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        reader.close();
        return builder.toString();
    }

    private static void resolveJoinAndMaybePin(int account, OfficialChannel channel, Set<Long> lockedIds, Set<Long> resolvedIds, java.util.concurrent.atomic.AtomicInteger remaining) {
        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = channel.username;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            if (error != null) {
                FileLog.e("MayogramAutoPin: resolveUsername failed for @" + channel.username + ": " + error.code + " " + error.text);
                onResolveAttemptFinished(account, resolvedIds, remaining);
                return;
            }
            if (!(response instanceof TLRPC.TL_contacts_resolvedPeer)) {
                onResolveAttemptFinished(account, resolvedIds, remaining);
                return;
            }
            TLRPC.TL_contacts_resolvedPeer resolved = (TLRPC.TL_contacts_resolvedPeer) response;
            if (resolved.chats.isEmpty()) {
                FileLog.e("MayogramAutoPin: @" + channel.username + " resolved to no chat (bot/user username?)");
                onResolveAttemptFinished(account, resolvedIds, remaining);
                return;
            }
            final TLRPC.Chat chat = resolved.chats.get(0);
            AndroidUtilities.runOnUIThread(() -> {
                MessagesController controller = MessagesController.getInstance(account);
                controller.putUsers(resolved.users, false);
                controller.putChats(resolved.chats, false);
                if (channel.pinToTop) {
                    synchronized (lockedIds) {
                        lockedIds.add(chat.id);
                        // Re-publish the locked set on every addition - channels
                        // resolve asynchronously and out of order, so there is no
                        // single "all done" point to publish once from.
                        MayogramBranding.setLockedChannelIds(new HashSet<>(lockedIds));
                    }
                }
                if (resolvedIds != null) {
                    resolvedIds.add(-chat.id);
                }
                joinIfNeededThenPin(account, chat, channel.pinToTop);
                onResolveAttemptFinished(account, resolvedIds, remaining);
            });
        });
    }

    private static void onResolveAttemptFinished(int account, Set<Long> resolvedIds, java.util.concurrent.atomic.AtomicInteger remaining) {
        if (remaining.decrementAndGet() == 0 && resolvedIds != null) {
            reconcileRemovedOfficialChannels(account, new HashSet<>(resolvedIds));
        }
    }

    // ---------------------------------------------------------------------
    // Removing a channel from the Supabase official_channels list should pull
    // it out of the local "mayogram" folder - but must NEVER leave the actual
    // channel. The user stays a member; it just stops being auto-organized.
    // ---------------------------------------------------------------------

    private static final String PREF_KEY_KNOWN_OFFICIAL_IDS = "mayo_official_channel_ids";

    private static void reconcileRemovedOfficialChannels(int account, Set<Long> currentOfficialDialogIds) {
        android.content.SharedPreferences prefs = MessagesController.getMainSettings(account);
        Set<Long> previousIds = new HashSet<>();
        String raw = prefs.getString(PREF_KEY_KNOWN_OFFICIAL_IDS, "");
        if (!raw.isEmpty()) {
            for (String part : raw.split(",")) {
                try {
                    previousIds.add(Long.parseLong(part));
                } catch (NumberFormatException ignore) {
                }
            }
        }

        Set<Long> removed = new HashSet<>(previousIds);
        removed.removeAll(currentOfficialDialogIds);
        for (Long dialogId : removed) {
            removeFromMayogramFolder(account, dialogId);
        }

        StringBuilder sb = new StringBuilder();
        for (Long id : currentOfficialDialogIds) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        prefs.edit().putString(PREF_KEY_KNOWN_OFFICIAL_IDS, sb.toString()).apply();
    }

    private static void joinIfNeededThenPin(int account, TLRPC.Chat chat, boolean pinToTop) {
        long dialogId = -chat.id;
        MessagesController controller = MessagesController.getInstance(account);
        if (controller.getDialog(dialogId) != null) {
            addToMayogramFolder(account, dialogId);
            if (pinToTop) {
                pinAndSync(account, dialogId, 0);
            }
            return;
        }
        TLRPC.TL_channels_joinChannel req = new TLRPC.TL_channels_joinChannel();
        req.channel = MessagesController.getInputChannel(chat);
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            if (error != null) {
                FileLog.e("MayogramAutoPin: joinChannel failed for chat " + chat.id + " (@" + chat.username + "): " + error.code + " " + error.text);
                return;
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (response instanceof TLRPC.Updates) {
                    controller.processUpdates((TLRPC.Updates) response, false);
                }
                addToMayogramFolder(account, dialogId);
                if (pinToTop) {
                    pinAndSync(account, dialogId, 0);
                }
            });
        });
    }

    // ---------------------------------------------------------------------
    // "mayogram" folder: every official channel also gets added to a local
    // chat filter named "mayogram", created on first use. Channels resolve
    // one at a time (staggered above), so additions are batched into one
    // debounced TL_messages_updateDialogFilter call per login instead of one
    // per channel.
    // ---------------------------------------------------------------------

    private static final String FOLDER_NAME = "mayogram";
    private static final Map<Integer, Set<Long>> pendingFolderDialogs = new ConcurrentHashMap<>();
    private static final Map<Integer, Set<Long>> pendingFolderRemovals = new ConcurrentHashMap<>();
    private static final Map<Integer, Runnable> pendingFolderSync = new ConcurrentHashMap<>();

    private static void addToMayogramFolder(int account, long dialogId) {
        Set<Long> pending = pendingFolderDialogs.computeIfAbsent(account, a -> new HashSet<>());
        synchronized (pending) {
            pending.add(dialogId);
        }
        scheduleMayogramFolderSync(account);
    }

    /** Pulls a channel out of the "mayogram" folder without ever leaving it. */
    private static void removeFromMayogramFolder(int account, long dialogId) {
        Set<Long> pending = pendingFolderRemovals.computeIfAbsent(account, a -> new HashSet<>());
        synchronized (pending) {
            pending.add(dialogId);
        }
        scheduleMayogramFolderSync(account);
    }

    private static void scheduleMayogramFolderSync(int account) {
        Runnable previous = pendingFolderSync.get(account);
        if (previous != null) {
            AndroidUtilities.cancelRunOnUIThread(previous);
        }
        Runnable sync = () -> syncMayogramFolder(account);
        pendingFolderSync.put(account, sync);
        AndroidUtilities.runOnUIThread(sync, 800);
    }

    private static void syncMayogramFolder(int account) {
        pendingFolderSync.remove(account);
        Set<Long> pendingAdds = pendingFolderDialogs.remove(account);
        Set<Long> pendingRemovals = pendingFolderRemovals.remove(account);
        boolean hasAdds = pendingAdds != null && !pendingAdds.isEmpty();
        boolean hasRemovals = pendingRemovals != null && !pendingRemovals.isEmpty();
        if (!hasAdds && !hasRemovals) {
            return;
        }

        MessagesController controller = MessagesController.getInstance(account);
        MessagesController.DialogFilter filter = null;
        for (MessagesController.DialogFilter f : controller.getDialogFilters()) {
            if (!f.isDefault() && FOLDER_NAME.equalsIgnoreCase(f.name)) {
                filter = f;
                break;
            }
        }
        final boolean creatingNew = filter == null;
        if (creatingNew) {
            if (!hasAdds) {
                // Nothing to remove from a folder that doesn't exist yet.
                return;
            }
            filter = new MessagesController.DialogFilter();
            filter.name = FOLDER_NAME;
            filter.id = 2;
            while (controller.dialogFiltersById.get(filter.id) != null) {
                filter.id++;
            }
            filter.order = controller.getDialogFilters().size();
            filter.pendingUnreadCount = filter.unreadCount = -1;
        }
        final MessagesController.DialogFilter finalFilter = filter;

        boolean changed = false;
        if (hasAdds) {
            for (Long dialogId : pendingAdds) {
                if (!finalFilter.alwaysShow.contains(dialogId)) {
                    finalFilter.alwaysShow.add(dialogId);
                    changed = true;
                }
            }
        }
        if (hasRemovals) {
            for (Long dialogId : pendingRemovals) {
                if (finalFilter.alwaysShow.remove(dialogId)) {
                    changed = true;
                }
            }
        }
        if (!changed && !creatingNew) {
            return;
        }

        TLRPC.TL_messages_updateDialogFilter req = new TLRPC.TL_messages_updateDialogFilter();
        req.id = finalFilter.id;
        req.flags |= 1;
        req.filter = new TLRPC.TL_dialogFilter();
        req.filter.id = finalFilter.id;
        req.filter.title = new TLRPC.TL_textWithEntities();
        req.filter.title.text = finalFilter.name;
        for (Long dialogId : finalFilter.alwaysShow) {
            TLRPC.InputPeer inputPeer = toInputPeer(controller, dialogId);
            if (inputPeer != null) {
                req.filter.include_peers.add(inputPeer);
            }
        }

        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            if (error != null) {
                FileLog.e("MayogramAutoPin: updateDialogFilter failed: " + error.code + " " + error.text);
                return;
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (creatingNew) {
                    controller.addFilter(finalFilter, false);
                } else {
                    controller.onFilterUpdate(finalFilter);
                }
                MessagesStorage.getInstance(account).saveDialogFilter(finalFilter, false, true);
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogFiltersUpdated);
            });
        });
    }

    private static TLRPC.InputPeer toInputPeer(MessagesController controller, long dialogId) {
        if (dialogId > 0) {
            TLRPC.User user = controller.getUser(dialogId);
            if (user == null) {
                return null;
            }
            TLRPC.TL_inputPeerUser inputPeer = new TLRPC.TL_inputPeerUser();
            inputPeer.user_id = dialogId;
            inputPeer.access_hash = user.access_hash;
            return inputPeer;
        }
        TLRPC.Chat chat = controller.getChat(-dialogId);
        if (chat == null) {
            return null;
        }
        if (ChatObject.isChannel(chat)) {
            TLRPC.TL_inputPeerChannel inputPeer = new TLRPC.TL_inputPeerChannel();
            inputPeer.channel_id = -dialogId;
            inputPeer.access_hash = chat.access_hash;
            return inputPeer;
        }
        TLRPC.TL_inputPeerChat inputPeer = new TLRPC.TL_inputPeerChat();
        inputPeer.chat_id = -dialogId;
        return inputPeer;
    }

    /**
     * The dialog may take a moment to materialize after a join; retries a
     * couple of times with a short delay before giving up quietly.
     */
    private static void pinAndSync(int account, long dialogId, int attempt) {
        MessagesController controller = MessagesController.getInstance(account);
        TLRPC.Dialog dialog = controller.getDialog(dialogId);
        if (dialog == null) {
            // Dialogs may still be loading right after login; keep trying for a while.
            if (attempt < 10) {
                AndroidUtilities.runOnUIThread(() -> pinAndSync(account, dialogId, attempt + 1), 1500);
            }
            return;
        }
        if (dialog.pinned) {
            return;
        }
        // Mayogram: pinDialog(..., taskId = -1) only updates this device's local
        // dialog list and local DB row - it deliberately skips the
        // TL_messages_toggleDialogPin network call, so the account's real
        // Telegram servers never learn this channel got pinned. Do NOT follow
        // this with reorderPinnedDialogs(): that call always sends
        // TL_messages_reorderPinnedDialogs with force=true regardless of the
        // taskId passed to it, which re-syncs the whole pinned-dialog set
        // (including this channel) to the server - exactly the leak that made
        // auto-pinned official channels show up pinned when the same account
        // was opened in stock Telegram. pinDialog() already places the channel
        // at the top of the local list (pinnedNum = current max + 1), so no
        // further reorder is needed.
        controller.pinDialog(dialogId, true, null, -1);
    }
}
