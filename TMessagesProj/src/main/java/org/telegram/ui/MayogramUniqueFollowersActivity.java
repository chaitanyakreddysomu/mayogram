/*
 * Mayogram: Settings -> Mayogram -> General -> Unique Followers. Scans every
 * channel the account owns and shows how many people follow at least one of
 * them (someone following several owned channels only counts once).
 */
package org.telegram.ui;

import android.view.View;

import org.telegram.messenger.MayogramCustomization;
import org.telegram.messenger.MayogramExportController;
import org.telegram.messenger.MayogramFollowersController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class MayogramUniqueFollowersActivity extends UniversalFragment implements MayogramFollowersController.ScanListener {

    private static final int BUTTON_VIEW_LIST = 1;
    private static final int BUTTON_RESCAN = 2;
    private static final int BUTTON_EXPORT = 3;

    /** A scan older than this is treated as stale and auto-refreshed on open. */
    private static final long STALE_AFTER_MS = 24 * 60 * 60 * 1000L;

    private int progressDone;
    private int progressTotal;
    private int progressUnique;
    private boolean exporting;

    @Override
    protected CharSequence getTitle() {
        return "Unique Followers";
    }

    @Override
    public boolean onFragmentCreate() {
        boolean result = super.onFragmentCreate();
        MayogramFollowersController.setListener(getCurrentAccount(), this);
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
        BulletinFactory.of(this).createSimpleBulletin(R.drawable.msg_retry,
                "Scanning your owned channels - this can take a while. We'll let you know when it's done.").show();
        MayogramFollowersController.startScan(getCurrentAccount());
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        int account = getCurrentAccount();
        if (MayogramFollowersController.isScanning(account)) {
            items.add(UItem.asHeader("SCANNING"));
            String text = progressTotal > 0
                    ? "Channel " + (progressDone + 1) + " of " + progressTotal + " - " + progressUnique + " unique so far"
                    : "Starting...";
            items.add(UItem.asShadow(text));
        } else if (MayogramCustomization.getFollowersLastScan(account) != 0) {
            items.add(UItem.asHeader("RESULTS"));
            items.add(UItem.asButton(0, "Followers", String.valueOf(MayogramCustomization.getFollowersTotal(account))));
            items.add(UItem.asButton(0, "Unique Followers", String.valueOf(MayogramCustomization.getFollowersUnique(account))));
            items.add(UItem.asShadow("\"Followers\" adds up every owned channel's subscriber count. \"Unique Followers\" counts each person once, even if they follow more than one of your channels. Last scanned "
                    + formatAgo(MayogramCustomization.getFollowersLastScan(account)) + ". Rescans automatically when this is over a day old."));

            items.add(UItem.asButton(BUTTON_VIEW_LIST, "View All Followers"));
            items.add(UItem.asButton(BUTTON_RESCAN, "Rescan"));
            items.add(UItem.asButton(BUTTON_EXPORT, exporting ? "Exporting..." : "Export as CSV"));
            items.add(UItem.asShadow("Exports telegram_userid, username, first_name, last_name, phone_number as a CSV you can open in Excel or share. Phone numbers will be blank for almost everyone - Telegram only shares a phone number with accounts that already have that person as a contact."));
        } else {
            items.add(UItem.asHeader("UNIQUE FOLLOWERS"));
            items.add(UItem.asShadow("Starting scan..."));
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == BUTTON_VIEW_LIST) {
            presentFragment(new MayogramFollowersListActivity());
        } else if (item.id == BUTTON_RESCAN) {
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
    public void onProgress(int channelsDone, int channelsTotal, int uniqueSoFar) {
        progressDone = channelsDone;
        progressTotal = channelsTotal;
        progressUnique = uniqueSoFar;
        if (listView != null) {
            listView.adapter.update(true);
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
