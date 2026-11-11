/*
 * Mayogram: Settings -> Mayogram -> Followers. Scans every channel the
 * account owns and shows how many people follow at least one of them
 * (someone following several owned channels only counts once), then lists
 * every one of them inline.
 */
package org.telegram.ui;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MayogramCustomization;
import org.telegram.messenger.MayogramExportController;
import org.telegram.messenger.MayogramFollowersController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

import static org.telegram.messenger.AndroidUtilities.dp;

public class MayogramUniqueFollowersActivity extends UniversalFragment implements MayogramFollowersController.ScanListener {

    private static final int BUTTON_RESCAN = 2;
    private static final int BUTTON_EXPORT = 3;

    /** A scan older than this is treated as stale and auto-refreshed on open. */
    private static final long STALE_AFTER_MS = 24 * 60 * 60 * 1000L;

    private int progressDone;
    private int progressTotal;
    private int progressUnique;
    private boolean exporting;

    private static class ScannedChannel {
        final long chatId;
        final int followers;
        ScannedChannel(long chatId, int followers) {
            this.chatId = chatId;
            this.followers = followers;
        }
    }
    private final ArrayList<ScannedChannel> scannedChannels = new ArrayList<>();

    @Override
    protected CharSequence getTitle() {
        return "Followers";
    }

    @Override
    public boolean onFragmentCreate() {
        boolean result = super.onFragmentCreate();
        MayogramFollowersController.setListener(getCurrentAccount(), this);
        MayogramFollowersController.ensureUsersCached(getCurrentAccount(), MayogramCustomization.getFollowersIds(getCurrentAccount()), () -> {
            if (listView != null) {
                listView.adapter.update(true);
            }
        });
        long lastScan = MayogramCustomization.getFollowersLastScan(getCurrentAccount());
        boolean stale = lastScan == 0 || System.currentTimeMillis() - lastScan > STALE_AFTER_MS;
        if (!MayogramFollowersController.isScanning(getCurrentAccount()) && stale) {
            startScanWithNotice();
        }
        return result;
    }

    @Override
    public void onFragmentDestroy() {
        MayogramFollowersController.removeListener(getCurrentAccount(), this);
        super.onFragmentDestroy();
    }

    private void startScanWithNotice() {
        scannedChannels.clear();
        BulletinFactory.of(this).createSimpleBulletin(R.drawable.msg_retry,
                "Scanning your owned channels - this can take a while. We'll let you know when it's done.").show();
        MayogramFollowersController.startScan(getCurrentAccount());
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    private View buildCenteredHeader(Context context, String text) {
        TextView textView = new TextView(context);
        textView.setText(text);
        textView.setGravity(Gravity.CENTER);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        textView.setPadding(dp(16), dp(12), dp(16), dp(8));
        return textView;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        int account = getCurrentAccount();
        if (MayogramFollowersController.isDiscoveringChannels(account)) {
            items.add(UItem.asCustom(buildCenteredHeader(getContext(), "FINDING YOUR CHANNELS")));
            items.add(UItem.asShadow("Looking for every channel you own before scanning their followers..."));
        } else if (MayogramFollowersController.isScanning(account)) {
            items.add(UItem.asCustom(buildCenteredHeader(getContext(), "SCANNING")));
            String text = progressTotal > 0
                    ? progressDone + " of " + progressTotal + " channels - " + progressUnique + " unique followers"
                    : "Starting...";
            items.add(UItem.asShadow(text));
            // Newest-scanned channel goes on top, so the list reads like a
            // feed of what just finished rather than growing downward.
            for (int i = scannedChannels.size() - 1; i >= 0; i--) {
                ScannedChannel scanned = scannedChannels.get(i);
                TLRPC.Chat chat = getMessagesController().getChat(scanned.chatId);
                if (chat != null) {
                    UItem item = UItem.asProfileCell(chat);
                    item.subtext = scanned.followers + (scanned.followers == 1 ? " follower" : " followers");
                    items.add(item);
                }
            }
        } else if (MayogramCustomization.getFollowersLastScan(account) != 0) {
            items.add(UItem.asButton(0, "Followers", String.valueOf(MayogramCustomization.getFollowersTotal(account))));
            items.add(UItem.asButton(0, "Unique Followers", String.valueOf(MayogramCustomization.getFollowersUnique(account))));
            items.add(UItem.asShadow("Last scanned " + formatAgo(MayogramCustomization.getFollowersLastScan(account)) + "."));

            items.add(UItem.asButton(BUTTON_RESCAN, "Rescan"));
            items.add(UItem.asButton(BUTTON_EXPORT, exporting ? "Exporting..." : "Export as CSV"));
            items.add(UItem.asShadow(null));

            items.add(UItem.asHeader("UNIQUE FOLLOWERS LIST"));
            ArrayList<Long> ids = MayogramCustomization.getFollowersIds(account);
            for (int i = 0; i < ids.size(); i++) {
                long id = ids.get(i);
                TLRPC.User user = getMessagesController().getUser(id);
                if (user == null) {
                    user = new TLRPC.TL_user();
                    user.id = id;
                }
                items.add(UItem.asProfileCell(user));
            }
            if (ids.isEmpty()) {
                items.add(UItem.asShadow("No followers found."));
            }
        } else {
            items.add(UItem.asHeader("FOLLOWERS"));
            items.add(UItem.asShadow("Starting scan..."));
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == BUTTON_RESCAN) {
            startScanWithNotice();
        } else if (item.id == BUTTON_EXPORT) {
            startExport();
        }
    }

    private void startExport() {
        if (exporting) {
            return;
        }
        exporting = true;
        if (listView != null) {
            listView.adapter.update(true);
        }
        int account = getCurrentAccount();
        MayogramExportController.exportFollowersToCsv(account, MayogramCustomization.getFollowersIds(account), new MayogramExportController.ExportCallback() {
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
                    BulletinFactory.of(MayogramUniqueFollowersActivity.this).createSimpleBulletin(R.drawable.msg_retry, "Export failed.").show();
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
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                try {
                    intent.putExtra(android.content.Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(getParentActivity(), org.telegram.messenger.ApplicationLoader.getApplicationId() + ".provider", file));
                    intent.setFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignore) {
                    intent.putExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri.fromFile(file));
                }
            } else {
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

    private static String formatAgo(long timeMs) {
        long diff = System.currentTimeMillis() - timeMs;
        long minutes = diff / 60000;
        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        }
        long days = hours / 24;
        return days + (days == 1 ? " day ago" : " days ago");
    }

    @Override
    public void onChannelsDiscovered(int channelCount) {
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    public void onProgress(int channelsDone, int channelsTotal, int uniqueSoFar) {
        progressDone = channelsDone;
        progressTotal = channelsTotal;
        progressUnique = uniqueSoFar;
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    public void onChannelScanned(TLRPC.Chat chat, int channelFollowers, int channelsDone, int channelsTotal) {
        scannedChannels.add(new ScannedChannel(chat.id, channelFollowers));
        if (listView != null) {
            listView.adapter.update(true);
            // Each new channel lands at the top of the list, so once there
            // are more than fit on screen, jump back to the top with it -
            // otherwise it appears above whatever's currently in view and
            // the user has to manually scroll up every time to see it.
            listView.layoutManager.scrollToPositionWithOffset(0, 0);
        }
    }

    @Override
    public void onComplete(int totalFollowers, int uniqueFollowers) {
        if (listView != null) {
            listView.adapter.update(true);
        }
        if (getParentActivity() != null) {
            BulletinFactory.of(this).createSimpleBulletin(R.drawable.msg_retry,
                    "Scan complete - " + uniqueFollowers + " unique followers").show();
        }
    }
}
