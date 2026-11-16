/*
 * Mayogram: backs the "Members" tab in the main search's tab row (alongside
 * Chats, Global Search, Channels, Apps, Posts - see SearchViewPager). Shows
 * each matching person once, followed immediately by the channels you own
 * that they've joined - see MayogramMemberSearchController for the actual
 * channels.getParticipants lookups this groups together.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.MayogramMemberSearchController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class MayogramMemberSearchAdapter extends RecyclerListView.SelectionAdapter {

    public static final int VIEW_TYPE_USER = 0;
    public static final int VIEW_TYPE_CHANNEL = 1;

    private static class Row {
        final TLRPC.User user;
        final TLRPC.Chat channel;
        final boolean lastChannelInGroup;

        Row(TLRPC.User user, TLRPC.Chat channel, boolean lastChannelInGroup) {
            this.user = user;
            this.channel = channel;
            this.lastChannelInGroup = lastChannelInGroup;
        }
    }

    private final Context context;
    private final int currentAccount;
    private final ArrayList<Row> rows = new ArrayList<>();
    private String lastQuery;
    private int generation;

    public MayogramMemberSearchAdapter(Context context, int currentAccount) {
        this.context = context;
        this.currentAccount = currentAccount;
    }

    public void search(String query) {
        if (query != null && query.equals(lastQuery)) {
            return;
        }
        lastQuery = query;
        generation++;
        final int token = generation;
        if (query == null || query.trim().length() < 2) {
            rows.clear();
            notifyDataSetChanged();
            return;
        }
        MayogramMemberSearchController.search(currentAccount, query, matches -> {
            if (token != generation) {
                return;
            }
            LinkedHashMap<Long, TLRPC.User> userById = new LinkedHashMap<>();
            LinkedHashMap<Long, ArrayList<TLRPC.Chat>> channelsByUser = new LinkedHashMap<>();
            for (int a = 0; a < matches.size(); a++) {
                MayogramMemberSearchController.Match m = matches.get(a);
                userById.putIfAbsent(m.user.id, m.user);
                ArrayList<TLRPC.Chat> list = channelsByUser.get(m.user.id);
                if (list == null) {
                    list = new ArrayList<>();
                    channelsByUser.put(m.user.id, list);
                }
                list.add(m.channel);
            }
            rows.clear();
            for (Long uid : channelsByUser.keySet()) {
                TLRPC.User user = userById.get(uid);
                rows.add(new Row(user, null, false));
                ArrayList<TLRPC.Chat> channels = channelsByUser.get(uid);
                for (int a = 0; a < channels.size(); a++) {
                    rows.add(new Row(user, channels.get(a), a == channels.size() - 1));
                }
            }
            notifyDataSetChanged();
        });
    }

    public TLRPC.Chat getChannel(int position) {
        if (position < 0 || position >= rows.size()) {
            return null;
        }
        return rows.get(position).channel;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).channel == null ? VIEW_TYPE_USER : VIEW_TYPE_CHANNEL;
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        return holder.getItemViewType() == VIEW_TYPE_CHANNEL;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        if (viewType == VIEW_TYPE_USER) {
            view = new UserCell(context, 16, 0, false);
        } else {
            view = new TextCell(context, 72, false);
        }
        view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new RecyclerListView.Holder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (row.channel == null) {
            ((UserCell) holder.itemView).setData(row.user, null, null, 0, false);
        } else {
            ((TextCell) holder.itemView).setText(row.channel.title, !row.lastChannelInGroup);
        }
    }
}
