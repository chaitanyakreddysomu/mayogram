/*
 * Mayogram: after a successful login, auto-join (and optionally pin to the
 * very top, unpinnably) every channel in the server-controlled "official
 * channels" list - see supabase/official_channels.sql. The list itself can
 * be changed from Supabase at any time (add/remove channels, toggle
 * pinning) without an app update.
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
import java.util.Set;

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
            if (channels == null || channels.isEmpty()) {
                // Supabase not configured, unreachable, or the list is empty -
                // fall back to the one channel this app has always shipped with.
                channels = new ArrayList<>();
                channels.add(new OfficialChannel(FALLBACK_CHANNEL_USERNAME, true));
            }
            final List<OfficialChannel> finalChannels = channels;
            AndroidUtilities.runOnUIThread(() -> {
                Set<Long> lockedIds = new HashSet<>();
                for (OfficialChannel channel : finalChannels) {
                    resolveJoinAndMaybePin(account, channel, lockedIds);
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

    private static void resolveJoinAndMaybePin(int account, OfficialChannel channel, Set<Long> lockedIds) {
        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = channel.username;
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
                if (channel.pinToTop) {
                    synchronized (lockedIds) {
                        lockedIds.add(chat.id);
                        // Re-publish the locked set on every addition - channels
                        // resolve asynchronously and out of order, so there is no
                        // single "all done" point to publish once from.
                        MayogramBranding.setLockedChannelIds(new HashSet<>(lockedIds));
                    }
                }
                joinIfNeededThenPin(account, chat, channel.pinToTop);
            });
        });
    }

    private static void joinIfNeededThenPin(int account, TLRPC.Chat chat, boolean pinToTop) {
        long dialogId = -chat.id;
        MessagesController controller = MessagesController.getInstance(account);
        if (controller.getDialog(dialogId) != null) {
            if (pinToTop) {
                pinAndSync(account, dialogId, 0);
            }
            return;
        }
        TLRPC.TL_channels_joinChannel req = new TLRPC.TL_channels_joinChannel();
        req.channel = MessagesController.getInputChannel(chat);
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            if (error != null) {
                return;
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (response instanceof TLRPC.Updates) {
                    controller.processUpdates((TLRPC.Updates) response, false);
                }
                if (pinToTop) {
                    pinAndSync(account, dialogId, 0);
                }
            });
        });
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
        if (controller.pinDialog(dialogId, true, null, -1)) {
            controller.reorderPinnedDialogs(dialog.folder_id, null, 0);
        }
    }
}
