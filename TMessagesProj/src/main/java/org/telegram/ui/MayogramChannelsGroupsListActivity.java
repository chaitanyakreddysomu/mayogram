/*
 * Mayogram: opened from the "Channels & Groups" counter row in Settings.
 * Lists exactly the chats counted by ChannelMembershipCounter, so the count
 * shown there is always exactly this list's size - never a stale or
 * seemingly random number. Each row shows whether you're the Owner, an
 * Admin, or just a Member, and the list can be filtered by title.
 */
package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.EditText;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.Locale;

public class MayogramChannelsGroupsListActivity extends UniversalFragment {

    private String query = "";

    @Override
    protected CharSequence getTitle() {
        return "Channels & Groups";
    }

    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        ActionBarMenuItem searchItem = actionBar.createMenu().addItem(0, R.drawable.outline_header_search, getResourceProvider());
        searchItem.setIsSearchField(true);
        searchItem.setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onTextChanged(EditText editText) {
                query = editText.getText().toString();
                if (listView != null) {
                    listView.adapter.update(true);
                }
            }

            @Override
            public void onSearchCollapse() {
                query = "";
                if (listView != null) {
                    listView.adapter.update(true);
                }
            }
        });
        return view;
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

        String q = query.trim().toLowerCase(Locale.getDefault());
        int shown = 0;
        items.add(UItem.asHeader(chats.size() + " channels & groups"));
        for (int i = 0; i < chats.size(); i++) {
            TLRPC.Chat chat = chats.get(i);
            if (!q.isEmpty() && (chat.title == null || !chat.title.toLowerCase(Locale.getDefault()).contains(q))) {
                continue;
            }
            shown++;
            UItem item = UItem.asProfileCell(chat);
            item.subtext = roleLabel(chat);
            items.add(item);
        }
        if (shown == 0) {
            items.add(UItem.asShadow(q.isEmpty() ? "No channels or groups found." : "No matches for \"" + query.trim() + "\"."));
        }
    }

    private CharSequence roleLabel(TLRPC.Chat chat) {
        if (chat.creator) {
            return "Owner";
        } else if (ChatObject.hasAdminRights(chat)) {
            return "Admin";
        }
        return "Member";
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
