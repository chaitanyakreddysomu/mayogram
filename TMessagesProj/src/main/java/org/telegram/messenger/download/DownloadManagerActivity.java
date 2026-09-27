/*
 * Mayogram download manager screen.
 *
 * First-cut UI: a plain scrolling list (not yet the RecyclerView-based cell
 * style the rest of Telegram's settings screens use) since download counts
 * are small (tens, not thousands) and this keeps the screen self-contained
 * while the rest of the download-manager feature set (bulk channel download,
 * per-category filters) is still being built. Reusing Theme.* color keys and
 * BaseFragment conventions throughout, same as the rest of the app.
 */

package org.telegram.messenger.download;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.DialogsActivity;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.Locale;

public class DownloadManagerActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private LinearLayout listContainer;
    private TextView emptyView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString(R.string.DownloadManagerTitle));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 1) {
                    MayoDownloadManager.getInstance(currentAccount).clearCompleted();
                    reload();
                }
            }
        });
        actionBar.createMenu().addItem(1, LocaleController.getString(R.string.DownloadManagerClearCompleted));

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        ScrollView scrollView = new ScrollView(context);
        listContainer = new LinearLayout(context);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        scrollView.addView(listContainer, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new TextView(context);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        emptyView.setPadding(AndroidUtilities.dp(32), AndroidUtilities.dp(64), AndroidUtilities.dp(32), 0);
        emptyView.setText(LocaleController.getString(R.string.DownloadManagerEmpty));
        root.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        fragmentView = root;
        reload();
        return fragmentView;
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, MayoDownloadManager.EVENT_DOWNLOAD_UPDATED);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, MayoDownloadManager.EVENT_DOWNLOAD_UPDATED);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == MayoDownloadManager.EVENT_DOWNLOAD_UPDATED && account == currentAccount) {
            reload();
        }
    }

    private void reload() {
        if (listContainer == null) {
            return;
        }
        ArrayList<DownloadRecord> records = MayoDownloadManager.getInstance(currentAccount).getAll();
        listContainer.removeAllViews();
        emptyView.setVisibility(records.isEmpty() ? View.VISIBLE : View.GONE);
        Context context = listContainer.getContext();
        for (DownloadRecord record : records) {
            listContainer.addView(buildRow(context, record));
        }
    }

    private View buildRow(Context context, DownloadRecord record) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
        if (record.status == DownloadRecord.STATUS_COMPLETED) {
            row.setLongClickable(true);
            row.setOnLongClickListener(v -> {
                openSharePicker(record);
                return true;
            });
        }
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = AndroidUtilities.dp(2);
        row.setLayoutParams(rowParams);

        TextView title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setMaxLines(1);
        title.setText((record.dialogTitle != null ? record.dialogTitle : "") +
                "  ·  " + record.category.folderName);
        row.addView(title);

        TextView subtitle = new TextView(context);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitle.setText(statusLabel(record) + "  ·  " + sizeLabel(record));
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = AndroidUtilities.dp(2);
        row.addView(subtitle, subtitleParams);

        if (record.status == DownloadRecord.STATUS_DOWNLOADING || record.status == DownloadRecord.STATUS_PAUSED) {
            ProgressBar progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setMax(1000);
            int percent = record.totalSize > 0
                    ? (int) (1000L * record.downloadedSize / record.totalSize) : 0;
            progressBar.setProgress(percent);
            LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(4));
            pbParams.topMargin = AndroidUtilities.dp(6);
            row.addView(progressBar, pbParams);
        }

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = AndroidUtilities.dp(6);
        row.addView(actions, actionsParams);

        if (record.status == DownloadRecord.STATUS_DOWNLOADING) {
            actions.addView(actionButton(context, R.string.DownloadManagerPause,
                    v -> MayoDownloadManager.getInstance(currentAccount).pause(record)));
        } else if (record.status == DownloadRecord.STATUS_PAUSED || record.status == DownloadRecord.STATUS_FAILED) {
            actions.addView(actionButton(context, R.string.DownloadManagerResume,
                    v -> MayoDownloadManager.getInstance(currentAccount).resume(record)));
        }
        if (record.status != DownloadRecord.STATUS_COMPLETED) {
            actions.addView(actionButton(context, R.string.DownloadManagerCancel,
                    v -> MayoDownloadManager.getInstance(currentAccount).cancel(record, true)));
        }

        return row;
    }

    private TextView actionButton(Context context, int stringRes, View.OnClickListener listener) {
        TextView button = new TextView(context);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        button.setTypeface(AndroidUtilities.bold());
        button.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        button.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(4), AndroidUtilities.dp(10), AndroidUtilities.dp(4));
        button.setText(LocaleController.getString(stringRes));
        button.setOnClickListener(listener);
        return button;
    }

    private String statusLabel(DownloadRecord record) {
        switch (record.status) {
            case DownloadRecord.STATUS_QUEUED:
                return LocaleController.getString(R.string.DownloadManagerStatusQueued);
            case DownloadRecord.STATUS_DOWNLOADING:
                return LocaleController.getString(R.string.DownloadManagerStatusDownloading);
            case DownloadRecord.STATUS_PAUSED:
                return LocaleController.getString(R.string.DownloadManagerStatusPaused);
            case DownloadRecord.STATUS_COMPLETED:
                return LocaleController.getString(R.string.DownloadManagerStatusCompleted);
            default:
                return LocaleController.getString(R.string.DownloadManagerStatusFailed);
        }
    }

    private void openSharePicker(DownloadRecord record) {
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        args.putBoolean("allowSwitchAccount", false);
        DialogsActivity fragment = new DialogsActivity(args);
        fragment.setDelegate((DialogsActivity picker, ArrayList<MessagesStorage.TopicKey> dids,
                               CharSequence message, boolean param, boolean notify,
                               int scheduleDate, int scheduleRepeatPeriod, org.telegram.ui.TopicsFragment topicsFragment) -> {
            ArrayList<Long> dialogIds = new ArrayList<>();
            for (MessagesStorage.TopicKey key : dids) {
                dialogIds.add(key.dialogId);
            }
            MayoDownloadManager.ShareResult result =
                    MayoDownloadManager.getInstance(currentAccount).shareToChats(record, dialogIds);
            if (result == MayoDownloadManager.ShareResult.FILE_MISSING) {
                if (getParentActivity() != null) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setMessage(LocaleController.getString(R.string.DownloadManagerShareFileMissing));
                    builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
                    builder.show();
                }
            }
            picker.finishFragment();
            return true;
        });
        presentFragment(fragment);
    }

    private String sizeLabel(DownloadRecord record) {
        if (record.totalSize <= 0) {
            return AndroidUtilities.formatFileSize(record.downloadedSize);
        }
        return AndroidUtilities.formatFileSize(record.downloadedSize) + " / " + AndroidUtilities.formatFileSize(record.totalSize);
    }
}
