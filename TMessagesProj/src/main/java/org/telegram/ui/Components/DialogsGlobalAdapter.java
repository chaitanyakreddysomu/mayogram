/*
 * Mayogram: backs the "Global" search tab - public chats, channels and users
 * matching the query that you have NOT already joined/added, plus matching
 * messages from public chats. Modeled on DialogsChannelsAdapter, but without
 * its channels-only (broadcasts_only) restriction.
 */
package org.telegram.ui.Components;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.HashSet;

public class DialogsGlobalAdapter extends UniversalAdapter {

    private final Context context;
    private final int currentAccount;
    private final int folderId;
    private final Theme.ResourcesProvider resourcesProvider;

    public final ArrayList<MessageObject> messages = new ArrayList<>();
    public final ArrayList<TLObjectHolder> searchPeers = new ArrayList<>();
    public boolean expandedSearchPeers;

    /** Wraps either a TLRPC.Chat or a TLRPC.User so both can share one list/UItem row. */
    public static class TLObjectHolder {
        public final TLRPC.Chat chat;
        public final TLRPC.User user;
        TLObjectHolder(TLRPC.Chat chat) { this.chat = chat; this.user = null; }
        TLObjectHolder(TLRPC.User user) { this.chat = null; this.user = user; }
    }

    public DialogsGlobalAdapter(RecyclerListView listView, Context context, int currentAccount, int folderId, Theme.ResourcesProvider resourcesProvider) {
        super(listView, context, currentAccount, 0, null, resourcesProvider);
        super.fillItems = this::fillItems;
        this.context = context;
        this.currentAccount = currentAccount;
        this.folderId = folderId;
        this.resourcesProvider = resourcesProvider;
        update(false);
    }

    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (TextUtils.isEmpty(query)) {
            return;
        }
        if (!searchPeers.isEmpty()) {
            if (searchPeers.size() > 5 && !messages.isEmpty()) {
                items.add(UItem.asGraySection(getString(R.string.GlobalSearch), getString(expandedSearchPeers ? R.string.ShowLess : R.string.ShowMore), this::toggleExpandedSearchPeers));
            } else {
                items.add(UItem.asGraySection(getString(R.string.GlobalSearch)));
            }
            int count = searchPeers.size();
            if (!expandedSearchPeers && !messages.isEmpty()) {
                count = Math.min(5, count);
            }
            for (int i = 0; i < count; ++i) {
                TLObjectHolder holder = searchPeers.get(i);
                items.add(UItem.asProfileCell(holder.chat != null ? holder.chat : holder.user));
            }
        }
        if (!messages.isEmpty()) {
            items.add(UItem.asGraySection(getString(R.string.SearchMessages)));
            for (MessageObject message : messages) {
                items.add(UItem.asSearchMessage(message));
            }
            if (hasMore) {
                items.add(UItem.asFlicker(FlickerLoadingView.DIALOG_TYPE));
            }
        }
    }

    public void toggleExpandedSearchPeers(View view) {
        expandedSearchPeers = !expandedSearchPeers;
        update(true);
        if (expandedSearchPeers) {
            hideKeyboard();
        }
    }

    protected void hideKeyboard() {
    }

    public Object getObject(int position) {
        UItem item = getItem(position);
        return item != null ? item.object : null;
    }

    public boolean loadingMessages;
    public boolean loadingPeers;

    private boolean hasMore;
    private int nextRate;
    private int searchId;
    public String query;

    private void searchMessages(boolean next) {
        loadingMessages = true;
        final int id = ++searchId;
        TLRPC.TL_messages_searchGlobal req = new TLRPC.TL_messages_searchGlobal();
        if (folderId != 0) {
            req.flags |= 1;
            req.folder_id = folderId;
        }
        req.q = this.query;
        req.limit = 25;
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        if (next && !messages.isEmpty()) {
            MessageObject lastMessage = messages.get(messages.size() - 1);
            req.offset_rate = nextRate;
            req.offset_id = lastMessage.getId();
            if (lastMessage.messageOwner.peer_id == null) {
                req.offset_peer = new TLRPC.TL_inputPeerEmpty();
            } else {
                req.offset_peer = MessagesController.getInstance(currentAccount).getInputPeer(lastMessage.messageOwner.peer_id);
            }
        } else {
            req.offset_rate = 0;
            req.offset_id = 0;
            req.offset_peer = new TLRPC.TL_inputPeerEmpty();
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (id != searchId || !TextUtils.equals(req.q, this.query)) return;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (id != searchId || !TextUtils.equals(req.q, this.query)) return;
                loadingMessages = false;
                if (!next) {
                    messages.clear();
                }
                if (res instanceof TLRPC.messages_Messages) {
                    TLRPC.messages_Messages response = (TLRPC.messages_Messages) res;
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(response.users, response.chats, true, true);
                    MessagesController.getInstance(currentAccount).putUsers(response.users, false);
                    MessagesController.getInstance(currentAccount).putChats(response.chats, false);

                    for (TLRPC.Message message : response.messages) {
                        MessageObject messageObject = new MessageObject(currentAccount, message, false, true);
                        messageObject.setQuery(query);
                        messages.add(messageObject);
                    }

                    hasMore = response instanceof TLRPC.TL_messages_messagesSlice;
                    nextRate = response.next_rate;
                }
                update(true);
            }));
        }, next ? 800 : 0);

        if (!next) {
            loadingPeers = true;
            TLRPC.TL_contacts_search req2 = new TLRPC.TL_contacts_search();
            req2.limit = 20;
            req2.q = this.query;
            ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req2, AndroidUtilities::runOnUIThread, (res, err) -> {
                if (!TextUtils.equals(req2.q, this.query) || TextUtils.isEmpty(this.query)) return;

                loadingPeers = false;
                TLRPC.TL_contacts_found response = null;
                if (res instanceof TLRPC.TL_contacts_found) {
                    response = (TLRPC.TL_contacts_found) res;
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(response.users, response.chats, true, true);
                    MessagesController.getInstance(currentAccount).putUsers(response.users, false);
                    MessagesController.getInstance(currentAccount).putChats(response.chats, false);
                }

                searchPeers.clear();
                if (response != null) {
                    HashSet<Long> seen = new HashSet<>();
                    long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
                    for (TLRPC.Peer peer : response.results) {
                        if (peer.channel_id != 0) {
                            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(peer.channel_id);
                            if (chat == null || seen.contains(-chat.id)) continue;
                            if (!ChatObject.isNotInChat(chat)) continue;
                            seen.add(-chat.id);
                            searchPeers.add(new TLObjectHolder(chat));
                        } else if (peer.chat_id != 0) {
                            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(peer.chat_id);
                            if (chat == null || seen.contains(-chat.id)) continue;
                            if (!ChatObject.isNotInChat(chat)) continue;
                            seen.add(-chat.id);
                            searchPeers.add(new TLObjectHolder(chat));
                        } else if (peer.user_id != 0) {
                            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(peer.user_id);
                            if (user == null || seen.contains(user.id)) continue;
                            if (user.self || user.id == selfId || user.contact) continue;
                            seen.add(user.id);
                            searchPeers.add(new TLObjectHolder(user));
                        }
                    }
                }

                update(true);
            });
        }
    }

    private final Runnable searchMessagesRunnable = () -> searchMessages(false);
    public void search(String query) {
        if (TextUtils.equals(query, this.query)) return;
        this.query = query;
        AndroidUtilities.cancelRunOnUIThread(searchMessagesRunnable);
        if (TextUtils.isEmpty(this.query)) {
            messages.clear();
            searchPeers.clear();
            update(true);
            searchId++;
            loadingMessages = false;
            loadingPeers = false;
            hasMore = false;
            nextRate = 0;
            if (listView != null) {
                listView.scrollToPosition(0);
            }
            return;
        }

        messages.clear();
        searchPeers.clear();

        AndroidUtilities.runOnUIThread(searchMessagesRunnable, 1000);
        loadingMessages = true;
        loadingPeers = true;

        update(true);

        if (listView != null) {
            listView.scrollToPosition(0);
        }
    }

    public void searchMore() {
        if (!hasMore || loadingMessages || TextUtils.isEmpty(this.query)) {
            return;
        }
        searchMessages(true);
    }

    public void checkBottom() {
        if (!hasMore || loadingMessages || TextUtils.isEmpty(this.query) || listView == null)
            return;
        if (seesLoading()) {
            searchMore();
        }
    }

    public boolean seesLoading() {
        if (listView == null) return false;
        for (int i = 0; i < listView.getChildCount(); ++i) {
            View child = listView.getChildAt(i);
            if (child instanceof FlickerLoadingView) {
                return true;
            }
        }
        return false;
    }
}
