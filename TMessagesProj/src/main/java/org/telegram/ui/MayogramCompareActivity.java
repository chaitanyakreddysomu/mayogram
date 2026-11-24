/*
 * Mayogram: "Compare" row in Settings -> Mayogram, below Followers. Pick 2+
 * channels you own or admin (fetched live from your current dialogs, not
 * the Followers-scan cache), scan them, and see how many distinct people
 * follow at least one of the selected channels.
 *
 * Telegram caps how many individual subscriber identities channels.
 * getParticipants will return for a broadcast channel (roughly 200,
 * regardless of admin/owner status) - megagroups don't have this limit.
 * The per-channel count shown in the result always uses chat.
 * participants_count (Telegram's own accurate running total, never
 * capped) rather than how many ids we actually got back, so a small
 * channel's count is always exact. The "unique across N channels" number
 * is still necessarily computed from whatever ids were actually fetched,
 * since computing real overlap needs individual identities - for any
 * channel over the ~200 cap that number is a best-effort lower bound, and
 * the result screen says so.
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
import org.telegram.messenger.MayogramCompareController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;

import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class MayogramCompareActivity extends UniversalFragment implements MayogramCompareController.Listener, NotificationCenter.NotificationCenterDelegate {

    private final LinkedHashSet<Long> selectedChatIds = new LinkedHashSet<>();
    private FragmentFloatingButton floatingButton;

    private boolean comparing;
    private int channelsDone;
    private int channelsTotal;
    private final ArrayList<String> doneLines = new ArrayList<>();

    private boolean hasResult;
    private int resultUnique;
    private HashMap<Long, Integer> resultPerChannel;
    private String query = "";

    // Mayogram: chat.participants_count isn't always populated on the plain
    // Chat objects we get from the dialogs list (the server only sends it
    // conditionally), so it can read as 0 even for real channels. We fall
    // back to the cached/loaded ChatFull's participants_count instead, which
    // is always accurate, kicking off a load the first time it's missing and
    // refreshing once chatInfoDidLoad fires.
    private final HashSet<Long> requestedFullChatIds = new HashSet<>();

    @Override
    protected CharSequence getTitle() {
        return "Compare Channels";
    }

    @Override
    public boolean onFragmentCreate() {
        boolean result = super.onFragmentCreate();
        if (MayogramCompareController.isActive(getCurrentAccount())) {
            comparing = true;
            MayogramCompareController.attachListener(getCurrentAccount(), this);
        }
        getNotificationCenter().addObserver(this, NotificationCenter.chatInfoDidLoad);
        return result;
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.chatInfoDidLoad);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoad) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull != null && requestedFullChatIds.contains(chatFull.id) && listView != null) {
                listView.adapter.update(true);
            }
        }
    }

    private int getAccurateSubscriberCount(TLRPC.Chat chat) {
        TLRPC.ChatFull full = getMessagesController().getChatFull(chat.id);
        if (full != null && full.participants_count > 0) {
            return full.participants_count;
        }
        if (chat.participants_count > 0) {
            return chat.participants_count;
        }
        if (requestedFullChatIds.add(chat.id)) {
            getMessagesController().loadFullChat(chat.id, 0, true);
        }
        return -1;
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
        if (view instanceof FrameLayout) {
            floatingButton = new FragmentFloatingButton(context, getResourceProvider());
            floatingButton.setImageResource(R.drawable.floating_check);
            floatingButton.setContentDescription("Compare");
            floatingButton.setOnClickListener(v -> startCompare());
            ((FrameLayout) view).addView(floatingButton, FragmentFloatingButton.createDefaultLayoutParams());
        }
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        List<TLRPC.Chat> allChats = collectCandidateChannels();

        if (hasResult && !comparing) {
            items.add(UItem.asHeader("RESULT"));
            boolean anyCapped = false;
            for (int i = 0; i < allChats.size(); i++) {
                TLRPC.Chat chat = allChats.get(i);
                Integer scanned = resultPerChannel.get(chat.id);
                int accurate = getAccurateSubscriberCount(chat);
                if (scanned != null && accurate > 0 && scanned < accurate) {
                    anyCapped = true;
                    break;
                }
            }
            items.add(UItem.asShadow("Unique followers across the " + resultPerChannel.size() + " channels you compared: " + resultUnique
                    + (anyCapped ? " (best-effort - Telegram only lets channels.getParticipants see roughly the first 200 subscribers of a channel, so this is a lower bound for any channel bigger than that)." : ".")));
            for (int i = 0; i < allChats.size(); i++) {
                TLRPC.Chat chat = allChats.get(i);
                if (resultPerChannel.containsKey(chat.id)) {
                    items.add(UItem.asCustom(buildResultRow(getContext(), chat)));
                }
            }
            items.add(UItem.asShadow(null));
        }

        if (comparing) {
            items.add(UItem.asHeader("COMPARING"));
            items.add(UItem.asShadow(channelsTotal > 0
                    ? ("Scanned " + channelsDone + " of " + channelsTotal + " channels...")
                    : "Starting..."));
            for (int i = doneLines.size() - 1; i >= 0; i--) {
                items.add(UItem.asShadow(doneLines.get(i)));
            }
            return;
        }

        items.add(UItem.asShadow(selectedChatIds.isEmpty()
                ? "Select 2 or more channels you own or admin, then tap the checkmark (bottom right)."
                : selectedChatIds.size() + " selected" + (selectedChatIds.size() < 2 ? " - pick at least 2." : ".")));

        if (!selectedChatIds.isEmpty()) {
            items.add(UItem.asCustom(buildSelectedStrip(getContext(), allChats)));
        }

        String q = query.trim().toLowerCase(Locale.getDefault());
        int shown = 0;
        for (int i = 0; i < allChats.size(); i++) {
            TLRPC.Chat chat = allChats.get(i);
            if (!q.isEmpty() && (chat.title == null || !chat.title.toLowerCase(Locale.getDefault()).contains(q))) {
                continue;
            }
            shown++;
            items.add(UItem.asCustom(buildChatRow(getContext(), chat)));
        }
        if (shown == 0) {
            items.add(UItem.asShadow(allChats.isEmpty()
                    ? "No channels found where you're the owner or an admin."
                    : (q.isEmpty() ? "No channels found." : "No matches for \"" + query.trim() + "\".")));
        }
    }

    private View buildResultRow(Context context, TLRPC.Chat chat) {
        UserCell cell = new UserCell(context, 16, 0, false);
        cell.setData(chat, null, subscriberCountLabel(chat), 0, false);
        return cell;
    }

    private View buildChatRow(Context context, TLRPC.Chat chat) {
        UserCell cell = new UserCell(context, 16, 2, false);
        cell.setData(chat, null, subscriberCountLabel(chat), 0, false);
        cell.setChecked(selectedChatIds.contains(chat.id), false);
        cell.setOnClickListener(v -> toggleSelection(chat));
        return cell;
    }

    private String subscriberCountLabel(TLRPC.Chat chat) {
        int count = getAccurateSubscriberCount(chat);
        if (count < 0) {
            return "Loading...";
        }
        return count + (count == 1 ? " subscriber" : " subscribers");
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

    private List<TLRPC.Chat> collectCandidateChannels() {
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
            if (chat != null && ChatObject.isChannel(chat) && !ChatObject.isNotInChat(chat)
                    && (chat.creator || ChatObject.hasAdminRights(chat))) {
                chats.add(chat);
            }
        }
        return chats;
    }

    private void startCompare() {
        if (comparing) {
            return;
        }
        if (selectedChatIds.size() < 2) {
            if (getParentActivity() != null) {
                BulletinFactory.of(this).createSimpleBulletin(R.drawable.msg_retry, "Select at least 2 channels first.").show();
            }
            return;
        }
        comparing = true;
        hasResult = false;
        channelsDone = 0;
        channelsTotal = selectedChatIds.size();
        doneLines.clear();
        if (listView != null) {
            listView.adapter.update(true);
        }
        MayogramCompareController.start(getCurrentAccount(), new ArrayList<>(selectedChatIds), this);
    }

    @Override
    public void onChannelDone(TLRPC.Chat chat, int channelFollowers, int done, int total) {
        channelsDone = done;
        channelsTotal = total;
        doneLines.add(chat.title + " - " + channelFollowers + " followers");
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    public void onComplete(int uniqueTotal, HashMap<Long, Integer> perChannelCounts) {
        comparing = false;
        hasResult = true;
        resultUnique = uniqueTotal;
        resultPerChannel = perChannelCounts;
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    public void onError() {
        comparing = false;
        if (getParentActivity() != null) {
            BulletinFactory.of(this).createSimpleBulletin(R.drawable.msg_retry, "Couldn't compare these channels.").show();
        }
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
