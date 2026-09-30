package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
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
import org.telegram.ui.ActionBar.Theme;

import java.io.File;

/**
 * Mayogram Telegram-style App Update Sheet.
 *
 * Features:
 * - Telegram light/dark theme support
 * - Rounded top corners
 * - No rectangular background visible around the rounded corners
 * - Subtle glass/frosted appearance
 * - Update checking
 * - Changelog
 * - Download progress
 * - Automatic installation
 */
public class AppUpdateSheet extends BottomSheet {

    private final Activity activity;

    private final FrameLayout root;
    private final LinearLayout content;

    private final ImageView iconView;
    private final RadialProgressView loader;

    private final TextView titleView;
    private final TextView subtitleView;

    private final ScrollView changelogScroll;
    private final TextView changelogView;

    private final TextView button;
    private final ImageView close;

    private AppUpdateController.UpdateInfo info;
    private File downloaded;

    private boolean busy;

    public AppUpdateSheet(Activity activity) {
        super(activity, false);

        this.activity = activity;

        Context context = activity;

        root = new FrameLayout(context);
        content = new LinearLayout(context);

        iconView = new ImageView(context);
        loader = new RadialProgressView(context);

        titleView = new TextView(context);
        subtitleView = new TextView(context);

        changelogScroll = new ScrollView(context);
        changelogView = new TextView(context);

        button = new TextView(context);
        close = new ImageView(context);

        /*
         * Do NOT use a black BottomSheet background.
         *
         * The root itself will contain the rounded sheet.
         * This prevents the rectangular background from appearing
         * outside the rounded top corners.
         */
        fixNavigationBar(
                Theme.getColor(
                        Theme.key_windowBackgroundWhite
                )
        );

        createView(context);

        applyTheme();

        setCustomView(root);

        /*
         * Transparent BottomSheet background.
         */
        setBackgroundColor(Color.TRANSPARENT);

        showChecking();
    }

    // ============================================================
    // CREATE VIEW
    // ============================================================

    private void createView(Context context) {

        /*
         * Root must remain transparent.
         */
        root.setBackgroundColor(Color.TRANSPARENT);

        // --------------------------------------------------------
        // Sheet background
        // --------------------------------------------------------

        GradientDrawable sheetBackground =
                new GradientDrawable();

        sheetBackground.setShape(
                GradientDrawable.RECTANGLE
        );

        /*
         * Only the top corners are rounded.
         *
         * This is what prevents the "cut rectangle" appearance.
         */
        sheetBackground.setCornerRadii(
                new float[]{
                        dp(24), dp(24),     // top-left
                        dp(24), dp(24),     // top-right
                        0, 0,               // bottom-right
                        0, 0                // bottom-left
                }
        );

        root.setBackground(
                sheetBackground
        );

        // --------------------------------------------------------
        // Main content
        // --------------------------------------------------------

        content.setOrientation(
                LinearLayout.VERTICAL
        );

        content.setGravity(
                Gravity.CENTER_HORIZONTAL
        );

        content.setPadding(
                dp(24),
                dp(22),
                dp(24),
                dp(20)
        );

        root.addView(
                content,
                LayoutHelper.createFrame(
                        LayoutHelper.MATCH_PARENT,
                        LayoutHelper.WRAP_CONTENT
                )
        );

        // --------------------------------------------------------
        // Title
        // --------------------------------------------------------

        titleView.setTextSize(20);

        titleView.setGravity(
                Gravity.CENTER
        );

        titleView.setTypeface(
                AndroidUtilities.bold()
        );

        content.addView(
                titleView,
                LayoutHelper.createLinear(
                        LayoutHelper.MATCH_PARENT,
                        LayoutHelper.WRAP_CONTENT,
                        0,
                        4,
                        0,
                        14
                )
        );

        // --------------------------------------------------------
        // Icon container
        // --------------------------------------------------------

        FrameLayout iconFrame =
                new FrameLayout(context);

        content.addView(
                iconFrame,
                LayoutHelper.createLinear(
                        96,
                        96,
                        Gravity.CENTER_HORIZONTAL
                )
        );

        // --------------------------------------------------------
        // Icon
        // --------------------------------------------------------

        iconView.setPadding(
                dp(23),
                dp(23),
                dp(23),
                dp(23)
        );

        iconView.setScaleType(
                ImageView.ScaleType.CENTER_INSIDE
        );

        iconFrame.addView(
                iconView,
                LayoutHelper.createFrame(
                        80,
                        80,
                        Gravity.CENTER
                )
        );

        // --------------------------------------------------------
        // Progress loader
        // --------------------------------------------------------

        loader.setSize(
                dp(42)
        );

        iconFrame.addView(
                loader,
                LayoutHelper.createFrame(
                        52,
                        52,
                        Gravity.CENTER
                )
        );

        // --------------------------------------------------------
        // Subtitle
        // --------------------------------------------------------

        subtitleView.setTextSize(15);

        subtitleView.setGravity(
                Gravity.CENTER
        );

        subtitleView.setLineSpacing(
                dp(1),
                1.05f
        );

        content.addView(
                subtitleView,
                LayoutHelper.createLinear(
                        LayoutHelper.MATCH_PARENT,
                        LayoutHelper.WRAP_CONTENT,
                        0,
                        14,
                        0,
                        0
                )
        );

        // --------------------------------------------------------
        // Changelog
        // --------------------------------------------------------

        changelogScroll.setFillViewport(
                true
        );

        changelogScroll.setOverScrollMode(
                View.OVER_SCROLL_IF_CONTENT_SCROLLS
        );

        /*
         * Remove default ScrollView background.
         */
        changelogScroll.setBackgroundColor(
                Color.TRANSPARENT
        );

        changelogView.setTextSize(14);

        changelogView.setLineSpacing(
                dp(2),
                1.0f
        );

        changelogView.setPadding(
                dp(14),
                dp(12),
                dp(14),
                dp(12)
        );

        changelogScroll.addView(
                changelogView,
                new ScrollView.LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT
                )
        );

        content.addView(
                changelogScroll,
                LayoutHelper.createLinear(
                        LayoutHelper.MATCH_PARENT,
                        130,
                        0,
                        16,
                        0,
                        0
                )
        );

        // --------------------------------------------------------
        // Action button
        // --------------------------------------------------------

        button.setTextSize(15);

        button.setGravity(
                Gravity.CENTER
        );

        button.setTypeface(
                AndroidUtilities.bold()
        );

        button.setMinHeight(
                dp(48)
        );

        button.setPadding(
                dp(16),
                0,
                dp(16),
                0
        );

        button.setOnClickListener(
                v -> onButtonClick()
        );

        content.addView(
                button,
                LayoutHelper.createLinear(
                        LayoutHelper.MATCH_PARENT,
                        48,
                        0,
                        18,
                        0,
                        0
                )
        );

        // --------------------------------------------------------
        // Close button
        // --------------------------------------------------------

        close.setImageResource(
                R.drawable.ic_close_white
        );

        close.setScaleType(
                ImageView.ScaleType.CENTER
        );

        close.setPadding(
                dp(6),
                dp(6),
                dp(6),
                dp(6)
        );

        close.setOnClickListener(
                v -> dismiss()
        );

        root.addView(
                close,
                LayoutHelper.createFrame(
                        32,
                        32,
                        Gravity.TOP | Gravity.RIGHT,
                        0,
                        12,
                        12,
                        0
                )
        );
    }

    // ============================================================
    // THEME
    // ============================================================

    private void applyTheme() {

        int backgroundColor =
                Theme.getColor(
                        Theme.key_windowBackgroundWhite
                );

        int primaryText =
                Theme.getColor(
                        Theme.key_windowBackgroundWhiteBlackText
                );

        int secondaryText =
                Theme.getColor(
                        Theme.key_windowBackgroundWhiteGrayText
                );

        int grayBackground =
                Theme.getColor(
                        Theme.key_windowBackgroundGray
                );

        int accentColor =
                Theme.getColor(
                        Theme.key_windowBackgroundWhiteBlueText
                );

        // --------------------------------------------------------
        // Root / sheet
        // --------------------------------------------------------

        GradientDrawable sheetBackground =
                new GradientDrawable();

        sheetBackground.setShape(
                GradientDrawable.RECTANGLE
        );

        sheetBackground.setCornerRadii(
                new float[]{
                        dp(24), dp(24),
                        dp(24), dp(24),
                        0, 0,
                        0, 0
                }
        );

        /*
         * Slightly transparent color in dark mode gives a
         * softer/frosted appearance while still remaining
         * readable.
         */
        if (isDarkTheme()) {

            int darkGlass = blendColor(
                    backgroundColor,
                    Color.WHITE,
                    0.035f
            );

            sheetBackground.setColor(
                    darkGlass
            );

        } else {

            int lightGlass = blendColor(
                    backgroundColor,
                    Color.WHITE,
                    0.35f
            );

            sheetBackground.setColor(
                    lightGlass
            );
        }

        root.setBackground(
                sheetBackground
        );

        // --------------------------------------------------------
        // Title
        // --------------------------------------------------------

        titleView.setTextColor(
                primaryText
        );

        // --------------------------------------------------------
        // Subtitle
        // --------------------------------------------------------

        subtitleView.setTextColor(
                secondaryText
        );

        // --------------------------------------------------------
        // Changelog
        // --------------------------------------------------------

        changelogView.setTextColor(
                primaryText
        );

        GradientDrawable changelogBackground =
                new GradientDrawable();

        changelogBackground.setShape(
                GradientDrawable.RECTANGLE
        );

        changelogBackground.setCornerRadius(
                dp(12)
        );

        if (isDarkTheme()) {

            changelogBackground.setColor(
                    blendColor(
                            grayBackground,
                            Color.WHITE,
                            0.035f
                    )
            );

        } else {

            changelogBackground.setColor(
                    blendColor(
                            grayBackground,
                            Color.WHITE,
                            0.25f
                    )
            );
        }

        changelogScroll.setBackground(
                changelogBackground
        );

        // --------------------------------------------------------
        // Icon background
        // --------------------------------------------------------

        GradientDrawable iconBackground =
                new GradientDrawable();

        iconBackground.setShape(
                GradientDrawable.OVAL
        );

        if (isDarkTheme()) {

            iconBackground.setColor(
                    blendColor(
                            grayBackground,
                            Color.WHITE,
                            0.06f
                    )
            );

        } else {

            iconBackground.setColor(
                    blendColor(
                            grayBackground,
                            Color.WHITE,
                            0.20f
                    )
            );
        }

        iconView.setBackground(
                iconBackground
        );

        // --------------------------------------------------------
        // Icon
        // --------------------------------------------------------

        iconView.setColorFilter(
                accentColor,
                PorterDuff.Mode.SRC_IN
        );

        // --------------------------------------------------------
        // Loader
        // --------------------------------------------------------

        loader.setProgressColor(
                accentColor
        );

        // --------------------------------------------------------
        // Button
        // --------------------------------------------------------

        GradientDrawable buttonBackground =
                new GradientDrawable();

        buttonBackground.setShape(
                GradientDrawable.RECTANGLE
        );

        buttonBackground.setCornerRadius(
                dp(12)
        );

        buttonBackground.setColor(
                accentColor
        );

        button.setBackground(
                buttonBackground
        );

        /*
         * Telegram blue buttons normally use white text.
         */
        button.setTextColor(
                Color.WHITE
        );

        // --------------------------------------------------------
        // Close button
        // --------------------------------------------------------

        GradientDrawable closeBackground =
                new GradientDrawable();

        closeBackground.setShape(
                GradientDrawable.OVAL
        );

        if (isDarkTheme()) {

            closeBackground.setColor(
                    blendColor(
                            grayBackground,
                            Color.WHITE,
                            0.08f
                    )
            );

        } else {

            closeBackground.setColor(
                    blendColor(
                            grayBackground,
                            Color.WHITE,
                            0.25f
                    )
            );
        }

        close.setBackground(
                closeBackground
        );

        close.setColorFilter(
                primaryText,
                PorterDuff.Mode.SRC_IN
        );

        /*
         * Navigation bar should follow the current theme.
         */
        fixNavigationBar(
                backgroundColor
        );
    }

    // ============================================================
    // DARK THEME DETECTION
    // ============================================================

    private boolean isDarkTheme() {

        int background =
                Theme.getColor(
                        Theme.key_windowBackgroundWhite
                );

        int red =
                Color.red(background);

        int green =
                Color.green(background);

        int blue =
                Color.blue(background);

        /*
         * Perceived brightness.
         */
        double brightness =
                (0.299 * red)
                        + (0.587 * green)
                        + (0.114 * blue);

        return brightness < 128;
    }

    // ============================================================
    // COLOR BLENDING
    // ============================================================

    private int blendColor(
            int base,
            int overlay,
            float amount
    ) {

        amount = Math.max(
                0f,
                Math.min(
                        1f,
                        amount
                )
        );

        int r =
                (int) (
                        Color.red(base)
                                * (1f - amount)
                                +
                                Color.red(overlay)
                                        * amount
                );

        int g =
                (int) (
                        Color.green(base)
                                * (1f - amount)
                                +
                                Color.green(overlay)
                                        * amount
                );

        int b =
                (int) (
                        Color.blue(base)
                                * (1f - amount)
                                +
                                Color.blue(overlay)
                                        * amount
                );

        return Color.rgb(
                r,
                g,
                b
        );
    }

    // ============================================================
    // CHECKING
    // ============================================================

    private void showChecking() {

        busy = true;

        downloaded = null;

        loader.setVisibility(
                View.VISIBLE
        );

        iconView.setVisibility(
                View.INVISIBLE
        );

        changelogScroll.setVisibility(
                View.GONE
        );

        titleView.setText(
                "Mayogram"
        );

        subtitleView.setText(
                "Checking for updates…"
        );

        setButton(
                "Checking…",
                false
        );

        AppUpdateController.check(
                (result, error) -> {

                    busy = false;

                    loader.setVisibility(
                            View.GONE
                    );

                    iconView.setVisibility(
                            View.VISIBLE
                    );

                    if (error) {

                        showError();

                    } else if (result == null) {

                        showLatest();

                    } else {

                        showUpdate(
                                result
                        );
                    }
                }
        );
    }

    // ============================================================
    // ERROR
    // ============================================================

    private void showError() {

        info = null;

        changelogScroll.setVisibility(
                View.GONE
        );

        iconView.setImageResource(
                R.drawable.msg_retry
        );

        titleView.setText(
                "Couldn't check for updates"
        );

        subtitleView.setText(
                "Please check your internet connection\n"
                        + "and try again."
        );

        applyIconTint();

        setButton(
                "Try again",
                true
        );
    }

    // ============================================================
    // LATEST VERSION
    // ============================================================

    private void showLatest() {

        info = null;

        changelogScroll.setVisibility(
                View.GONE
        );

        iconView.setImageResource(
                R.drawable.msg_info
        );

        titleView.setText(
                "Mayogram"
        );

        subtitleView.setText(
                "You're using the latest version\n"
                        + BuildVars.BUILD_VERSION_STRING
        );

        applyIconTint();

        setButton(
                "Check again",
                true
        );
    }

    // ============================================================
    // UPDATE AVAILABLE
    // ============================================================

    private void showUpdate(
            AppUpdateController.UpdateInfo result
    ) {

        info = result;

        iconView.setImageResource(
                R.drawable.msg_download
        );

        titleView.setText(
                "Update available"
        );

        subtitleView.setText(
                "Version "
                        + result.version
                        + "\nCurrent version "
                        + BuildVars.BUILD_VERSION_STRING
        );

        if (!TextUtils.isEmpty(
                result.changelog
        )) {

            changelogView.setText(
                    result.changelog
            );

            changelogScroll.setVisibility(
                    View.VISIBLE
            );

        } else {

            changelogScroll.setVisibility(
                    View.GONE
            );
        }

        applyIconTint();

        setButton(
                "Download & Install",
                true
        );
    }

    // ============================================================
    // SET BUTTON
    // ============================================================

    private void setButton(
            String text,
            boolean enabled
    ) {

        button.setText(
                text
        );

        button.setEnabled(
                enabled
        );

        button.setAlpha(
                enabled
                        ? 1.0f
                        : 0.5f
        );
    }

    // ============================================================
    // BUTTON CLICK
    // ============================================================

    private void onButtonClick() {

        if (busy) {
            return;
        }

        // --------------------------------------------------------
        // Already downloaded
        // --------------------------------------------------------

        if (downloaded != null) {

            AppUpdateController.install(
                    activity,
                    downloaded
            );

            return;
        }

        // --------------------------------------------------------
        // No update information
        // --------------------------------------------------------

        if (info == null) {

            showChecking();

            return;
        }

        // --------------------------------------------------------
        // Start download
        // --------------------------------------------------------

        busy = true;

        loader.setVisibility(
                View.VISIBLE
        );

        iconView.setVisibility(
                View.INVISIBLE
        );

        setButton(
                "Downloading… 0%",
                false
        );

        AppUpdateController.download(
                info,
                new AppUpdateController.DownloadCallback() {

                    @Override
                    public void onProgress(
                            int percent
                    ) {

                        setButton(
                                "Downloading… "
                                        + percent
                                        + "%",
                                false
                        );
                    }

                    @Override
                    public void onDone(
                            File file
                    ) {

                        busy = false;

                        downloaded = file;

                        loader.setVisibility(
                                View.GONE
                        );

                        iconView.setVisibility(
                                View.VISIBLE
                        );

                        setButton(
                                "Install",
                                true
                        );

                        /*
                         * Automatically open Android installer.
                         */
                        AppUpdateController.install(
                                activity,
                                file
                        );
                    }

                    @Override
                    public void onError() {

                        busy = false;

                        loader.setVisibility(
                                View.GONE
                        );

                        iconView.setVisibility(
                                View.VISIBLE
                        );

                        iconView.setImageResource(
                                R.drawable.msg_retry
                        );

                        applyIconTint();

                        setButton(
                                "Download failed — Retry",
                                true
                        );
                    }
                }
        );
    }

    // ============================================================
    // ICON TINT
    // ============================================================

    private void applyIconTint() {

        iconView.setColorFilter(
                Theme.getColor(
                        Theme.key_windowBackgroundWhiteBlueText
                ),
                PorterDuff.Mode.SRC_IN
        );
    }
}