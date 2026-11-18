/*
 * Mayogram: multi-select version of a bot's "Add to Group or Channel" button
 * (see ProfileActivity's addToGroupButtonRow / invite_to_group, which now
 * opens this instead of the stock single-chat picker for bot profiles).
 * Lists every channel/group the account can add a bot to (the same
 * dialogsMyChannels/dialogsMyGroups/dialogsCanAddUsers sets DialogsActivity's
 * own add-users picker uses), lets you check any number of them, then asks
 * once whether to add the bot as a plain member or as an administrator
 * (full default rights) and applies that choice to every selected chat.
 */
package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.EditText;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class MayogramAddBotToChannelsActivity extends UniversalFragment {

    private static final int BUTTON_NEXT = 1;

    private final long botUserId;
    private final LinkedHashSet<Long> selectedChatIds = new LinkedHashSet<>();
    private String query = "";
    private boolean working;

    public MayogramAddBotToChannelsActivity(long botUserId) {
        super();
        this.botUserId = botUserId;
    }

    @Override
    protected CharSequence getTitle() {
        return "Add Bot to Channels";
    }

    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == BUTTON_NEXT) {
                    showRolePicker();
                }
            }
        });
        actionBar.createMenu().addItem(BUTTON_NEXT, "Next");
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
        List<TLRPC.Chat> chats = collectEligibleChats();
        items.add(UItem.asShadow(selectedChatIds.isEmpty()
                ? "Select the channels/groups to add this bot to, then tap Next (top right)."
                : selectedChatIds.size() + " selected. Tap Next (top right) when ready."));

        String q = query.trim().toLowerCase(Locale.getDefault());
        int shown = 0;
        for (int i = 0; i < chats.size(); i++) {
            TLRPC.Chat chat = chats.get(i);
            if (!q.isEmpty() && (chat.title == null || !chat.title.toLowerCase(Locale.getDefault()).contains(q))) {
                continue;
            }
            shown++;
            UItem item = UItem.asCheck((int) chat.id, chat.title);
            item.object = chat;
            item.checked = selectedChatIds.contains(chat.id);
            items.add(item);
        }
        if (shown == 0) {
            items.add(UItem.asShadow(q.isEmpty() ? "No eligible channels or groups found." : "No matches for \"" + query.trim() + "\"."));
        }
    }

    private List<TLRPC.Chat> collectEligibleChats() {
        MessagesController controller = getMessagesController();
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        addChatsFromDialogs(controller.dialogsMyChannels, controller, chats, seen);
        addChatsFromDialogs(controller.dialogsMyGroups, controller, chats, seen);
        addChatsFromDialogs(controller.dialogsCanAddUsers, controller, chats, seen);
        return chats;
    }

    private void addChatsFromDialogs(List<TLRPC.Dialog> dialogs, MessagesController controller, ArrayList<TLRPC.Chat> out, LinkedHashSet<Long> seen) {
        if (dialogs == null) {
            return;
        }
        for (int i = 0; i < dialogs.size(); i++) {
            long chatId = -dialogs.get(i).id;
            if (chatId <= 0 || !seen.add(chatId)) {
                continue;
            }
            TLRPC.Chat chat = controller.getChat(chatId);
            if (chat != null) {
                out.add(chat);
            }
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.object instanceof TLRPC.Chat) {
            long id = ((TLRPC.Chat) item.object).id;
            if (!selectedChatIds.remove(id)) {
                selectedChatIds.add(id);
            }
            if (listView != null) {
                listView.adapter.update(true);
            }
        }
    }

    private void showRolePicker() {
        if (working) {
            return;
        }
        if (selectedChatIds.isEmpty()) {
            if (getParentActivity() != null) {
                BulletinFactory.of(this).createSimpleBulletin(R.drawable.msg_retry, "Select at least one channel or group first.").show();
            }
            return;
        }
        if (getContext() == null) {
            return;
        }
        String[] options = {"Add as Member", "Add as Administrator"};
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Add bot as...");
        builder.setItems(options, (dialog, which) -> startBulkAdd(which == 1));
        showDialog(builder.create());
    }

    private void startBulkAdd(boolean asAdmin) {
        TLRPC.User bot = getMessagesController().getUser(botUserId);
        if (bot == null) {
            return;
        }
        List<TLRPC.Chat> chats = collectEligibleChats();
        ArrayList<TLRPC.Chat> targets = new ArrayList<>();
        for (int i = 0; i < chats.size(); i++) {
            if (selectedChatIds.contains(chats.get(i).id)) {
                targets.add(chats.get(i));
            }
        }
        working = true;
        addNext(targets, 0, bot, asAdmin, 0, 0);
    }

    private void addNext(List<TLRPC.Chat> targets, int index, TLRPC.User bot, boolean asAdmin, int successCount, int failCount) {
        if (index >= targets.size()) {
            working = false;
            if (getParentActivity() != null) {
                BulletinFactory.of(this).createSimpleBulletin(R.drawable.msg_retry,
                        "Added to " + successCount + " of " + targets.size() + " chats" + (failCount > 0 ? " (" + failCount + " failed)." : ".")).show();
            }
            finishFragment();
            return;
        }
        TLRPC.Chat chat = targets.get(index);
        boolean isChannel = ChatObject.isChannel(chat);
        getMessagesController().addUserToChat(chat.id, bot, 0, null, this, true, () -> {
            if (asAdmin) {
                getMessagesController().setUserAdminRole(chat.id, bot, ChatRightsEditActivity.emptyAdminRights(true), null, isChannel, this, true, false, null,
                        () -> addNext(targets, index + 1, bot, asAdmin, successCount + 1, failCount),
                        error -> {
                            addNext(targets, index + 1, bot, asAdmin, successCount, failCount + 1);
                            return false;
                        });
            } else {
                addNext(targets, index + 1, bot, asAdmin, successCount + 1, failCount);
            }
        }, error -> {
            addNext(targets, index + 1, bot, asAdmin, successCount, failCount + 1);
            return false;
        });
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
