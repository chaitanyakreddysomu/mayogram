/*
 * Mayogram: multi-select version of a bot's "Add to Group or Channel" button
 * (see ProfileActivity's addToGroupButtonRow / invite_to_group, which opens
 * this instead of the stock single-chat picker for bot profiles). Same row
 * style as MayogramChannelIdsActivity - avatar left, name, square tick mark
 * at the right end, selected chats shown as a horizontal avatar strip above
 * the list. The bottom-right floating button asks once whether to add the
 * bot as a plain member or as an administrator (full default rights) and
 * applies that choice to every selected chat.
 */
package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.FragmentFloatingButton;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class MayogramAddBotToChannelsActivity extends UniversalFragment {

    private final long botUserId;
    private final LinkedHashSet<Long> selectedChatIds = new LinkedHashSet<>();
    private String query = "";
    private boolean working;
    private FragmentFloatingButton floatingButton;

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
                }
            }
        });
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
        if (view instanceof FrameLayout) {
            floatingButton = new FragmentFloatingButton(context, getResourceProvider());
            floatingButton.setImageResource(R.drawable.floating_check);
            floatingButton.setContentDescription("Next");
            floatingButton.setOnClickListener(v -> showRolePicker());
            ((FrameLayout) view).addView(floatingButton, FragmentFloatingButton.createDefaultLayoutParams());
        }
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        List<TLRPC.Chat> chats = collectEligibleChats();

        if (!selectedChatIds.isEmpty()) {
            items.add(UItem.asCustom(buildSelectedStrip(getContext(), chats)));
        }

        items.add(UItem.asShadow(selectedChatIds.isEmpty()
                ? "Select the channels/groups to add this bot to, then tap the checkmark (bottom right)."
                : selectedChatIds.size() + " selected. Tap the checkmark (bottom right) when ready."));

        String q = query.trim().toLowerCase(Locale.getDefault());
        int shown = 0;
        for (int i = 0; i < chats.size(); i++) {
            TLRPC.Chat chat = chats.get(i);
            if (!q.isEmpty() && (chat.title == null || !chat.title.toLowerCase(Locale.getDefault()).contains(q))) {
                continue;
            }
            shown++;
            items.add(UItem.asCustom(buildChatRow(getContext(), chat)));
        }
        if (shown == 0) {
            items.add(UItem.asShadow(q.isEmpty() ? "No eligible channels or groups found." : "No matches for \"" + query.trim() + "\"."));
        }
    }

    private View buildChatRow(Context context, TLRPC.Chat chat) {
        UserCell cell = new UserCell(context, 16, 2, false);
        cell.setData(chat, null, null, 0, false);
        cell.setChecked(selectedChatIds.contains(chat.id), false);
        cell.setOnClickListener(v -> toggleSelection(chat));
        return cell;
    }

    private View buildSelectedStrip(Context context, List<TLRPC.Chat> chats) {
        HorizontalScrollView scrollView = new HorizontalScrollView(context);
        scrollView.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        for (int i = 0; i < chats.size(); i++) {
            TLRPC.Chat chat = chats.get(i);
            if (!selectedChatIds.contains(chat.id)) {
                continue;
            }
            row.addView(buildAvatarChip(context, chat), LayoutHelper.createLinear(52, 52, 0, 0, 0, 10, 0));
        }
        scrollView.addView(row, LayoutHelper.createScroll(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
        return scrollView;
    }

    private View buildAvatarChip(Context context, TLRPC.Chat chat) {
        FrameLayout chip = new FrameLayout(context);

        BackupImageView avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(dp(22));
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setInfo(chat);
        avatarView.setImage(ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_SMALL), "50_50", avatarDrawable, chat);
        chip.addView(avatarView, LayoutHelper.createFrame(44, 44, Gravity.TOP | Gravity.LEFT));

        ImageView removeBadge = new ImageView(context);
        removeBadge.setImageResource(R.drawable.msg_close);
        removeBadge.setColorFilter(0xffffffff);
        removeBadge.setPadding(dp(3), dp(3), dp(3), dp(3));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(0xff8a8a8a);
        removeBadge.setBackground(badgeBg);
        chip.addView(removeBadge, LayoutHelper.createFrame(16, 16, Gravity.TOP | Gravity.RIGHT));

        chip.setOnClickListener(v -> toggleSelection(chat));
        return chip;
    }

    private void toggleSelection(TLRPC.Chat chat) {
        if (!selectedChatIds.remove(chat.id)) {
            selectedChatIds.add(chat.id);
        }
        if (listView != null) {
            listView.adapter.update(true);
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
            if (getParentActivity() != null) {
                BulletinFactory.of(this).createSimpleBulletin(R.drawable.msg_retry, "Couldn't find this bot's account - try reopening its profile.").show();
            }
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
        boolean isBroadcastChannel = isChannel && !chat.megagroup;

        if (isBroadcastChannel) {
            // Telegram has no concept of a plain, non-admin bot in a
            // broadcast channel - channels.inviteToChannel rejects a bot
            // with USER_BOT every time. A bot can only ever be added to a
            // channel by promoting it directly via channels.editAdmin,
            // which adds it as a participant as a side effect. "Member" here
            // is the closest real equivalent: promote with every right off.
            TLRPC.TL_chatAdminRights rights = ChatRightsEditActivity.emptyAdminRights(asAdmin);
            getMessagesController().setUserAdminRole(chat.id, bot, rights, null, true, this, true, false, null,
                    () -> addNext(targets, index + 1, bot, asAdmin, successCount + 1, failCount),
                    error -> {
                        addNext(targets, index + 1, bot, asAdmin, successCount, failCount + 1);
                        return true;
                    });
            return;
        }

        getMessagesController().addUserToChat(chat.id, bot, 0, null, this, true, () -> {
            if (asAdmin) {
                getMessagesController().setUserAdminRole(chat.id, bot, ChatRightsEditActivity.emptyAdminRights(true), null, isChannel, this, true, false, null,
                        () -> addNext(targets, index + 1, bot, asAdmin, successCount + 1, failCount),
                        error -> {
                            // Surfaced so a real cause (e.g. missing admin
                            // rights, bot already admin) is visible instead
                            // of just silently skipping this chat.
                            addNext(targets, index + 1, bot, asAdmin, successCount, failCount + 1);
                            return true;
                        });
            } else {
                addNext(targets, index + 1, bot, asAdmin, successCount + 1, failCount);
            }
        }, error -> {
            addNext(targets, index + 1, bot, asAdmin, successCount, failCount + 1);
            return true;
        });
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
    }
}
