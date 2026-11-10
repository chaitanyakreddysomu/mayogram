/*
 * Mayogram: "Customise" screen (Settings -> Customise). Lets the user tweak
 * a few appearance knobs without needing an app update:
 *  - App icon (reuses the existing LauncherIconController/ThemeActivity icon picker)
 *  - Folder (chat filter) tabs bar: selected-pill color + corner radius
 *  - Avatar shape across the app: square <-> circle
 *  - Home screen title: app name vs. first name vs. full name
 *
 * All settings are stored in MessagesController.getGlobalMainSettings() -
 * the same SharedPreferences file the rest of the app's settings use, so
 * they survive app updates and simply reset to defaults on a fresh install,
 * same as every other setting in this app.
 */
package org.telegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MayogramCustomization;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.Locale;

import static org.telegram.messenger.AndroidUtilities.dp;

public class MayogramCustomizeActivity extends UniversalFragment {

    private static final int BUTTON_FOLDER_COLOR = 2;
    private static final int SLIDER_FOLDER_RADIUS = 3;
    private static final int SLIDER_AVATAR_SHAPE = 4;
    private static final int BUTTON_TITLE_MODE = 5;
    private static final int BUTTON_RESET = 6;

    private static final int[] SWATCHES = {
            0, // "default" / theme color
            0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047,
            0xFF00ACC1, 0xFF1E88E5, 0xFF8E24AA, 0xFFD81B60,
    };

    @Override
    protected CharSequence getTitle() {
        return "Appearance";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader("FOLDERS BAR"));
        items.add(UItem.asButton(BUTTON_FOLDER_COLOR, folderColorLabel()));
        items.add(UItem.asIntSlideView(
                1,
                0, Math.round(MayogramCustomization.getFolderBarRadiusDp()), 28,
                val -> val == 0 ? "Square" : val + "dp",
                val -> {
                    MayogramCustomization.setFolderBarRadiusDp(val);
                }
        ).setMinSliderValue(-1));
        items.add(UItem.asShadow("Color and corner radius of the selected tab in the folders bar above your chat list."));

        items.add(UItem.asHeader("AVATARS"));
        items.add(UItem.asIntSlideView(
                1,
                0, MayogramCustomization.getAvatarRadiusPercent(), 100,
                val -> val <= 0 ? "Square" : val >= 100 ? "Circle" : val + "%",
                val -> {
                    MayogramCustomization.setAvatarRadiusPercent(val);
                }
        ).setMinSliderValue(-1));
        items.add(UItem.asShadow("Shape used for profile pictures across chats, profiles and member lists."));

        items.add(UItem.asHeader("HOME SCREEN TITLE"));
        items.add(UItem.asButton(BUTTON_TITLE_MODE, titleModeLabel()));
        items.add(UItem.asShadow("What shows at the top of your Chats list."));

        items.add(UItem.asButton(BUTTON_RESET, "Reset to Defaults").red());
        items.add(UItem.asShadow(null));
    }

    private CharSequence folderColorLabel() {
        int color = MayogramCustomization.getFolderBarColor();
        return color == 0 ? "Folder Bar Color (Default)" : "Folder Bar Color";
    }

    private CharSequence titleModeLabel() {
        switch (MayogramCustomization.getTitleMode()) {
            case MayogramCustomization.TITLE_MODE_APP_NAME:
                return "Title: MayoGram";
            case MayogramCustomization.TITLE_MODE_FIRST_NAME:
                return "Title: First Name";
            default:
                return "Title: First + Last Name";
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == BUTTON_FOLDER_COLOR) {
            showColorPicker();
        } else if (item.id == BUTTON_TITLE_MODE) {
            showTitleModePicker();
        } else if (item.id == BUTTON_RESET) {
            MayogramCustomization.resetAppearanceDefaults();
            getNotificationCenter().postNotificationName(org.telegram.messenger.NotificationCenter.mayogramTitleModeChanged);
            listView.adapter.update(true);
        }
    }

    private void showColorPicker() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(16), dp(16), dp(16), dp(8));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Folder Bar Color");

        for (int color : SWATCHES) {
            FrameLayout swatchWrapper = new FrameLayout(context);
            View swatch = new View(context);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            if (color == 0) {
                bg.setColor(Color.TRANSPARENT);
                bg.setStroke(dp(2), getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            } else {
                bg.setColor(color);
            }
            swatch.setBackground(bg);
            swatchWrapper.addView(swatch, LayoutHelper.createFrame(32, 32, Gravity.CENTER));
            swatchWrapper.setOnClickListener(v -> {
                MayogramCustomization.setFolderBarColor(color);
                listView.adapter.update(true);
                dismissCurrentDialog();
            });
            LinearLayout.LayoutParams lp = LayoutHelper.createLinear(40, 40);
            row.addView(swatchWrapper, lp);
        }

        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(context);
        scroll.addView(row);
        builder.setView(scroll);
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showTitleModePicker() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        String[] options = {"MayoGram", "First Name", "First + Last Name"};
        int[] modes = {MayogramCustomization.TITLE_MODE_APP_NAME, MayogramCustomization.TITLE_MODE_FIRST_NAME, MayogramCustomization.TITLE_MODE_FULL_NAME};
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Home Screen Title");
        builder.setItems(options, (dialog, which) -> {
            MayogramCustomization.setTitleMode(modes[which]);
            getNotificationCenter().postNotificationName(org.telegram.messenger.NotificationCenter.mayogramTitleModeChanged);
            listView.adapter.update(true);
        });
        showDialog(builder.create());
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
