/*
 * Mayogram: searches for a person by name/username among the members of the
 * channels the current account owns (see MayogramFollowersController, which
 * discovers and persists that owned-channel id list). Surfaced as an extra
 * "In your channels" section in the main dialogs search - lets the user find
 * which of their own channels a given person has joined, something stock
 * Telegram search has no way to answer.
 */

package org.telegram.messenger;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MayogramMemberSearchController {

    public static class Match {
        public final TLRPC.User user;
        public final TLRPC.Chat channel;

        public Match(TLRPC.User user, TLRPC.Chat channel) {
            this.user = user;
            this.channel = channel;
        }
    }

    public interface Callback {
        void onResult(ArrayList<Match> matches);
    }

    private static final int MAX_CHANNELS_SEARCHED = 40;
    private static final int PER_CHANNEL_LIMIT = 5;
    private static final int MAX_RESULTS = 25;

    public static void search(int account, String query, Callback callback) {
        if (query == null || query.trim().length() < 2) {
            callback.onResult(new ArrayList<>());
            return;
        }
        ArrayList<Long> ownedIds = MayogramCustomization.getOwnedChannelIds(account);
        if (ownedIds.isEmpty()) {
            callback.onResult(new ArrayList<>());
            return;
        }
        final String q = query.trim();
        final List<Long> channelIds = ownedIds.subList(0, Math.min(ownedIds.size(), MAX_CHANNELS_SEARCHED));
        if (channelIds.isEmpty()) {
            callback.onResult(new ArrayList<>());
            return;
        }

        final ArrayList<Match> matches = new ArrayList<>();
        final AtomicInteger pending = new AtomicInteger(channelIds.size());

        for (Long chatId : channelIds) {
            TLRPC.Chat chat = MessagesController.getInstance(account).getChat(chatId);
            if (chat == null) {
                if (pending.decrementAndGet() == 0) {
                    finish(matches, callback);
                }
                continue;
            }
            TLRPC.TL_channels_getParticipants req = new TLRPC.TL_channels_getParticipants();
            req.channel = MessagesController.getInstance(account).getInputChannel(chatId);
            TLRPC.TL_channelParticipantsSearch filter = new TLRPC.TL_channelParticipantsSearch();
            filter.q = q;
            req.filter = filter;
            req.offset = 0;
            req.limit = PER_CHANNEL_LIMIT;
            ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
                if (response instanceof TLRPC.TL_channels_channelParticipants) {
                    TLRPC.TL_channels_channelParticipants res = (TLRPC.TL_channels_channelParticipants) response;
                    MessagesController.getInstance(account).putUsers(res.users, false);
                    MessagesStorage.getInstance(account).putUsersAndChats(res.users, null, false, true);
                    synchronized (matches) {
                        for (int a = 0; a < res.users.size(); a++) {
                            matches.add(new Match(res.users.get(a), chat));
                        }
                    }
                }
                if (pending.decrementAndGet() == 0) {
                    finish(matches, callback);
                }
            });
        }
    }

    private static void finish(ArrayList<Match> matches, Callback callback) {
        ArrayList<Match> result;
        synchronized (matches) {
            result = new ArrayList<>(matches);
        }
        if (result.size() > MAX_RESULTS) {
            result = new ArrayList<>(result.subList(0, MAX_RESULTS));
        }
        final ArrayList<Match> finalResult = result;
        AndroidUtilities.runOnUIThread(() -> callback.onResult(finalResult));
    }
}
