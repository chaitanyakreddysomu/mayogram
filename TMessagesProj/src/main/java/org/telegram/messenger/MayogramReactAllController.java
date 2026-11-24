/*
 * Mayogram: "React to All Messages" (channel 3-dot menu). Pages through the
 * whole chat's history (messages.getHistory), then sends one
 * messages.sendReaction per message, one at a time. If Telegram throttles
 * it (FLOOD_WAIT_n), it waits the exact n seconds the server asked for and
 * retries that same message - nothing is skipped, nothing restarts from
 * scratch. Lives independent of any fragment (same reasoning as
 * MayogramForwardQueueController) so closing the chat doesn't stop it.
 */
package org.telegram.messenger;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class MayogramReactAllController {

    public interface Listener {
        void onProgress(int done, int total);
        void onWaiting(int seconds);
        void onComplete(int done, int total);
        void onError();
    }

    private static class Job {
        List<String> emojiPool;
        ArrayList<Integer> messageIds = new ArrayList<>();
        int index;
        java.lang.ref.WeakReference<Listener> listener;
    }

    private static final HashMap<Integer, HashMap<Long, Job>> jobsByAccount = new HashMap<>();
    private static final int PAGE_LIMIT = 100;
    private static final int DELAY_BETWEEN_MS = 400;

    public static boolean isActive(int account, long dialogId) {
        return getJobs(account).get(dialogId) != null;
    }

    public static void attachListener(int account, long dialogId, Listener listener) {
        Job job = getJobs(account).get(dialogId);
        if (job == null) {
            return;
        }
        job.listener = listener != null ? new java.lang.ref.WeakReference<>(listener) : null;
        if (listener != null) {
            listener.onProgress(job.index, job.messageIds.size());
        }
    }

    public static void detachListener(int account, long dialogId, Listener listener) {
        Job job = getJobs(account).get(dialogId);
        if (job != null && job.listener != null && job.listener.get() == listener) {
            job.listener = null;
        }
    }

    public static void cancel(int account, long dialogId) {
        getJobs(account).remove(dialogId);
    }

    public static void start(int account, long dialogId, List<String> emojiPool, Listener listener) {
        if (isActive(account, dialogId) || emojiPool == null || emojiPool.isEmpty()) {
            return;
        }
        Job job = new Job();
        job.emojiPool = emojiPool;
        job.listener = listener != null ? new java.lang.ref.WeakReference<>(listener) : null;
        getJobs(account).put(dialogId, job);
        loadHistoryPage(account, dialogId, job, 0);
    }

    private static HashMap<Long, Job> getJobs(int account) {
        HashMap<Long, Job> map = jobsByAccount.get(account);
        if (map == null) {
            map = new HashMap<>();
            jobsByAccount.put(account, map);
        }
        return map;
    }

    private static void loadHistoryPage(int account, long dialogId, Job job, int offsetId) {
        if (getJobs(account).get(dialogId) != job) {
            return;
        }
        TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
        req.peer = MessagesController.getInstance(account).getInputPeer(dialogId);
        req.offset_id = offsetId;
        req.limit = PAGE_LIMIT;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (getJobs(account).get(dialogId) != job) {
                return;
            }
            if (!(response instanceof TLRPC.messages_Messages)) {
                getJobs(account).remove(dialogId);
                notifyError(job);
                return;
            }
            TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
            MessagesController.getInstance(account).putUsers(res.users, false);
            MessagesController.getInstance(account).putChats(res.chats, false);
            int lastId = 0;
            for (int i = 0; i < res.messages.size(); i++) {
                TLRPC.Message m = res.messages.get(i);
                if (!(m instanceof TLRPC.TL_messageEmpty) && !(m instanceof TLRPC.TL_messageService)) {
                    job.messageIds.add(m.id);
                }
                lastId = m.id;
            }
            if (res.messages.size() >= PAGE_LIMIT && lastId > 0) {
                loadHistoryPage(account, dialogId, job, lastId);
            } else {
                reactNext(account, dialogId, job);
            }
        }));
    }

    private static void reactNext(int account, long dialogId, Job job) {
        if (getJobs(account).get(dialogId) != job) {
            return;
        }
        if (job.index >= job.messageIds.size()) {
            getJobs(account).remove(dialogId);
            Listener listener = job.listener != null ? job.listener.get() : null;
            if (listener != null) {
                listener.onComplete(job.index, job.messageIds.size());
            }
            return;
        }
        int msgId = job.messageIds.get(job.index);
        String emoji = job.emojiPool.size() == 1 ? job.emojiPool.get(0) : job.emojiPool.get((int) (Math.random() * job.emojiPool.size()));
        TLRPC.TL_messages_sendReaction req = new TLRPC.TL_messages_sendReaction();
        req.peer = MessagesController.getInstance(account).getInputPeer(dialogId);
        req.msg_id = msgId;
        req.flags |= 1;
        TLRPC.TL_reactionEmoji reaction = new TLRPC.TL_reactionEmoji();
        reaction.emoticon = emoji;
        req.reaction.add(reaction);
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (getJobs(account).get(dialogId) != job) {
                return;
            }
            if (error != null && error.text != null && error.text.startsWith("FLOOD_WAIT_")) {
                int seconds = 5;
                try {
                    seconds = Integer.parseInt(error.text.substring("FLOOD_WAIT_".length()));
                } catch (Exception ignore) {
                }
                Listener listener = job.listener != null ? job.listener.get() : null;
                if (listener != null) {
                    listener.onWaiting(seconds);
                }
                AndroidUtilities.runOnUIThread(() -> reactNext(account, dialogId, job), (seconds + 1) * 1000L);
                return;
            }
            if (response instanceof TLRPC.Updates) {
                MessagesController.getInstance(account).processUpdates((TLRPC.Updates) response, false);
            }
            job.index++;
            Listener listener = job.listener != null ? job.listener.get() : null;
            if (listener != null) {
                listener.onProgress(job.index, job.messageIds.size());
            }
            AndroidUtilities.runOnUIThread(() -> reactNext(account, dialogId, job), DELAY_BETWEEN_MS);
        }));
    }

    private static void notifyError(Job job) {
        Listener listener = job.listener != null ? job.listener.get() : null;
        if (listener != null) {
            listener.onError();
        }
    }
}
