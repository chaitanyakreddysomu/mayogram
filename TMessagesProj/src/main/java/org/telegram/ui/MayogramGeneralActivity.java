/*
 * Mayogram: Settings -> Mayogram -> General. Small general-purpose toggles
 * that don't fit under Appearance, plus the Support Us link.
 */
package org.telegram.ui;

import android.view.View;

import org.telegram.messenger.MayogramCustomization;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class MayogramGeneralActivity extends UniversalFragment {

    private static final int SWITCH_TIME_SECONDS = 1;
    private static final int BUTTON_SUPPORT = 2;
    private static final int BUTTON_UNIQUE_FOLLOWERS = 3;

    private static final String SUPPORT_URL = "https://buymeatea.in/mayogram";

    @Override
    protected CharSequence getTitle() {
        return "General";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader("MESSAGE TIME"));
        items.add(UItem.asSwitch(SWITCH_TIME_SECONDS, "Format time with seconds")
                .setChecked(MayogramCustomization.isTimeWithSecondsEnabled()));
        items.add(UItem.asShadow("Format time from 12:34 to 12:34:56."));

        items.add(UItem.asHeader("CHANNELS"));
        items.add(UItem.asButton(BUTTON_UNIQUE_FOLLOWERS, R.drawable.msg_stats, "Unique Followers"));
        items.add(UItem.asShadow("Scans every channel you own and counts how many people follow at least one of them."));

        items.add(UItem.asButton(BUTTON_SUPPORT, R.drawable.media_like, "Support Us ❤️", SUPPORT_URL));
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == SWITCH_TIME_SECONDS) {
            MayogramCustomization.setTimeWithSecondsEnabled(!MayogramCustomization.isTimeWithSecondsEnabled());
            listView.adapter.update(true);
        } else if (item.id == BUTTON_SUPPORT) {
            Browser.openUrl(getContext(), SUPPORT_URL);
        } else if (item.id == BUTTON_UNIQUE_FOLLOWERS) {
            presentFragment(new MayogramUniqueFollowersActivity());
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
