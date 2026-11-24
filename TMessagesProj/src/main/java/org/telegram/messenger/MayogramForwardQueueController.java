/*
 * Mayogram: drives the chunked owner-cache-reupload forward queue (the
 * "sent X of Y" bar shown while forwarding more than one message out of a
 * restricted chat the user owns) independent of any ChatActivity instance.
 *
 * That queue used to live as plain fields on ChatActivity and advance via a
 * messageReceivedByServer observer the fragment registered in its own
 * observersGroup. Closing the chat - or the app being backgrounded long
 * enough for Android to destroy that fragment - tore the observer down,
 * silently freezing the queue mid-forward: no error, just nothing further
 * ever went out. A plain single-message upload never had this problem,
 * since it's driven entirely inside SendMessagesHelper/FileUploadOperation,
 * not by any UI.
 *
 * This controller registers its own messageReceivedByServer observer
 * directly on NotificationCenter, once per account, for the app process's
 * whole lifetime, so the queue keeps advancing with no fragment attached at
 * all. ChatActivity only attaches/detaches a Listener to show progress
 * while it happens to be the live fragment for that dialog - detaching
 * never stops the underlying sends, and reopening the chat (or just
 * resuming it) can reattach to see the real current progress via
 * isActive/getSent/getTotal.
 */
package org.telegram.messenger;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class MayogramForwardQueueController {

    public interface Listener {
        void onProgress(int sent, int total);
        void onComplete();
    }

    private static class QueueState {
        long dialogId;
        ArrayList<MessageObject> queue;
        int nextIndex;
        int pendingCount;
        int lastCountedMessageId;
        boolean fromMyName;
        boolean hideCaption;
        boolean notify;
        long payStars;
        int total;
        int sent;
        WeakReference<Listener> listener;
    }

    private static final HashMap<Integer, HashMap<Long, QueueState>> statesByAccount = new HashMap<>();
    private static final HashSet<Integer> registeredAccounts = new HashSet<>();

    private static void ensureRegistered(int account) {
        if (!registeredAccounts.add(account)) {
            return;
        }
        NotificationCenter.getInstance(account).addObserver((id, acc, args) -> {
            if (id != NotificationCenter.messageReceivedByServer || args.length < 4 || !(args[3] instanceof Long)) {
                return;
            }
            long dialogId = (Long) args[3];
            int newMessageId = args[1] instanceof Integer ? (Integer) args[1] : 0;
            onMessageDelivered(account, dialogId, newMessageId);
        }, NotificationCenter.messageReceivedByServer);
    }

    public static void startForward(int account, long dialogId, ArrayList<MessageObject> queue, boolean fromMyName, boolean hideCaption, boolean notify, long payStars, Listener listener) {
        if (queue == null || queue.isEmpty()) {
            return;
        }
        ensureRegistered(account);
        QueueState state = new QueueState();
        state.dialogId = dialogId;
        state.queue = queue;
        state.fromMyName = fromMyName;
        state.hideCaption = hideCaption;
        state.notify = notify;
        state.payStars = payStars;
        state.total = queue.size();
        state.listener = listener != null ? new WeakReference<>(listener) : null;
        getStatesForAccount(account).put(dialogId, state);
        sendNextChunk(account, state);
    }

    public static void attachListener(int account, long dialogId, Listener listener) {
        QueueState state = getStatesForAccount(account).get(dialogId);
        if (state == null) {
            return;
        }
        state.listener = listener != null ? new WeakReference<>(listener) : null;
        if (listener != null) {
            listener.onProgress(state.sent, state.total);
        }
    }

    public static void detachListener(int account, long dialogId, Listener listener) {
        QueueState state = getStatesForAccount(account).get(dialogId);
        if (state != null && state.listener != null && state.listener.get() == listener) {
            state.listener = null;
        }
    }

    public static boolean isActive(int account, long dialogId) {
        return getStatesForAccount(account).get(dialogId) != null;
    }

    public static int getSent(int account, long dialogId) {
        QueueState state = getStatesForAccount(account).get(dialogId);
        return state != null ? state.sent : 0;
    }

    public static int getTotal(int account, long dialogId) {
        QueueState state = getStatesForAccount(account).get(dialogId);
        return state != null ? state.total : 0;
    }

    /**
     * Whatever single message is already in flight can't be recalled, but
     * removing the state here means onMessageDelivered finds nothing to
     * queue further once that one lands.
     */
    public static void cancel(int account, long dialogId) {
        getStatesForAccount(account).remove(dialogId);
    }

    private static HashMap<Long, QueueState> getStatesForAccount(int account) {
        HashMap<Long, QueueState> map = statesByAccount.get(account);
        if (map == null) {
            map = new HashMap<>();
            statesByAccount.put(account, map);
        }
        return map;
    }

    private static void sendNextChunk(int account, QueueState state) {
        if (state.nextIndex >= state.queue.size()) {
            return;
        }
        ArrayList<MessageObject> chunk = new ArrayList<>();
        chunk.add(state.queue.get(state.nextIndex));
        state.nextIndex++;
        state.pendingCount = 1;
        SendMessagesHelper.getInstance(account).sendMessage(chunk, state.dialogId, state.fromMyName, state.hideCaption, state.notify, 0, 0, null, -1, state.payStars, 0, null);
    }

    private static void onMessageDelivered(int account, long dialogId, int newMessageId) {
        HashMap<Long, QueueState> states = statesByAccount.get(account);
        QueueState state = states == null ? null : states.get(dialogId);
        if (state == null || state.pendingCount <= 0) {
            return;
        }
        if (newMessageId != 0 && newMessageId == state.lastCountedMessageId) {
            return;
        }
        state.lastCountedMessageId = newMessageId;
        state.pendingCount--;
        state.sent = Math.min(state.sent + 1, state.total);
        Listener listener = state.listener != null ? state.listener.get() : null;
        if (listener != null) {
            listener.onProgress(state.sent, state.total);
        }
        if (state.sent >= state.total) {
            states.remove(dialogId);
            if (listener != null) {
                listener.onComplete();
            }
            return;
        }
        if (state.nextIndex < state.queue.size()) {
            sendNextChunk(account, state);
        }
    }
}
