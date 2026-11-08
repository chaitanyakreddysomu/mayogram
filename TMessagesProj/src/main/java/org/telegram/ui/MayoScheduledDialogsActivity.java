/*
 * Mayogram: "Scheduled Messages" dashboard, reachable from the same overflow
 * menu that has Saved Messages/Updates. Shows one row per chat that has at
 * least one scheduled message, laid out like a normal chat list (avatar,
 * chat name, latest scheduled message as the preview, its date) - modeled
 * on SharedMediaLayout's SavedDialogsAdapter, which binds plain DialogCell
 * rows to a list of (dialogId, MessageObject) pairs with no real
 * DialogsActivity behind them. Tapping a row opens that chat's existing
 * Scheduled Messages view (ChatActivity in MODE_SCHEDULED), the same way
 * ChatActivity.openScheduledMessages() does internally.
 */

package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class MayoScheduledDialogsActivity extends BaseFragment {

    private RecyclerListView listView;
    private EmptyTextProgressView emptyView;
    private ListAdapter listAdapter;
    private final ArrayList<Long> dialogIds = new ArrayList<>();
    private final LongSparseArray<MessageObject> previews = new LongSparseArray<>();

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.MayoScheduledMessages));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        emptyView = new EmptyTextProgressView(context);
        emptyView.setText(LocaleController.getString(R.string.MayoScheduledMessagesEmpty));
        emptyView.showProgress();
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listAdapter = new ListAdapter(context);
        listView = new RecyclerListView(context);
        listView.setFocusable(true);
        listView.setEmptyView(emptyView);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= dialogIds.size()) {
                return;
            }
            openScheduledForDialog(dialogIds.get(position));
        });
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        load();

        return fragmentView;
    }

    private void openScheduledForDialog(long dialogId) {
        if (getParentActivity() == null) {
            return;
        }
        Bundle bundle = new Bundle();
        if (dialogId > 0) {
            bundle.putLong("user_id", dialogId);
        } else {
            bundle.putLong("chat_id", -dialogId);
        }
        bundle.putInt("chatMode", ChatActivity.MODE_SCHEDULED);
        presentFragment(new ChatActivity(bundle));
    }

    private void load() {
        getMessagesStorage().loadDialogsWithScheduledMessages(currentAccount, result -> {
            dialogIds.clear();
            previews.clear();
            for (int i = 0; i < result.size(); i++) {
                long dialogId = result.keyAt(i);
                dialogIds.add(dialogId);
                previews.put(dialogId, result.valueAt(i));
            }
            Collections.sort(dialogIds, (a, b) -> {
                MessageObject ma = previews.get(a);
                MessageObject mb = previews.get(b);
                int da = ma != null && ma.messageOwner != null ? ma.messageOwner.date : 0;
                int db = mb != null && mb.messageOwner != null ? mb.messageOwner.date : 0;
                return Integer.compare(db, da);
            });
            if (emptyView != null) {
                emptyView.showTextView();
            }
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        });
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return dialogIds.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            DialogCell cell = new DialogCell(null, mContext, false, true, currentAccount, null);
            cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            DialogCell cell = (DialogCell) holder.itemView;
            long dialogId = dialogIds.get(position);
            MessageObject messageObject = previews.get(dialogId);
            int date = messageObject != null && messageObject.messageOwner != null ? messageObject.messageOwner.date : 0;
            cell.setDialog(dialogId, messageObject, date, false, false);
            cell.useSeparator = position + 1 < dialogIds.size();
        }
    }
}
