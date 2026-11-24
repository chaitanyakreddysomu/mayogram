/*
 * Mayogram: "Compare" row in Settings -> Mayogram, below Followers. Pick 2+
 * of your owned channels (same avatar+tick row style as the other
 * multi-select Mayogram screens), scan them, and see how many distinct
 * people follow at least one of the selected channels, plus each channel's
 * own follower count.
 */
package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MayogramCompareController;
import org.telegram.messenger.MayogramCustomization;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
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
import java.util.LinkedHashSet;
import java.util.List;

public class MayogramCompareActivity extends UniversalFragment implements MayogramCompareController.Listener {

    private final LinkedHashSet<Long> selectedChatIds = new LinkedHashSet<>();
    private FragmentFloatingButton floatingButton;

    private boolean comparing;
    private int channelsDone;
    private int channelsTotal;
    private final ArrayList<String> doneLines = new ArrayList<>();

    private boolean hasResult;
    private int resultUnique;
    private HashMap<Long, Integer> resultPerChannel;

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
        return result;
    }

    @Override
    public View createView(Context context) {
        View view = super.createView(context);
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
        List<TLRPC.Chat> chats = collectOwnedChannels();

        if (hasResult && !comparing) {
            items.add(UItem.asHeader("RESULT"));
            items.add(UItem.asShadow("Unique followers across the " + resultPerChannel.size() + " channels you compared: " + resultUnique));
            for (int i = 0; i < chats.size(); i++) {
                TLRPC.Chat chat = chats.get(i);
                Integer count = resultPerChannel.get(chat.id);
                if (count != null) {
                    items.add(UItem.asCustom(buildResultRow(getContext(), chat, count)));
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
                ? "Select 2 or more of your owned channels, then tap the checkmark (bottom right)."
                : selectedChatIds.size() + " selected" + (selectedChatIds.size() < 2 ? " - pick at least 2." : ".")));

        if (!selectedChatIds.isEmpty()) {
            items.add(UItem.asCustom(buildSelectedStrip(getContext(), chats)));
        }

        if (chats.isEmpty()) {
            items.add(UItem.asShadow("No owned channels found yet - run a scan on the Followers screen first, so Mayogram knows which channels you own."));
            return;
        }
        for (int i = 0; i < chats.size(); i++) {
            items.add(UItem.asCustom(buildChatRow(getContext(), chats.get(i))));
        }
    }

    private View buildResultRow(Context context, TLRPC.Chat chat, int count) {
        UserCell cell = new UserCell(context, 16, 0, false);
        cell.setData(chat, null, count + " followers", 0, false);
        return cell;
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

    private List<TLRPC.Chat> collectOwnedChannels() {
        int account = getCurrentAccount();
        MessagesController controller = MessagesController.getInstance(account);
        ArrayList<Long> ownedIds = MayogramCustomization.getOwnedChannelIds(account);
        ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        for (int i = 0; i < ownedIds.size(); i++) {
            TLRPC.Chat chat = controller.getChat(ownedIds.get(i));
            if (chat != null) {
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
