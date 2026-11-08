package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.List;

public class AppUpdateSheet extends BottomSheet {

    private final Activity activity;

    private final FrameLayout root;
    private final LinearLayout content;

    private final ImageView iconView;
    private final RadialProgressView loader;

    private final TextView titleView;
    private final TextView subtitleView;

    private final ScrollView changelogScroll;
    private final LinearLayout changelogContainer;

    private final DownloadProgressButton button;
    private final ImageView close;

    private AppUpdateController.UpdateInfo info;
    private File downloaded;

    private boolean busy;

    private final AppUpdateController.DownloadCallback downloadCallback = new AppUpdateController.DownloadCallback() {
        @Override
        public void onProgress(int percent) {
            onDownloadProgress(percent);
        }

        @Override
        public void onDone(File file) {
            onDownloadDone(file);
        }

        @Override
        public void onError() {
            onDownloadError();
        }
    };

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
        changelogContainer = new LinearLayout(context);

        button = new DownloadProgressButton(context);
        close = new ImageView(context);

        fixNavigationBar(
                Theme.getColor(
                        Theme.key_windowBackgroundWhite
                )
        );

        createView(context);
        applyTheme();

        setCustomView(root);

        setBackgroundColor(Color.TRANSPARENT);

        showChecking();
    }

    // ============================================================
    // CREATE UI
    // ============================================================

    private void createView(Context context) {

        root.setBackgroundColor(Color.TRANSPARENT);

        /*
         * Rounded sheet background.
         *
         * Important:
         * BottomSheet itself stays transparent.
         * The rounded background belongs to root.
         */
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

        root.setBackground(sheetBackground);

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
        // Icon
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
        // Changelog / Features
        // --------------------------------------------------------

        changelogScroll.setFillViewport(true);

        changelogScroll.setOverScrollMode(
                View.OVER_SCROLL_IF_CONTENT_SCROLLS
        );

        changelogScroll.setBackgroundColor(
                Color.TRANSPARENT
        );

        changelogContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        changelogContainer.setPadding(
                dp(14),
                dp(12),
                dp(14),
                dp(12)
        );

        changelogScroll.addView(
                changelogContainer,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
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
        // Download button
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
        // Sheet
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

        if (isDarkTheme()) {

            sheetBackground.setColor(
                    blendColor(
                            backgroundColor,
                            Color.WHITE,
                            0.035f
                    )
            );

        } else {

            sheetBackground.setColor(
                    blendColor(
                            backgroundColor,
                            Color.WHITE,
                            0.30f
                    )
            );
        }

        root.setBackground(
                sheetBackground
        );

        // --------------------------------------------------------
        // Text
        // --------------------------------------------------------

        titleView.setTextColor(
                primaryText
        );

        subtitleView.setTextColor(
                secondaryText
        );

        // --------------------------------------------------------
        // Features container
        // --------------------------------------------------------

        GradientDrawable featuresBackground =
                new GradientDrawable();

        featuresBackground.setShape(
                GradientDrawable.RECTANGLE
        );

        featuresBackground.setCornerRadius(
                dp(12)
        );

        if (isDarkTheme()) {

            featuresBackground.setColor(
                    blendColor(
                            grayBackground,
                            Color.WHITE,
                            0.035f
                    )
            );

        } else {

            featuresBackground.setColor(
                    blendColor(
                            grayBackground,
                            Color.WHITE,
                            0.25f
                    )
            );
        }

        changelogScroll.setBackground(
                featuresBackground
        );

        // --------------------------------------------------------
        // Icon
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

        iconView.setColorFilter(
                accentColor,
                PorterDuff.Mode.SRC_IN
        );

        loader.setProgressColor(
                accentColor
        );

        // --------------------------------------------------------
        // Button
        // --------------------------------------------------------

        button.setButtonColor(
                accentColor
        );

        button.setProgressColor(
                accentColor
        );

        button.setTextColor(
                Color.WHITE
        );

        // --------------------------------------------------------
        // Close
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

        fixNavigationBar(
                backgroundColor
        );
    }

    private boolean isDarkTheme() {

        int background =
                Theme.getColor(
                        Theme.key_windowBackgroundWhite
                );

        int red = Color.red(background);
        int green = Color.green(background);
        int blue = Color.blue(background);

        double brightness =
                (0.299 * red)
                        + (0.587 * green)
                        + (0.114 * blue);

        return brightness < 128;
    }

    private int blendColor(
            int base,
            int overlay,
            float amount
    ) {

        amount = Math.max(
                0f,
                Math.min(1f, amount)
        );

        int r =
                (int) (
                        Color.red(base)
                                * (1f - amount)
                                + Color.red(overlay)
                                * amount
                );

        int g =
                (int) (
                        Color.green(base)
                                * (1f - amount)
                                + Color.green(overlay)
                                * amount
                );

        int b =
                (int) (
                        Color.blue(base)
                                * (1f - amount)
                                + Color.blue(overlay)
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

        button.resetProgress();

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

                        showUpdate(result);
                    }
                }
        );
    }

    // ============================================================
    // ERROR
    // ============================================================

    private void showError() {

        info = null;

        button.resetProgress();

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
                "Please check your internet connection\nand try again."
        );

        applyIconTint();

        setButton(
                "Try again",
                true
        );
    }

    // ============================================================
    // LATEST
    // ============================================================

    private void showLatest() {

        info = null;

        button.resetProgress();

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

        button.resetProgress();

        titleView.setText(
                "Update available"
        );

        subtitleView.setText(
                "Version " + result.version
                        + "\nCurrent version "
                        + BuildVars.BUILD_VERSION_STRING
        );

        /*
         * Build the What's New section from:
         *
         * "features": [
         *     "OTA Updates",
         *     "Bug fixes"
         * ]
         */
        buildFeaturesList(
                result.features,
                result.changelog
        );

        applyIconTint();

        if (AppUpdateController.isDownloaded(result.version)) {

            // Already fully downloaded in a previous session/sheet - skip
            // straight to install, never re-download.
            downloaded = AppUpdateController.getDownloadedFile(result.version);

            iconView.setImageResource(
                    R.drawable.msg_download
            );

            setButton(
                    "Install",
                    true
            );

        } else if (AppUpdateController.isDownloading(result.version)) {

            // A download kicked off from an earlier sheet (or before this one
            // was reopened) is still running in the background - attach to
            // it and show its real progress instead of restarting at 0%.
            downloaded = null;
            busy = true;

            int accentColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);
            button.setButtonColor(accentColor);
            button.setProgressColor(accentColor);

            loader.setVisibility(View.VISIBLE);
            iconView.setVisibility(View.INVISIBLE);

            int percent = AppUpdateController.getDownloadPercent();
            button.setProgress(percent);
            setButton("Downloading… " + percent + "%", false);

            AppUpdateController.download(result, downloadCallback);

        } else {

            downloaded = null;

            iconView.setImageResource(
                    R.drawable.msg_download
            );

            setButton(
                    "Download & Install",
                    true
            );
        }
    }

    // ============================================================
    // FEATURES LIST
    // ============================================================

    private void buildFeaturesList(
            List<String> features,
            String changelog
    ) {

        changelogContainer.removeAllViews();

        boolean hasFeatures =
                features != null
                        && !features.isEmpty();

        boolean hasChangelog =
                !TextUtils.isEmpty(
                        changelog
                );

        if (!hasFeatures && !hasChangelog) {

            changelogScroll.setVisibility(
                    View.GONE
            );

            return;
        }

        // --------------------------------------------------------
        // "What's new" title
        // --------------------------------------------------------

        TextView heading =
                new TextView(activity);

        heading.setText(
                "What's new"
        );

        heading.setTextSize(14);

        heading.setTypeface(
                AndroidUtilities.bold()
        );

        heading.setTextColor(
                Theme.getColor(
                        Theme.key_windowBackgroundWhiteBlackText
                )
        );

        changelogContainer.addView(
                heading,
                LayoutHelper.createLinear(
                        LayoutHelper.MATCH_PARENT,
                        LayoutHelper.WRAP_CONTENT,
                        0,
                        0,
                        0,
                        8
                )
        );

        // --------------------------------------------------------
        // Features
        // --------------------------------------------------------

        if (hasFeatures) {

            for (String feature : features) {

                if (TextUtils.isEmpty(
                        feature
                )) {
                    continue;
                }

                addFeature(
                        feature
                );
            }
        }

        // --------------------------------------------------------
        // Optional detailed changelog
        // --------------------------------------------------------

        if (hasChangelog) {

            if (hasFeatures) {

                View divider =
                        new View(activity);

                divider.setBackgroundColor(
                        Theme.getColor(
                                Theme.key_divider
                        )
                );

                changelogContainer.addView(
                        divider,
                        LayoutHelper.createLinear(
                                LayoutHelper.MATCH_PARENT,
                                1,
                                0,
                                8,
                                0,
                                8
                        )
                );
            }

            TextView details =
                    new TextView(activity);

            details.setText(
                    changelog
            );

            details.setTextSize(14);

            details.setTextColor(
                    Theme.getColor(
                            Theme.key_windowBackgroundWhiteBlackText
                    )
            );

            details.setLineSpacing(
                    dp(2),
                    1.0f
            );

            changelogContainer.addView(
                    details,
                    LayoutHelper.createLinear(
                            LayoutHelper.MATCH_PARENT,
                            LayoutHelper.WRAP_CONTENT
                    )
            );
        }

        changelogScroll.setVisibility(
                View.VISIBLE
        );
    }

    // ============================================================
    // SINGLE FEATURE
    // ============================================================

    private void addFeature(
            String feature
    ) {

        LinearLayout row =
                new LinearLayout(activity);

        row.setOrientation(
                LinearLayout.HORIZONTAL
        );

        row.setGravity(
                Gravity.CENTER_VERTICAL
        );

        // --------------------------------------------------------
        // Bullet
        // --------------------------------------------------------

        TextView bullet =
                new TextView(activity);

        bullet.setText(
                "•"
        );

        bullet.setTextSize(18);

        bullet.setGravity(
                Gravity.CENTER
        );

        bullet.setTextColor(
                Theme.getColor(
                        Theme.key_windowBackgroundWhiteBlueText
                )
        );

        row.addView(
                bullet,
                LayoutHelper.createLinear(
                        20,
                        LayoutHelper.WRAP_CONTENT
                )
        );

        // --------------------------------------------------------
        // Feature text
        // --------------------------------------------------------

        TextView text =
                new TextView(activity);

        text.setText(
                feature
        );

        text.setTextSize(14);

        text.setTextColor(
                Theme.getColor(
                        Theme.key_windowBackgroundWhiteBlackText
                )
        );

        text.setLineSpacing(
                dp(1),
                1.0f
        );

        row.addView(
                text,
                LayoutHelper.createLinear(
                        0,
                        LayoutHelper.WRAP_CONTENT,
                        1f,
                        0,
                        0,
                        0,
                        6
                )
        );

        changelogContainer.addView(
                row,
                LayoutHelper.createLinear(
                        LayoutHelper.MATCH_PARENT,
                        LayoutHelper.WRAP_CONTENT,
                        0,
                        0,
                        0,
                        5
                )
        );
    }

    // ============================================================
    // BUTTON
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
                        : 0.55f
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

        busy = true;

        button.resetProgress();

        button.setProgress(
                0
        );

        int accentColor =
                Theme.getColor(
                        Theme.key_windowBackgroundWhiteBlueText
                );

        button.setButtonColor(
                accentColor
        );

        button.setProgressColor(
                accentColor
        );

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

        // --------------------------------------------------------
        // Download (continues in the background even if this sheet
        // is dismissed - see downloadCallback/onDownload* below).
        // --------------------------------------------------------

        AppUpdateController.download(
                info,
                downloadCallback
        );
    }

    // ============================================================
    // DOWNLOAD CALLBACKS
    // ============================================================
    //
    // Split out from onButtonClick so the same callback instance can be
    // (re)attached from showUpdate() when this sheet is reopened onto an
    // already-running or already-finished background download, not just
    // when the user just pressed the button.

    private void onDownloadProgress(int percent) {

        busy = true;

        button.setProgress(
                percent
        );

        setButton(
                "Downloading… "
                        + percent
                        + "%",
                false
        );
    }

    private void onDownloadDone(File file) {

        busy = false;

        downloaded = file;

        button.setProgress(
                100
        );

        button.setText(
                "Install"
        );

        button.setEnabled(true);
        button.setAlpha(1.0f);

        loader.setVisibility(
                View.GONE
        );

        iconView.setVisibility(
                View.VISIBLE
        );
    }

    private void onDownloadError() {

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

        button.resetProgress();

        setButton(
                "Download failed — Retry",
                true
        );
    }

    // ============================================================
    // DISMISS
    // ============================================================
    //
    // Only detaches this sheet's UI callback - the download itself (held by
    // AppUpdateController as static/background state) keeps running so it
    // finishes even with the sheet closed.

    @Override
    public void dismiss() {
        AppUpdateController.removeListener(downloadCallback);
        super.dismiss();
    }

    // ============================================================
    // ICON
    // ============================================================

    private void applyIconTint() {

        iconView.setColorFilter(
                Theme.getColor(
                        Theme.key_windowBackgroundWhiteBlueText
                ),
                PorterDuff.Mode.SRC_IN
        );
    }

    // ============================================================
    // DOWNLOAD PROGRESS BUTTON
    // ============================================================

    private static class DownloadProgressButton
            extends TextView {

        private final Paint backgroundPaint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );

        private final Paint progressPaint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );

        private final Paint textPaint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );

        private final RectF rect =
                new RectF();

        private final Path clipPath =
                new Path();

        private float progress = 0f;

        private int buttonColor =
                Color.rgb(
                        51,
                        144,
                        236
                );

        private int progressColor =
                Color.rgb(
                        51,
                        144,
                        236
                );

        private float cornerRadius;

        public DownloadProgressButton(
                Context context
        ) {

            super(context);

            setWillNotDraw(false);

            setClickable(true);

            setFocusable(true);

            backgroundPaint.setStyle(
                    Paint.Style.FILL
            );

            progressPaint.setStyle(
                    Paint.Style.FILL
            );

            textPaint.setAntiAlias(
                    true
            );

            textPaint.setTextAlign(
                    Paint.Align.CENTER
            );

            setBackgroundColor(
                    Color.TRANSPARENT
            );
        }

        public void setButtonColor(
                int color
        ) {

            buttonColor = color;

            invalidate();
        }

        public void setProgressColor(
                int color
        ) {

            progressColor = color;

            invalidate();
        }

        public void setProgress(
                float value
        ) {

            progress =
                    Math.max(
                            0f,
                            Math.min(
                                    100f,
                                    value
                            )
                    );

            invalidate();
        }

        public void resetProgress() {

            progress = 0f;

            invalidate();
        }

        @Override
        protected void onDraw(
                Canvas canvas
        ) {

            cornerRadius =
                    dp(12);

            rect.set(
                    0,
                    0,
                    getWidth(),
                    getHeight()
            );

            clipPath.reset();

            clipPath.addRoundRect(
                    rect,
                    cornerRadius,
                    cornerRadius,
                    Path.Direction.CW
            );

            int save =
                    canvas.save();

            canvas.clipPath(
                    clipPath
            );

            // ----------------------------------------------------
            // Base
            // ----------------------------------------------------

            backgroundPaint.setColor(
                    buttonColor
            );

            canvas.drawRect(
                    0,
                    0,
                    getWidth(),
                    getHeight(),
                    backgroundPaint
            );

            // ----------------------------------------------------
            // Progress
            // ----------------------------------------------------

            if (progress > 0f) {

                float progressWidth =
                        getWidth()
                                * (
                                progress
                                        / 100f
                        );

                progressPaint.setColor(
                        progressColor
                );

                canvas.drawRect(
                        0,
                        0,
                        progressWidth,
                        getHeight(),
                        progressPaint
                );
            }

            canvas.restoreToCount(
                    save
            );

            // ----------------------------------------------------
            // Text
            // ----------------------------------------------------

            drawCenteredText(
                    canvas
            );
        }

        private void drawCenteredText(
                Canvas canvas
        ) {

            CharSequence value =
                    getText();

            if (value == null) {
                return;
            }

            String text =
                    value.toString();

            if (TextUtils.isEmpty(
                    text
            )) {
                return;
            }

            textPaint.setColor(
                    Color.WHITE
            );

            textPaint.setTextSize(
                    getTextSize()
            );

            textPaint.setTypeface(
                    AndroidUtilities.bold()
            );

            Paint.FontMetrics metrics =
                    textPaint.getFontMetrics();

            float x =
                    getWidth()
                            / 2f;

            float y =
                    getHeight()
                            / 2f
                            - (
                            metrics.ascent
                                    + metrics.descent
                    ) / 2f;

            canvas.drawText(
                    text,
                    x,
                    y,
                    textPaint
            );
        }
    }
}