/*
 * Mayogram: opened from the "Channels & Groups" counter row in Settings.
 * Lists exactly the chats counted by ChannelMembershipCounter, so the count
 * shown there is always exactly this list's size - never a stale or
 * seemingly random number.
 */
package org.telegram.ui;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class MayogramChannelsGroupsListActivity extends UniversalFragment {

    @Override
    protected CharSequence getTitle() {
        return "Channels & Groups";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        int account = getCurrentAccount();
        MessagesController controller = MessagesController.getInstance(account);
        ArrayList<TLRPC.Dialog> dialogs = controller.getAllDialogs();
        ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        for (int i = 0; i < dialogs.size(); i++) {
            long dialogId = dialogs.get(i).id;
            if (dialogId >= 0) {
                continue;
            }
            TLRPC.Chat chat = controller.getChat(-dialogId);
            if (chat != null && ChatObject.isChannel(chat) && !ChatObject.isNotInChat(chat)) {
                chats.add(chat);
            }
        }
        items.add(UItem.asHeader(chats.size() + " channels & groups"));
        for (int i = 0; i < chats.size(); i++) {
            items.add(UItem.asProfileCell(chats.get(i)));
        }
        if (chats.isEmpty()) {
            items.add(UItem.asShadow("No channels or groups found."));
        }
    }

    @Override
    protected void onClick(UItem item, android.view.View view, int position, float x, float y) {
        if (item.object instanceof TLRPC.Chat) {
            TLRPC.Chat chat = (TLRPC.Chat) item.object;
            android.os.Bundle args = new android.os.Bundle();
            args.putLong("chat_id", chat.id);
            presentFragment(new ChatActivity(args));
        }
    }

    @Override
    protected boolean onLongClick(UItem item, android.view.View view, int position, float x, float y) {
        return false;
    }
}
