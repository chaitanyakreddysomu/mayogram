/*
 * Mayogram: "Get Channel IDs" row in Settings -> Mayogram, below Followers.
 * Lists every channel/group the account is in - avatar on the left, name,
 * a square tick mark at the right end of each row. Selected chats also show
 * as a horizontal strip of avatars above the list (tap one there to
 * deselect). The bottom-right floating button exports whatever's selected
 * (or the whole filtered list if nothing's checked) as a CSV
 * (no, channel_name, channel_id) and opens the share sheet.
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
import org.telegram.messenger.MayogramExportController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class MayogramChannelIdsActivity extends UniversalFragment {

    private final LinkedHashSet<Long> selectedChatIds = new LinkedHashSet<>();
    private String query = "";
    private boolean exporting;
    private FragmentFloatingButton floatingButton;

    @Override
    protected CharSequence getTitle() {
        return "Get Channel IDs";
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
            floatingButton.setContentDescription("Export");
            floatingButton.setOnClickListener(v -> startExport());
            ((FrameLayout) view).addView(floatingButton, FragmentFloatingButton.createDefaultLayoutParams());
        }
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        List<TLRPC.Chat> chats = collectChats();

        if (!selectedChatIds.isEmpty()) {
            items.add(UItem.asCustom(buildSelectedStrip(getContext(), chats)));
        }

        items.add(UItem.asShadow(selectedChatIds.isEmpty()
                ? "Select channels/groups below, or leave nothing selected to export the whole list."
                : selectedChatIds.size() + " selected."));

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
            items.add(UItem.asShadow(q.isEmpty() ? "No channels or groups found." : "No matches for \"" + query.trim() + "\"."));
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

    private List<TLRPC.Chat> collectChats() {
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
        return chats;
    }

    private void startExport() {
        if (exporting) {
            return;
        }
        List<TLRPC.Chat> chats = collectChats();
        List<TLRPC.Chat> toExport = new ArrayList<>();
        if (selectedChatIds.isEmpty()) {
            toExport.addAll(chats);
        } else {
            for (TLRPC.Chat chat : chats) {
                if (selectedChatIds.contains(chat.id)) {
                    toExport.add(chat);
                }
            }
        }
        if (toExport.isEmpty()) {
            if (getParentActivity() != null) {
                BulletinFactory.of(this).createSimpleBulletin(R.drawable.msg_retry, "Nothing to export.").show();
            }
            return;
        }
        exporting = true;
        MayogramExportController.exportChatsToCsv(toExport, new MayogramExportController.ExportCallback() {
            @Override
            public void onDone(java.io.File csvFile) {
                exporting = false;
                shareCsv(csvFile);
            }

            @Override
            public void onError() {
                exporting = false;
                if (getParentActivity() != null) {
                    BulletinFactory.of(MayogramChannelIdsActivity.this).createSimpleBulletin(R.drawable.msg_retry, "Export failed.").show();
                }
            }
        });
    }

    private void shareCsv(java.io.File file) {
        if (getParentActivity() == null) {
            return;
        }
        try {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            intent.setType("text/csv");
            try {
                intent.putExtra(android.content.Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(getParentActivity(), org.telegram.messenger.ApplicationLoader.getApplicationId() + ".provider", file));
                intent.setFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignore) {
                intent.putExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri.fromFile(file));
            }
            getParentActivity().startActivity(android.content.Intent.createChooser(intent, "Share CSV"));
        } catch (Exception e) {
            org.telegram.messenger.FileLog.e(e);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
    }
}
