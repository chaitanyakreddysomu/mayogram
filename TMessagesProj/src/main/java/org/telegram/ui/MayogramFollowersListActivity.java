/*
 * Mayogram: Settings -> Mayogram -> General -> Unique Followers -> View All
 * Followers. Lists every unique follower found by the last scan. Users not
 * already cached locally show with a placeholder name (their id) rather
 * than triggering extra network lookups for potentially thousands of users.
 */
package org.telegram.ui;

import org.telegram.messenger.MayogramCustomization;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class MayogramFollowersListActivity extends UniversalFragment {

    @Override
    protected CharSequence getTitle() {
        return "All Followers";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        int account = getCurrentAccount();
        ArrayList<Long> ids = MayogramCustomization.getFollowersIds(account);
        items.add(UItem.asHeader(ids.size() + " unique followers"));
        for (int i = 0; i < ids.size(); i++) {
            long id = ids.get(i);
            TLRPC.User user = MessagesController.getInstance(account).getUser(id);
            if (user == null) {
                user = new TLRPC.TL_user();
                user.id = id;
                user.first_name = "User " + id;
            }
            items.add(UItem.asProfileCell(user));
        }
        if (ids.isEmpty()) {
            items.add(UItem.asShadow("No followers found."));
        }
    }

    @Override
    protected void onClick(UItem item, android.view.View view, int position, float x, float y) {
    }

    @Override
    protected boolean onLongClick(UItem item, android.view.View view, int position, float x, float y) {
        return false;
    }
}
