/*
 * Mayogram: opened from the "Channels & Groups" counter row in Settings.
 * Lists exactly the chats counted by ChannelMembershipCounter (plus
 * whatever Rescan/Deep Scan surface since the last sync), filterable by
 * type (Channel/Group) and role (Owner/Admin/Member), and searchable by
 * title. Rescan forces a full dialog re-sync; Deep Scan additionally
 * re-verifies every chat's role directly with the server instead of
 * trusting the locally cached flag, which is how an old channel the normal
 * sync missed, or a stale role, gets caught.
 */
package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.EditText;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MayogramFollowersController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MayogramChannelsGroupsListActivity extends UniversalFragment {

    private static final int BUTTON_RESCAN = 1;
    private static final int BUTTON_DEEP_SCAN = 2;
    private static final int BUTTON_TYPE_FILTER = 3;
    private static final int BUTTON_ROLE_FILTER = 4;

    private static final int TYPE_ALL = 0, TYPE_CHANNELS = 1, TYPE_GROUPS = 2;
    private static final int ROLE_ALL = 0, ROLE_OWNER = 1, ROLE_ADMIN = 2, ROLE_MEMBER = 3;

    private String query = "";
    private int typeFilter = TYPE_ALL;
    private int roleFilter = ROLE_ALL;
    private boolean rescanning;
    private boolean deepScanning;

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
        ArrayList<TLRPC.Chat> chats = collectCandidateChats(account);

        items.add(UItem.asButton(BUTTON_RESCAN, rescanning ? "Rescanning..." : "Rescan"));
        items.add(UItem.asButton(BUTTON_DEEP_SCAN, deepScanning ? "Deep Scanning..." : "Deep Scan"));
        items.add(UItem.asShadow("Rescan re-syncs your dialog list. Deep Scan also re-checks your role on every channel directly with Telegram's server instead of trusting the cached value - this is what catches an old channel that a normal rescan missed."));

        items.add(UItem.asButton(BUTTON_TYPE_FILTER, "Type: " + typeFilterLabel()));
        items.add(UItem.asButton(BUTTON_ROLE_FILTER, "Role: " + roleFilterLabel()));
        items.add(UItem.asShadow(null));

        String q = query.trim().toLowerCase(Locale.getDefault());
        int shown = 0;
        ArrayList<UItem> rows = new ArrayList<>();
        for (int i = 0; i < chats.size(); i++) {
            TLRPC.Chat chat = chats.get(i);
            if (!matchesTypeFilter(chat) || !matchesRoleFilter(chat)) {
                continue;
            }
            if (!q.isEmpty() && (chat.title == null || !chat.title.toLowerCase(Locale.getDefault()).contains(q))) {
                continue;
            }
            shown++;
            UItem item = UItem.asProfileCell(chat);
            item.subtext = roleLabel(chat);
            rows.add(item);
        }
        items.add(UItem.asHeader(shown + " channels & groups"));
        items.addAll(rows);
        if (shown == 0) {
            items.add(UItem.asShadow(q.isEmpty() ? "No channels or groups found." : "No matches for \"" + query.trim() + "\"."));
        }
    }

    private ArrayList<TLRPC.Chat> collectCandidateChats(int account) {
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

    private boolean matchesTypeFilter(TLRPC.Chat chat) {
        switch (typeFilter) {
            case TYPE_CHANNELS:
                return !chat.megagroup;
            case TYPE_GROUPS:
                return chat.megagroup;
            default:
                return true;
        }
    }

    private boolean matchesRoleFilter(TLRPC.Chat chat) {
        switch (roleFilter) {
            case ROLE_OWNER:
                return chat.creator;
            case ROLE_ADMIN:
                return !chat.creator && ChatObject.hasAdminRights(chat);
            case ROLE_MEMBER:
                return !chat.creator && !ChatObject.hasAdminRights(chat);
            default:
                return true;
        }
    }

    private String typeFilterLabel() {
        switch (typeFilter) {
            case TYPE_CHANNELS: return "Channels";
            case TYPE_GROUPS: return "Groups";
            default: return "All";
        }
    }

    private String roleFilterLabel() {
        switch (roleFilter) {
            case ROLE_OWNER: return "Owner";
            case ROLE_ADMIN: return "Admin";
            case ROLE_MEMBER: return "Member";
            default: return "All";
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
        if (item.id == BUTTON_RESCAN) {
            startRescan();
        } else if (item.id == BUTTON_DEEP_SCAN) {
            startDeepScan();
        } else if (item.id == BUTTON_TYPE_FILTER) {
            showTypeFilterPicker();
        } else if (item.id == BUTTON_ROLE_FILTER) {
            showRoleFilterPicker();
        } else if (item.object instanceof TLRPC.Chat) {
            TLRPC.Chat chat = (TLRPC.Chat) item.object;
            android.os.Bundle args = new android.os.Bundle();
            args.putLong("chat_id", chat.id);
            presentFragment(new ChatActivity(args));
        }
    }

    private void showTypeFilterPicker() {
        if (getContext() == null) {
            return;
        }
        String[] options = {"All", "Channels", "Groups"};
        int[] values = {TYPE_ALL, TYPE_CHANNELS, TYPE_GROUPS};
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Filter by Type");
        builder.setItems(options, (dialog, which) -> {
            typeFilter = values[which];
            if (listView != null) {
                listView.adapter.update(true);
            }
        });
        showDialog(builder.create());
    }

    private void showRoleFilterPicker() {
        if (getContext() == null) {
            return;
        }
        String[] options = {"All", "Owner", "Admin", "Member"};
        int[] values = {ROLE_ALL, ROLE_OWNER, ROLE_ADMIN, ROLE_MEMBER};
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Filter by Role");
        builder.setItems(options, (dialog, which) -> {
            roleFilter = values[which];
            if (listView != null) {
                listView.adapter.update(true);
            }
        });
        showDialog(builder.create());
    }

    private void startRescan() {
        if (rescanning || deepScanning) {
            return;
        }
        rescanning = true;
        if (listView != null) {
            listView.adapter.update(true);
        }
        int account = getCurrentAccount();
        MayogramFollowersController.ensureAllDialogsLoaded(account, () -> {
            rescanning = false;
            if (listView != null) {
                listView.adapter.update(true);
            }
            if (getParentActivity() != null) {
                BulletinFactory.of(this).createSimpleBulletin(R.drawable.msg_retry, "Rescan complete.").show();
            }
        });
    }

    private static final int DEEP_SCAN_BATCH_SIZE = 100;

    private void startDeepScan() {
        if (rescanning || deepScanning) {
            return;
        }
        deepScanning = true;
        if (listView != null) {
            listView.adapter.update(true);
        }
        int account = getCurrentAccount();
        MayogramFollowersController.ensureAllDialogsLoaded(account, () -> {
            ArrayList<TLRPC.Chat> candidates = collectCandidateChats(account);
            deepVerifyRoles(account, candidates, 0);
        });
    }

    private void deepVerifyRoles(int account, List<TLRPC.Chat> candidates, int offset) {
        if (offset >= candidates.size()) {
            deepScanning = false;
            if (listView != null) {
                listView.adapter.update(true);
            }
            if (getParentActivity() != null) {
                BulletinFactory.of(this).createSimpleBulletin(R.drawable.msg_retry, "Deep scan complete.").show();
            }
            return;
        }
        int end = Math.min(offset + DEEP_SCAN_BATCH_SIZE, candidates.size());
        TLRPC.TL_channels_getChannels req = new TLRPC.TL_channels_getChannels();
        for (int i = offset; i < end; i++) {
            req.id.add(MessagesController.getInstance(account).getInputChannel(candidates.get(i).id));
        }
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.TL_messages_chats) {
                TLRPC.TL_messages_chats res = (TLRPC.TL_messages_chats) response;
                MessagesController.getInstance(account).putChats(res.chats, false);
            }
            if (listView != null) {
                listView.adapter.update(true);
            }
            deepVerifyRoles(account, candidates, end);
        }));
    }

    @Override
    protected boolean onLongClick(UItem item, android.view.View view, int position, float x, float y) {
        return false;
    }
}
