/*
 * Mayogram: simple "About" screen opened from the Settings row of the same
 * name - circular app icon + "MayoGram X.X.X" at the top, then Categories
 * (General / Appearance / Local Premium) and a Links section with a row
 * that opens the official @mayogram channel.
 */

package org.telegram.ui;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.Outline;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

import static org.telegram.messenger.AndroidUtilities.dp;

public class MayogramInfoActivity extends UniversalFragment {

    private static final int BUTTON_GENERAL = 1;
    private static final int BUTTON_APPEARANCE = 2;
    private static final int BUTTON_LOCAL_PREMIUM = 3;
    private static final int BUTTON_MENUS = 5;
    private static final int BUTTON_FOLLOWERS = 6;
    private static final int BUTTON_CHANNEL = 4;
    private static final int BUTTON_REPORT_BUG = 7;

    @Override
    protected CharSequence getTitle() {
        return "Mayogram";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(createHeaderView(getContext())));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("CATEGORIES"));
        items.add(UItem.asButton(BUTTON_GENERAL, R.drawable.msg_settings, "General"));
        items.add(UItem.asButton(BUTTON_APPEARANCE, R.drawable.msg_palette, "Appearance"));
        items.add(UItem.asButton(BUTTON_LOCAL_PREMIUM, R.drawable.msg_premium_badge, "Local Premium"));
        items.add(UItem.asButton(BUTTON_MENUS, R.drawable.msg_permissions, "Menus"));
        items.add(UItem.asButton(BUTTON_FOLLOWERS, R.drawable.msg_stats, "Followers"));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("LINKS"));
        items.add(UItem.asButton(BUTTON_CHANNEL, R.drawable.msg2_discussion, "Channel", "@mayogram"));
        items.add(UItem.asButton(BUTTON_REPORT_BUG, R.drawable.msg_report, "Found Any Bugs"));
        items.add(UItem.asShadow(null));
    }

    private View createHeaderView(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(0, dp(24), 0, dp(24));
        layout.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

        ImageView iconView = new ImageView(context);
        try {
            iconView.setImageDrawable(context.getResources().getDrawable(R.mipmap.icon_mayo_foreground).mutate());
        } catch (Exception ignore) {
        }
        iconView.setClipToOutline(true);
        iconView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        layout.addView(iconView, LayoutHelper.createLinear(100, 100, Gravity.CENTER_HORIZONTAL));

        TextView nameView = new TextView(context);
        nameView.setText("MayoGram " + getAppVersionName());
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        nameView.setTypeface(AndroidUtilities.bold());
        nameView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        nameView.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams params = LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
        params.topMargin = dp(12);
        layout.addView(nameView, params);

        FrameLayout wrapper = new FrameLayout(context);
        wrapper.addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return wrapper;
    }

    private String getAppVersionName() {
        try {
            PackageInfo pInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            return pInfo.versionName;
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == BUTTON_GENERAL) {
            presentFragment(new MayogramGeneralActivity());
        } else if (item.id == BUTTON_APPEARANCE) {
            presentFragment(new MayogramCustomizeActivity());
        } else if (item.id == BUTTON_LOCAL_PREMIUM) {
            presentFragment(new MayogramLocalPremiumActivity());
        } else if (item.id == BUTTON_MENUS) {
            presentFragment(new MayogramMenusActivity());
        } else if (item.id == BUTTON_FOLLOWERS) {
            presentFragment(new MayogramUniqueFollowersActivity());
        } else if (item.id == BUTTON_CHANNEL) {
            getMessagesController().openByUserName("mayogram", this, 1);
        } else if (item.id == BUTTON_REPORT_BUG) {
            Browser.openUrl(getContext(), "https://forms.gle/V6M2LjA3MHchUroX7");
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
