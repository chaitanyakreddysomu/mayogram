/*
 * Mayogram: Settings -> Mayogram -> General -> Unique Followers. Scans every
 * channel the account owns and shows how many people follow at least one of
 * them (someone following several owned channels only counts once).
 */
package org.telegram.ui;

import android.view.View;

import org.telegram.messenger.MayogramCustomization;
import org.telegram.messenger.MayogramFollowersController;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class MayogramUniqueFollowersActivity extends UniversalFragment implements MayogramFollowersController.ScanListener {

    private static final int BUTTON_VIEW_LIST = 1;
    private static final int BUTTON_RESCAN = 2;

    private int progressDone;
    private int progressTotal;
    private int progressUnique;

    @Override
    protected CharSequence getTitle() {
        return "Unique Followers";
    }

    @Override
    public void onFragmentCreate() {
        super.onFragmentCreate();
        MayogramFollowersController.setListener(getCurrentAccount(), this);
        if (!MayogramFollowersController.isScanning(getCurrentAccount()) && MayogramCustomization.getFollowersLastScan(getCurrentAccount()) == 0) {
            startScanWithNotice();
        }
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
            items.add(UItem.asShadow("\"Followers\" adds up every owned channel's subscriber count. \"Unique Followers\" counts each person once, even if they follow more than one of your channels."));

            items.add(UItem.asButton(BUTTON_VIEW_LIST, "View All Followers"));
            items.add(UItem.asButton(BUTTON_RESCAN, "Rescan"));
            items.add(UItem.asShadow(null));
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
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
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
