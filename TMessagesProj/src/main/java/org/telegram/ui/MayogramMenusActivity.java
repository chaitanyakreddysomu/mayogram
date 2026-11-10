/*
 * Mayogram: Settings -> Mayogram -> Menus. Lets the user hide items from the
 * app's various overflow ("...") menus. Items marked permanent in each menu's
 * own code (e.g. Night Mode, Saved Messages, Updates on the home screen) are
 * never listed here - they always show, same as before this screen existed.
 *
 * Each section shows a live preview built from the exact same toggle state
 * used to gate the real menu, right above its switches, so it updates the
 * instant a switch is flipped.
 */
package org.telegram.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MayogramCustomization;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.List;

import static org.telegram.messenger.AndroidUtilities.dp;

public class MayogramMenusActivity extends UniversalFragment {

    private static final int SWITCH_SHOW_CALLS_TAB = -1;
    private static final int BUTTON_RESET = -2;

    private final SparseArray<String> idToKey = new SparseArray<>();
    private int nextId = 1;

    private int register(String key) {
        int id = nextId++;
        idToKey.put(id, key);
        return id;
    }

    @Override
    protected CharSequence getTitle() {
        return "Menus";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        idToKey.clear();
        nextId = 1;

        items.add(UItem.asHeader("BOTTOM TABS"));
        items.add(UItem.asCustom(buildTabsPreview(getContext(), getUserConfig().showCallsTab)));
        items.add(UItem.asSwitch(SWITCH_SHOW_CALLS_TAB, "Show Calls Tab").setChecked(getUserConfig().showCallsTab));
        items.add(UItem.asShadow("Chats, Contacts and Profile always show and can't be hidden. This slot shows Calls when on, Settings when off."));

        items.add(UItem.asHeader("HOME SCREEN MENU"));
        List<String> home = new ArrayList<>();
        home.add("Night Mode");
        if (MayogramCustomization.isMenuItemVisible(MayogramCustomization.MENU_HOME_GHOST_MODE)) home.add("Ghost Mode");
        if (MayogramCustomization.isMenuItemVisible(MayogramCustomization.MENU_HOME_NEW_GROUP)) home.add("New Group");
        home.add("Saved Messages");
        if (MayogramCustomization.isMenuItemVisible(MayogramCustomization.MENU_HOME_SCHEDULED)) home.add("Scheduled Messages");
        home.add("Updates");
        items.add(UItem.asCustom(buildListPreview(getContext(), home)));
        addSwitch(items, "Ghost Mode", MayogramCustomization.MENU_HOME_GHOST_MODE);
        addSwitch(items, "New Group", MayogramCustomization.MENU_HOME_NEW_GROUP);
        addSwitch(items, "Scheduled Messages", MayogramCustomization.MENU_HOME_SCHEDULED);
        items.add(UItem.asShadow("Night Mode, Saved Messages and Updates always show and can't be hidden."));

        items.add(UItem.asHeader("CHAT MENU - ADMIN"));
        List<String> chatAdmin = new ArrayList<>();
        chatAdmin.add("Mute");
        chatAdmin.add("Search");
        if (MayogramCustomization.isMenuItemVisible(MayogramCustomization.MENU_CHAT_ADMIN_RESTRICT)) chatAdmin.add("Restricted On");
        if (MayogramCustomization.isMenuItemVisible(MayogramCustomization.MENU_CHAT_ADMIN_BOOST)) chatAdmin.add("Boost Channel");
        if (MayogramCustomization.isMenuItemVisible(MayogramCustomization.MENU_CHAT_ADMIN_GO_TO_TOP)) chatAdmin.add("Go To Top");
        if (MayogramCustomization.isMenuItemVisible(MayogramCustomization.MENU_CHAT_ADMIN_RECENT_ACTIONS)) chatAdmin.add("Recent Actions");
        if (MayogramCustomization.isMenuItemVisible(MayogramCustomization.MENU_CHAT_ADMIN_MEMBERS)) chatAdmin.add("Members");
        if (MayogramCustomization.isMenuItemVisible(MayogramCustomization.MENU_CHAT_ADMIN_INVITE_LINKS)) chatAdmin.add("Invite Links");
        chatAdmin.add("Leave Channel");
        items.add(UItem.asCustom(buildListPreview(getContext(), chatAdmin)));
        addSwitch(items, "Restricted On", MayogramCustomization.MENU_CHAT_ADMIN_RESTRICT);
        addSwitch(items, "Boost Channel", MayogramCustomization.MENU_CHAT_ADMIN_BOOST);
        addSwitch(items, "Go To Top", MayogramCustomization.MENU_CHAT_ADMIN_GO_TO_TOP);
        addSwitch(items, "Recent Actions", MayogramCustomization.MENU_CHAT_ADMIN_RECENT_ACTIONS);
        addSwitch(items, "Members", MayogramCustomization.MENU_CHAT_ADMIN_MEMBERS);
        addSwitch(items, "Invite Links", MayogramCustomization.MENU_CHAT_ADMIN_INVITE_LINKS);
        items.add(UItem.asShadow("Mute, Search and Leave Channel always show and can't be hidden."));

        items.add(UItem.asHeader("CHAT MENU - NOT ADMIN"));
        List<String> chatUser = new ArrayList<>();
        chatUser.add("Mute");
        chatUser.add("Search");
        if (MayogramCustomization.isMenuItemVisible(MayogramCustomization.MENU_CHAT_USER_GO_TO_TOP)) chatUser.add("Go To Top");
        if (MayogramCustomization.isMenuItemVisible(MayogramCustomization.MENU_CHAT_USER_REPORT)) chatUser.add("Report");
        chatUser.add("Leave Channel");
        items.add(UItem.asCustom(buildListPreview(getContext(), chatUser)));
        addSwitch(items, "Go To Top", MayogramCustomization.MENU_CHAT_USER_GO_TO_TOP);
        addSwitch(items, "Report", MayogramCustomization.MENU_CHAT_USER_REPORT);
        items.add(UItem.asShadow("Mute, Search and Leave Channel always show and can't be hidden."));

        items.add(UItem.asHeader("MESSAGE MENU - ADMIN"));
        List<String> msgAdmin = new ArrayList<>();
        if (MayogramCustomization.isMenuItemVisible(MayogramCustomization.MENU_MSG_ADMIN_REPLY)) msgAdmin.add("Reply");
        if (MayogramCustomization.isMenuItemVisible(MayogramCustomization.MENU_MSG_ADMIN_COPY_LINK)) msgAdmin.add("Copy Link");
        if (MayogramCustomization.isMenuItemVisible(MayogramCustomization.MENU_MSG_ADMIN_SHOW_OWNER)) msgAdmin.add("Show Message Owner");
        if (MayogramCustomization.isMenuItemVisible(MayogramCustomization.MENU_MSG_ADMIN_SAVE_MUSIC)) msgAdmin.add("Save Music");
        if (MayogramCustomization.isMenuItemVisible(MayogramCustomization.MENU_MSG_ADMIN_EDIT)) msgAdmin.add("Edit");
        msgAdmin.add("Pin");
        msgAdmin.add("Delete");
        items.add(UItem.asCustom(buildListPreview(getContext(), msgAdmin)));
        addSwitch(items, "Reply", MayogramCustomization.MENU_MSG_ADMIN_REPLY);
        addSwitch(items, "Copy Link", MayogramCustomization.MENU_MSG_ADMIN_COPY_LINK);
        addSwitch(items, "Show Message Owner", MayogramCustomization.MENU_MSG_ADMIN_SHOW_OWNER);
        addSwitch(items, "Save Music", MayogramCustomization.MENU_MSG_ADMIN_SAVE_MUSIC);
        addSwitch(items, "Edit", MayogramCustomization.MENU_MSG_ADMIN_EDIT);
        items.add(UItem.asShadow("Pin and Delete always show and can't be hidden."));

        items.add(UItem.asHeader("MESSAGE MENU - NOT ADMIN"));
        List<String> msgUser = new ArrayList<>();
        if (MayogramCustomization.isMenuItemVisible(MayogramCustomization.MENU_MSG_USER_REPLY)) msgUser.add("Reply");
        if (MayogramCustomization.isMenuItemVisible(MayogramCustomization.MENU_MSG_USER_COPY_LINK)) msgUser.add("Copy Link");
        if (MayogramCustomization.isMenuItemVisible(MayogramCustomization.MENU_MSG_USER_SHOW_OWNER)) msgUser.add("Show Message Owner");
        if (MayogramCustomization.isMenuItemVisible(MayogramCustomization.MENU_MSG_USER_SAVE_MUSIC)) msgUser.add("Save Music");
        if (MayogramCustomization.isMenuItemVisible(MayogramCustomization.MENU_MSG_USER_EDIT)) msgUser.add("Edit");
        msgUser.add("Report");
        items.add(UItem.asCustom(buildListPreview(getContext(), msgUser)));
        addSwitch(items, "Reply", MayogramCustomization.MENU_MSG_USER_REPLY);
        addSwitch(items, "Copy Link", MayogramCustomization.MENU_MSG_USER_COPY_LINK);
        addSwitch(items, "Show Message Owner", MayogramCustomization.MENU_MSG_USER_SHOW_OWNER);
        addSwitch(items, "Save Music", MayogramCustomization.MENU_MSG_USER_SAVE_MUSIC);
        addSwitch(items, "Edit", MayogramCustomization.MENU_MSG_USER_EDIT);
        items.add(UItem.asShadow("Report always shows and can't be hidden."));

        items.add(UItem.asButton(BUTTON_RESET, "Reset to Defaults").red());
        items.add(UItem.asShadow("Shows every item above again and turns Show Calls Tab off."));
    }

    private void addSwitch(ArrayList<UItem> items, CharSequence text, String key) {
        items.add(UItem.asSwitch(register(key), text).setChecked(MayogramCustomization.isMenuItemVisible(key)));
    }

    /** A small popup-menu-style card listing exactly the items that would show right now. */
    private View buildListPreview(Context context, List<String> labels) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Theme.createRoundRectDrawable(dp(10), getThemedColor(Theme.key_actionBarDefaultSubmenuBackground)));

        for (int i = 0; i < labels.size(); i++) {
            TextView row = new TextView(context);
            row.setText(labels.get(i));
            row.setTextSize(14);
            row.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItem));
            row.setPadding(dp(14), dp(9), dp(14), dp(9));
            card.addView(row, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        }
        if (labels.isEmpty()) {
            TextView row = new TextView(context);
            row.setText("(nothing to show)");
            row.setTextSize(14);
            row.setAlpha(0.6f);
            row.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItem));
            row.setPadding(dp(14), dp(9), dp(14), dp(9));
            card.addView(row);
        }

        FrameLayout wrapper = new FrameLayout(context);
        wrapper.setPadding(dp(16), dp(10), dp(16), dp(6));
        wrapper.addView(card, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT));
        return wrapper;
    }

    /** A sample of the bottom tab bar's 4 slots, highlighting the toggleable one. */
    private View buildTabsPreview(Context context, boolean showCalls) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackground(Theme.createRoundRectDrawable(dp(24), getThemedColor(Theme.key_actionBarDefaultSubmenuBackground)));
        row.setPadding(dp(10), dp(8), dp(10), dp(8));

        addTabPreviewItem(context, row, "Chats");
        addTabPreviewItem(context, row, "Contacts");
        addTabPreviewItem(context, row, showCalls ? "Calls" : "Settings");
        addTabPreviewItem(context, row, "Profile");

        FrameLayout wrapper = new FrameLayout(context);
        wrapper.setPadding(dp(16), dp(10), dp(16), dp(6));
        wrapper.addView(row, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT));
        return wrapper;
    }

    private void addTabPreviewItem(Context context, LinearLayout row, String label) {
        TextView pill = new TextView(context);
        pill.setText(label);
        pill.setTextSize(12);
        pill.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItem));
        pill.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(16));
        bg.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
        pill.setBackground(bg);
        pill.setPadding(dp(14), dp(7), dp(14), dp(7));
        LinearLayout.LayoutParams lp = LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        lp.rightMargin = dp(6);
        row.addView(pill, lp);
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == SWITCH_SHOW_CALLS_TAB) {
            boolean newValue = !getUserConfig().showCallsTab;
            getUserConfig().setShowCallsTab(newValue);
            getNotificationCenter().postNotificationName(org.telegram.messenger.NotificationCenter.callTabsVisibleToggled);
            listView.adapter.update(true);
            return;
        }
        if (item.id == BUTTON_RESET) {
            MayogramCustomization.resetAllMenuVisibility();
            if (getUserConfig().showCallsTab) {
                getUserConfig().setShowCallsTab(false);
                getNotificationCenter().postNotificationName(org.telegram.messenger.NotificationCenter.callTabsVisibleToggled);
            }
            listView.adapter.update(true);
            return;
        }
        String key = idToKey.get(item.id);
        if (key != null) {
            MayogramCustomization.setMenuItemVisible(key, !MayogramCustomization.isMenuItemVisible(key));
            listView.adapter.update(true);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
