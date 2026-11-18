/*
 * Mayogram: "Get Channel IDs" row in Settings -> Mayogram, below Followers.
 * Lists every channel/group the account is in, searchable by title, with a
 * checkbox per row. Tapping Export (top right) writes a CSV of whatever is
 * selected - no, channel_name, channel_id - and opens the share sheet. If
 * nothing is selected, exports the full (filtered) list instead.
 */
package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.EditText;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MayogramExportController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class MayogramChannelIdsActivity extends UniversalFragment {

    private static final int BUTTON_EXPORT = 1;

    private String query = "";
    private final LinkedHashSet<Long> selectedIds = new LinkedHashSet<>();
    private boolean exporting;

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
                } else if (id == BUTTON_EXPORT) {
                    startExport();
                }
            }
        });
        actionBar.createMenu().addItem(BUTTON_EXPORT, exporting ? "Exporting..." : "Export");
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
        List<TLRPC.Chat> chats = collectChats();

        items.add(UItem.asShadow(selectedIds.isEmpty()
                ? "Select channels/groups below, or leave nothing selected to export the whole list."
                : selectedIds.size() + " selected."));

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
            item.checked = selectedIds.contains(chat.id);
            items.add(item);
        }
        if (shown == 0) {
            items.add(UItem.asShadow(q.isEmpty() ? "No channels or groups found." : "No matches for \"" + query.trim() + "\"."));
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

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.object instanceof TLRPC.Chat) {
            long id = ((TLRPC.Chat) item.object).id;
            if (!selectedIds.remove(id)) {
                selectedIds.add(id);
            }
            if (listView != null) {
                listView.adapter.update(true);
            }
        }
    }

    private void startExport() {
        if (exporting) {
            return;
        }
        List<TLRPC.Chat> chats = collectChats();
        List<TLRPC.Chat> toExport = new ArrayList<>();
        if (selectedIds.isEmpty()) {
            toExport.addAll(chats);
        } else {
            for (TLRPC.Chat chat : chats) {
                if (selectedIds.contains(chat.id)) {
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
        if (listView != null) {
            listView.adapter.update(true);
        }
        MayogramExportController.exportChatsToCsv(toExport, new MayogramExportController.ExportCallback() {
            @Override
            public void onDone(java.io.File csvFile) {
                exporting = false;
                if (listView != null) {
                    listView.adapter.update(true);
                }
                shareCsv(csvFile);
            }

            @Override
            public void onError() {
                exporting = false;
                if (listView != null) {
                    listView.adapter.update(true);
                }
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
}
