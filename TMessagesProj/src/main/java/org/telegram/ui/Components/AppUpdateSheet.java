package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AppUpdateController;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;

import java.io.File;

/** "Check for updates" sheet: checking -> (update available | up to date) -> downloading -> install. */
public class AppUpdateSheet extends BottomSheet {

    private final Activity activity;
    private final ImageView iconView;
    private final RadialProgressView loader;
    private final TextView titleView;
    private final TextView subtitleView;
    private final ScrollView changelogScroll;
    private final TextView changelogView;
    private final TextView button;

    private AppUpdateController.UpdateInfo info;
    private File downloaded;
    private boolean busy;

    public AppUpdateSheet(Activity activity) {
        super(activity, false);
        this.activity = activity;
        Context context = activity;
        fixNavigationBar(0xff000000);

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(0xff000000);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(20), dp(24), dp(20), dp(20));
        root.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        titleView = new TextView(context);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(17);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER);
        content.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        FrameLayout iconFrame = new FrameLayout(context);
        content.addView(iconFrame, LayoutHelper.createLinear(120, 120, Gravity.CENTER_HORIZONTAL));

        iconView = new ImageView(context);
        iconView.setImageResource(R.drawable.msg_download);
        iconView.setColorFilter(Color.WHITE);
        iconView.setPadding(dp(28), dp(28), dp(28), dp(28));
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xff1c1c1e);
        iconView.setBackground(circle);
        iconFrame.addView(iconView, LayoutHelper.createFrame(96, 96, Gravity.CENTER));

        loader = new RadialProgressView(context);
        loader.setSize(dp(44));
        loader.setProgressColor(Color.WHITE);
        iconFrame.addView(loader, LayoutHelper.createFrame(56, 56, Gravity.CENTER));

        subtitleView = new TextView(context);
        subtitleView.setTextColor(0xff9a9a9f);
        subtitleView.setTextSize(14);
        subtitleView.setGravity(Gravity.CENTER);
        content.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

        changelogScroll = new ScrollView(context);
        changelogView = new TextView(context);
        changelogView.setTextColor(0xffd0d0d4);
        changelogView.setTextSize(14);
        changelogScroll.addView(changelogView);
        content.addView(changelogScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 120, 0, 12, 0, 0));

        button = new TextView(context);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setTypeface(AndroidUtilities.bold());
        button.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(0xff2c2c2e);
        button.setBackground(bg);
        button.setOnClickListener(v -> onButtonClick());
        content.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 20, 0, 0));

        ImageView close = new ImageView(context);
        close.setImageResource(R.drawable.ic_close_white);
        close.setColorFilter(Color.WHITE);
        close.setScaleType(ImageView.ScaleType.CENTER);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setShape(GradientDrawable.OVAL);
        closeBg.setColor(0xff2c2c2e);
        close.setBackground(closeBg);
        close.setOnClickListener(v -> dismiss());
        root.addView(close, LayoutHelper.createFrame(28, 28, Gravity.TOP | Gravity.RIGHT, 0, 16, 16, 0));

        setCustomView(root);
        setBackgroundColor(0xff000000);
        showChecking();
    }

    private void showChecking() {
        busy = true;
        loader.setVisibility(View.VISIBLE);
        iconView.setVisibility(View.INVISIBLE);
        changelogScroll.setVisibility(View.GONE);
        titleView.setText("Mayogram");
        subtitleView.setText("Checking for updates...");
        setButton("Checking...", false);
        AppUpdateController.check((result, error) -> {
            busy = false;
            loader.setVisibility(View.GONE);
            iconView.setVisibility(View.VISIBLE);
            if (error) {
                iconView.setImageResource(R.drawable.msg_retry);
                titleView.setText("Could not check");
                subtitleView.setText("Check your internet connection and try again.");
                info = null;
                setButton("Try again", true);
            } else if (result == null) {
                iconView.setImageResource(R.drawable.msg_info);
                titleView.setText("Mayogram");
                subtitleView.setText("No updates found.\nYou are on the latest version (" + BuildVars.BUILD_VERSION_STRING + ").");
                info = null;
                setButton("No updates, check later", false);
            } else {
                info = result;
                iconView.setImageResource(R.drawable.msg_download);
                titleView.setText("Update available");
                subtitleView.setText("Version " + result.version + "  (current " + BuildVars.BUILD_VERSION_STRING + ")");
                if (!TextUtils.isEmpty(result.changelog)) {
                    changelogView.setText(result.changelog);
                    changelogScroll.setVisibility(View.VISIBLE);
                }
                setButton("Download & Install", true);
            }
        });
    }

    private void setButton(String text, boolean enabled) {
        button.setText(text);
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.5f);
    }

    private void onButtonClick() {
        if (busy) {
            return;
        }
        if (downloaded != null) {
            AppUpdateController.install(activity, downloaded);
            return;
        }
        if (info == null) {
            showChecking();
            return;
        }
        busy = true;
        loader.setVisibility(View.VISIBLE);
        iconView.setVisibility(View.INVISIBLE);
        setButton("Downloading... 0%", false);
        AppUpdateController.download(info, new AppUpdateController.DownloadCallback() {
            @Override
            public void onProgress(int percent) {
                setButton("Downloading... " + percent + "%", false);
            }

            @Override
            public void onDone(File file) {
                busy = false;
                downloaded = file;
                loader.setVisibility(View.GONE);
                iconView.setVisibility(View.VISIBLE);
                setButton("Install", true);
                AppUpdateController.install(activity, file);
            }

            @Override
            public void onError() {
                busy = false;
                loader.setVisibility(View.GONE);
                iconView.setVisibility(View.VISIBLE);
                setButton("Download failed, retry", true);
            }
        });
    }
}
