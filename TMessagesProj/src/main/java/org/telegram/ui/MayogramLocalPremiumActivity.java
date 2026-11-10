/*
 * Mayogram: Settings -> Mayogram -> Local Premium.
 *
 * This is a device-only cosmetic toggle. It does NOT purchase or grant real
 * Telegram Premium, does not change the account's premium flag on the
 * server, and is not visible to anyone else - on any other device or
 * client (including this same account opened in stock Telegram), the
 * account still looks and behaves like a normal free account.
 */
package org.telegram.ui;

import android.view.View;

import org.telegram.messenger.MayogramCustomization;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class MayogramLocalPremiumActivity extends UniversalFragment {

    private static final int SWITCH_LOCAL_PREMIUM = 1;

    @Override
    protected CharSequence getTitle() {
        return "Local Premium";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader("LOCAL PREMIUM"));
        items.add(UItem.asSwitch(SWITCH_LOCAL_PREMIUM, "Local Premium")
                .setChecked(MayogramCustomization.isLocalPremiumEnabled()));
        items.add(UItem.asShadow(
            "On this device only, lets you:\n" +
            "• Set an emoji status\n" +
            "• See animated emoji in messages\n" +
            "• Show a profile badge\n\n" +
            "This is local to this app only - it does not buy or activate real Telegram Premium. " +
            "Opening this same account in stock Telegram (or any other client) shows a normal, " +
            "non-Premium account."
        ));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == SWITCH_LOCAL_PREMIUM) {
            MayogramCustomization.setLocalPremiumEnabled(!MayogramCustomization.isLocalPremiumEnabled());
            listView.adapter.update(true);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
