/*
 * Mayogram: "Compare" screen in Settings -> Mayogram, below Followers. Pick
 * 2+ owned channels, scans each one's participant list (same
 * channels.getParticipants paging as MayogramFollowersController), and
 * reports how many distinct people follow at least one of the selected
 * channels - plus each channel's own count. Deliberately a separate,
 * independent scan state from the main Followers scan (not shared/reused)
 * so running one never interferes with the other.
 */
package org.telegram.messenger;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class MayogramCompareController {

    public interface Listener {
        void onChannelDone(TLRPC.Chat chat, int channelFollowers, int channelsDone, int channelsTotal);
        void onComplete(int uniqueTotal, HashMap<Long, Integer> perChannelCounts);
        void onError();
    }

    private static class CompareState {
        int account;
        List<TLRPC.Chat> channels;
        int channelIndex;
        int offset;
        final HashSet<Long> uniqueFollowers = new HashSet<>();
        final HashMap<Long, Integer> perChannelCounts = new HashMap<>();
        WeakReference<Listener> listener;
    }

    private static final HashMap<Integer, CompareState> activeStates = new HashMap<>();
    private static final int PAGE_LIMIT = 200;

    public static boolean isActive(int account) {
        return activeStates.containsKey(account);
    }

    public static void cancel(int account) {
        activeStates.remove(account);
    }

    public static void attachListener(int account, Listener listener) {
        CompareState state = activeStates.get(account);
        if (state != null) {
            state.listener = listener != null ? new WeakReference<>(listener) : null;
        }
    }

    public static void start(int account, List<Long> chatIds, Listener listener) {
        if (chatIds == null || chatIds.size() < 2 || isActive(account)) {
            return;
        }
        CompareState state = new CompareState();
        state.account = account;
        state.listener = listener != null ? new WeakReference<>(listener) : null;
        state.channels = new ArrayList<>();
        for (int i = 0; i < chatIds.size(); i++) {
            TLRPC.Chat chat = MessagesController.getInstance(account).getChat(chatIds.get(i));
            if (chat != null) {
                state.channels.add(chat);
            }
        }
        if (state.channels.size() < 2) {
            notifyError(state);
            return;
        }
        activeStates.put(account, state);
        scanNextChannel(state);
    }

    private static void scanNextChannel(CompareState state) {
        if (activeStates.get(state.account) != state) {
            return;
        }
        if (state.channelIndex >= state.channels.size()) {
            activeStates.remove(state.account);
            Listener listener = state.listener != null ? state.listener.get() : null;
            if (listener != null) {
                listener.onComplete(state.uniqueFollowers.size(), state.perChannelCounts);
            }
            return;
        }
        state.offset = 0;
        fetchPage(state);
    }

    private static void fetchPage(CompareState state) {
        if (activeStates.get(state.account) != state) {
            return;
        }
        TLRPC.Chat chat = state.channels.get(state.channelIndex);
        TLRPC.TL_channels_getParticipants req = new TLRPC.TL_channels_getParticipants();
        req.channel = MessagesController.getInstance(state.account).getInputChannel(chat.id);
        req.filter = new TLRPC.TL_channelParticipantsRecent();
        req.offset = state.offset;
        req.limit = PAGE_LIMIT;
        ConnectionsManager.getInstance(state.account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (activeStates.get(state.account) != state) {
                return;
            }
            if (response instanceof TLRPC.TL_channels_channelParticipants) {
                TLRPC.TL_channels_channelParticipants res = (TLRPC.TL_channels_channelParticipants) response;
                MessagesController.getInstance(state.account).putUsers(res.users, false);
                MessagesStorage.getInstance(state.account).putUsersAndChats(res.users, null, false, true);
                int before = state.uniqueFollowers.size();
                for (int i = 0; i < res.users.size(); i++) {
                    state.uniqueFollowers.add(res.users.get(i).id);
                }
                Integer prevCount = state.perChannelCounts.get(chat.id);
                state.perChannelCounts.put(chat.id, (prevCount != null ? prevCount : 0) + res.users.size());
                if (res.participants.size() >= PAGE_LIMIT) {
                    state.offset += PAGE_LIMIT;
                    fetchPage(state);
                } else {
                    finishChannel(state, chat);
                }
            } else {
                finishChannel(state, chat);
            }
        }));
    }

    private static void finishChannel(CompareState state, TLRPC.Chat chat) {
        Integer count = state.perChannelCounts.get(chat.id);
        Listener listener = state.listener != null ? state.listener.get() : null;
        state.channelIndex++;
        if (listener != null) {
            listener.onChannelDone(chat, count != null ? count : 0, state.channelIndex, state.channels.size());
        }
        scanNextChannel(state);
    }

    private static void notifyError(CompareState state) {
        Listener listener = state.listener != null ? state.listener.get() : null;
        if (listener != null) {
            listener.onError();
        }
    }
}
