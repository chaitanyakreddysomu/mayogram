/*
 * Mayogram local-only "Channel Appearance" screen.
 *
 * Everything here affects only how this chat looks on this device. Nothing
 * is sent to Telegram's servers and nothing propagates to other members -
 * see ChannelAppearanceSettings for why that split matters. This is
 * deliberately a separate screen from Telegram's own (server-synced,
 * boost-level-gated) channel color/wallpaper settings, not a replacement
 * for them.
 */

package org.telegram.messenger.appearance;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class ChannelAppearanceActivity extends BaseFragment {

    /** Fired when the user applies changes. args[0] = dialogId. Chat rendering can observe this later. */
    public static final int EVENT_APPEARANCE_CHANGED = 100002;

    private static final int REQUEST_PICK_WALLPAPER = 5501;

    private final long dialogId;
    private ChannelAppearanceSettings settings;

    private final ImageView[] swatchViews = new ImageView[ChannelAppearanceSettings.SWATCH_COLORS.length + 1];
    private TextView wallpaperPathLabel;
    private Switch replyLogoSwitch;

    public ChannelAppearanceActivity(long dialogId) {
        super();
        this.dialogId = dialogId;
    }

    @Override
    public View createView(Context context) {
        settings = ChannelAppearanceSettings.load(currentAccount, dialogId);

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString(R.string.ChannelAppearanceTitle));
        actionBar.setAllowOverlayTitle(true);

        ScrollView scrollView = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        TextView notice = new TextView(context);
        notice.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        notice.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        notice.setText(LocaleController.getString(R.string.ChannelAppearanceLocalNotice));
        root.addView(notice, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        root.addView(sectionLabel(context, R.string.ChannelAppearanceColorSection));
        root.addView(buildSwatchRow(context));

        root.addView(divider(context));

        root.addView(sectionLabel(context, R.string.ChannelAppearanceReplyLogoSection));
        root.addView(buildReplyLogoRow(context));

        root.addView(divider(context));

        root.addView(sectionLabel(context, R.string.ChannelAppearanceWallpaperSection));
        root.addView(buildWallpaperRow(context));

        TextView applyButton = new TextView(context);
        applyButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        applyButton.setTypeface(AndroidUtilities.bold());
        applyButton.setGravity(Gravity.CENTER);
        applyButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        applyButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                AndroidUtilities.dp(8),
                Theme.getColor(Theme.key_featuredStickers_addButton),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        applyButton.setText(LocaleController.getString(R.string.ChannelAppearanceApply));
        applyButton.setOnClickListener(v -> applyChanges());
        root.addView(applyButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 24, 0, 0));

        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        FrameLayout container = new FrameLayout(context);
        container.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = container;

        updateSwatchSelectionUi();
        return fragmentView;
    }

    private TextView sectionLabel(Context context, int stringRes) {
        TextView label = new TextView(context);
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        label.setTypeface(AndroidUtilities.bold());
        label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        label.setText(LocaleController.getString(stringRes));
        return label;
    }

    private View divider(Context context) {
        View line = new View(context);
        line.setBackgroundColor(Theme.getColor(Theme.key_divider));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        params.topMargin = AndroidUtilities.dp(20);
        params.bottomMargin = AndroidUtilities.dp(12);
        line.setLayoutParams(params);
        return line;
    }

    private View buildSwatchRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = AndroidUtilities.dp(10);
        row.setLayoutParams(rowParams);

        for (int i = 0; i < ChannelAppearanceSettings.SWATCH_COLORS.length; i++) {
            final int index = i;
            ImageView swatch = createSwatchCircle(context, ChannelAppearanceSettings.SWATCH_COLORS[i]);
            swatch.setOnClickListener(v -> {
                settings.swatchIndex = index;
                updateSwatchSelectionUi();
            });
            swatchViews[i] = swatch;
            row.addView(swatch, swatchParams());
        }

        int customIndex = ChannelAppearanceSettings.SWATCH_COLORS.length;
        ImageView customSwatch = createSwatchCircle(context,
                settings.customColor != 0 ? settings.customColor : 0xFF888888);
        customSwatch.setOnClickListener(v -> showCustomColorPicker());
        swatchViews[customIndex] = customSwatch;
        row.addView(customSwatch, swatchParams());

        return row;
    }

    private LinearLayout.LayoutParams swatchParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                AndroidUtilities.dp(40), AndroidUtilities.dp(40));
        params.rightMargin = AndroidUtilities.dp(12);
        return params;
    }

    private ImageView createSwatchCircle(Context context, int color) {
        ImageView view = new ImageView(context);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        view.setBackground(drawable);
        return view;
    }

    private void updateSwatchSelectionUi() {
        for (int i = 0; i < swatchViews.length; i++) {
            if (swatchViews[i] == null) {
                continue;
            }
            boolean selected = i == settings.swatchIndex
                    || (i == swatchViews.length - 1 && settings.swatchIndex == ChannelAppearanceSettings.SWATCH_CUSTOM);
            swatchViews[i].setAlpha(selected ? 1f : 0.45f);
        }
    }

    private void showCustomColorPicker() {
        // A full HSV color-wheel picker is a separate, larger component; for
        // this first pass, cycle through a small extra palette on tap so the
        // "Custom" swatch is functional rather than a placeholder.
        int[] extra = {0xFF06B6D4, 0xFFEC4899, 0xFF84CC16, 0xFF64748B, 0xFFFACC15};
        int current = settings.customColor;
        int nextIndex = 0;
        for (int i = 0; i < extra.length; i++) {
            if (extra[i] == current) {
                nextIndex = (i + 1) % extra.length;
                break;
            }
        }
        settings.customColor = extra[nextIndex];
        settings.swatchIndex = ChannelAppearanceSettings.SWATCH_CUSTOM;
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(settings.customColor);
        swatchViews[swatchViews.length - 1].setBackground(drawable);
        updateSwatchSelectionUi();
    }

    private View buildReplyLogoRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = AndroidUtilities.dp(8);
        row.setLayoutParams(rowParams);

        TextView label = new TextView(context);
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        label.setText(LocaleController.getString(R.string.ChannelAppearanceReplyLogo));
        row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        replyLogoSwitch = new Switch(context);
        replyLogoSwitch.setChecked(settings.replyLogoEnabled);
        replyLogoSwitch.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) ->
                settings.replyLogoEnabled = isChecked);
        row.addView(replyLogoSwitch);

        return row;
    }

    private View buildWallpaperRow(Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = AndroidUtilities.dp(8);
        column.setLayoutParams(params);

        wallpaperPathLabel = new TextView(context);
        wallpaperPathLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        wallpaperPathLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        updateWallpaperLabel();
        column.addView(wallpaperPathLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        TextView chooseButton = new TextView(context);
        chooseButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        chooseButton.setTypeface(AndroidUtilities.bold());
        chooseButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        chooseButton.setText(LocaleController.getString(R.string.ChannelAppearanceChooseWallpaper));
        chooseButton.setOnClickListener(v -> openWallpaperPicker());
        column.addView(chooseButton);

        return column;
    }

    private void updateWallpaperLabel() {
        wallpaperPathLabel.setText(settings.wallpaperPath != null
                ? LocaleController.getString(R.string.ChannelAppearanceWallpaperSet)
                : LocaleController.getString(R.string.ChannelAppearanceWallpaperNone));
    }

    private void openWallpaperPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_PICK_WALLPAPER);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        super.onActivityResultFragment(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_WALLPAPER || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            File dir = new File(getParentActivity().getFilesDir(), "mayo_channel_wallpapers");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File dest = new File(dir, "wallpaper_" + dialogId + ".jpg");
            try (InputStream in = getParentActivity().getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(dest)) {
                if (in == null) {
                    return;
                }
                AndroidUtilities.copyFile(in, out);
            }
            settings.wallpaperPath = dest.getAbsolutePath();
            updateWallpaperLabel();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void applyChanges() {
        settings.save(currentAccount);
        NotificationCenter.getInstance(currentAccount).postNotificationName(EVENT_APPEARANCE_CHANGED, dialogId);
        if (getParentActivity() != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setMessage(LocaleController.getString(R.string.ChannelAppearanceApplied));
            builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
            builder.show();
        }
        finishFragment();
    }
}
