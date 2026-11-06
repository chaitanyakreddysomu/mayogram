/*
 * Mayogram: after a successful login, make sure the @mayogram channel is
 * joined and pinned to the very top of that account's dialog list. Runs
 * once per login (see the call site in LoginActivity's onAuthSuccess),
 * best-effort - any failure (channel missing, already left, network error)
 * is silently ignored and never blocks or disrupts login.
 */

package org.telegram.messenger;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

public class MayogramAutoPin {

    private static final String CHANNEL_USERNAME = "mayogram";

    private MayogramAutoPin() {
    }

    public static void ensurePinnedTop(int account) {
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
                joinIfNeededThenPin(account, chat);
            });
        });
    }

    private static void joinIfNeededThenPin(int account, TLRPC.Chat chat) {
        long dialogId = -chat.id;
        MessagesController controller = MessagesController.getInstance(account);
        if (controller.getDialog(dialogId) != null) {
            pinAndSync(account, dialogId, 0);
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
                pinAndSync(account, dialogId, 0);
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
