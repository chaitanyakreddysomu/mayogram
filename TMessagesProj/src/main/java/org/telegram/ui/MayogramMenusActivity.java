/*
 * Mayogram: Settings -> Mayogram -> Menus. Lets the user hide items from the
 * app's various overflow ("...") menus. Items marked permanent in each menu's
 * own code (e.g. Night Mode, Saved Messages, Updates on the home screen) are
 * never listed here - they always show, same as before this screen existed.
 */
package org.telegram.ui;

import android.util.SparseArray;
import android.view.View;

import org.telegram.messenger.MayogramCustomization;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class MayogramMenusActivity extends UniversalFragment {

    private static final int SWITCH_SHOW_CALLS_TAB = -1;

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
        items.add(UItem.asSwitch(SWITCH_SHOW_CALLS_TAB, "Show Calls Tab").setChecked(getUserConfig().showCallsTab));
        items.add(UItem.asShadow("Chats, Contacts and Profile always show and can't be hidden. This slot shows Calls when on, Settings when off."));

        items.add(UItem.asHeader("HOME SCREEN MENU"));
        addSwitch(items, "Ghost Mode", MayogramCustomization.MENU_HOME_GHOST_MODE);
        addSwitch(items, "New Group", MayogramCustomization.MENU_HOME_NEW_GROUP);
        addSwitch(items, "Scheduled Messages", MayogramCustomization.MENU_HOME_SCHEDULED);
        items.add(UItem.asShadow("Night Mode, Saved Messages and Updates always show and can't be hidden."));

        items.add(UItem.asHeader("CHAT MENU - ADMIN"));
        addSwitch(items, "Restricted On", MayogramCustomization.MENU_CHAT_ADMIN_RESTRICT);
        addSwitch(items, "Boost Channel", MayogramCustomization.MENU_CHAT_ADMIN_BOOST);
        addSwitch(items, "Go To Top", MayogramCustomization.MENU_CHAT_ADMIN_GO_TO_TOP);
        addSwitch(items, "Recent Actions", MayogramCustomization.MENU_CHAT_ADMIN_RECENT_ACTIONS);
        addSwitch(items, "Members", MayogramCustomization.MENU_CHAT_ADMIN_MEMBERS);
        addSwitch(items, "Invite Links", MayogramCustomization.MENU_CHAT_ADMIN_INVITE_LINKS);
        items.add(UItem.asShadow("Mute, Search and Leave Channel always show and can't be hidden."));

        items.add(UItem.asHeader("CHAT MENU - NOT ADMIN"));
        addSwitch(items, "Go To Top", MayogramCustomization.MENU_CHAT_USER_GO_TO_TOP);
        addSwitch(items, "Report", MayogramCustomization.MENU_CHAT_USER_REPORT);
        items.add(UItem.asShadow("Mute, Search and Leave Channel always show and can't be hidden."));

        items.add(UItem.asHeader("MESSAGE MENU - ADMIN"));
        addSwitch(items, "Reply", MayogramCustomization.MENU_MSG_ADMIN_REPLY);
        addSwitch(items, "Copy Link", MayogramCustomization.MENU_MSG_ADMIN_COPY_LINK);
        addSwitch(items, "Show Message Owner", MayogramCustomization.MENU_MSG_ADMIN_SHOW_OWNER);
        addSwitch(items, "Save Music", MayogramCustomization.MENU_MSG_ADMIN_SAVE_MUSIC);
        addSwitch(items, "Edit", MayogramCustomization.MENU_MSG_ADMIN_EDIT);
        items.add(UItem.asShadow("Pin and Delete always show and can't be hidden."));

        items.add(UItem.asHeader("MESSAGE MENU - NOT ADMIN"));
        addSwitch(items, "Reply", MayogramCustomization.MENU_MSG_USER_REPLY);
        addSwitch(items, "Copy Link", MayogramCustomization.MENU_MSG_USER_COPY_LINK);
        addSwitch(items, "Show Message Owner", MayogramCustomization.MENU_MSG_USER_SHOW_OWNER);
        addSwitch(items, "Save Music", MayogramCustomization.MENU_MSG_USER_SAVE_MUSIC);
        addSwitch(items, "Edit", MayogramCustomization.MENU_MSG_USER_EDIT);
        items.add(UItem.asShadow("Report always shows and can't be hidden."));
    }

    private void addSwitch(ArrayList<UItem> items, CharSequence text, String key) {
        items.add(UItem.asSwitch(register(key), text).setChecked(MayogramCustomization.isMenuItemVisible(key)));
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
