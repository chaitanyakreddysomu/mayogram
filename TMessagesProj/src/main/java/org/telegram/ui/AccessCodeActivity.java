/*
 * Mayogram access-code screen.
 *
 * Shown before Telegram's own login. Redeeming a one-time code authorises this
 * installation to use the app; it has nothing to do with the Telegram account,
 * which is still authenticated by phone/OTP/2FA afterwards.
 */

package org.telegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AccessCodeController;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.FragmentFloatingButton;
import org.telegram.ui.Components.LayoutHelper;

public class AccessCodeActivity extends BaseFragment {

    private EditTextBoldCursor codeField;
    private TextView errorTextView;
    private FragmentFloatingButton floatingButton;
    private boolean redeeming;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString(R.string.AccessCodeTitle));
        actionBar.setAllowOverlayTitle(true);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        root.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(48),
                AndroidUtilities.dp(24), AndroidUtilities.dp(24));

        TextView title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setText(LocaleController.getString(R.string.AccessCodeTitle));
        root.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 8));

        TextView subtitle = new TextView(context);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitle.setText(LocaleController.getString(R.string.AccessCodeSubtitle));
        root.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 32));

        codeField = new EditTextBoldCursor(context);
        codeField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        codeField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        codeField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        codeField.setBackgroundDrawable(Theme.createEditTextDrawable(context, true));
        codeField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        codeField.setCursorSize(AndroidUtilities.dp(20));
        codeField.setCursorWidth(1.5f);
        codeField.setMaxLines(1);
        codeField.setGravity(Gravity.CENTER);
        codeField.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        codeField.setImeOptions(EditorInfo.IME_ACTION_DONE);
        codeField.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32),
                new InputFilter.AllCaps()});
        codeField.setHint(LocaleController.getString(R.string.AccessCodeHint));
        codeField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
        root.addView(codeField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                AndroidUtilities.dp(52), Gravity.CENTER_HORIZONTAL, 0, 0, 0, 12));

        errorTextView = new TextView(context);
        errorTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        errorTextView.setGravity(Gravity.CENTER);
        errorTextView.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        errorTextView.setVisibility(View.GONE);
        root.addView(errorTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 12));

        FrameLayout container = new FrameLayout(context);
        container.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        container.addView(root, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        // Telegram's own small round FAB (same one PasscodeActivity/LoginActivity
        // use for "Next"/"Done") rather than a distinct full-width button.
        floatingButton = new FragmentFloatingButton(context, getResourceProvider());
        floatingButton.setImageResource(R.drawable.floating_check);
        floatingButton.setContentDescription(LocaleController.getString(R.string.AccessCodeContinue));
        floatingButton.setOnClickListener(v -> submit());
        container.addView(floatingButton, FragmentFloatingButton.createDefaultLayoutParamsBig());

        fragmentView = container;
        return fragmentView;
    }

    private void showError(int resId) {
        errorTextView.setText(LocaleController.getString(resId));
        errorTextView.setVisibility(View.VISIBLE);
        AndroidUtilities.shakeView(codeField);
    }

    private void submit() {
        if (redeeming) {
            return;
        }
        errorTextView.setVisibility(View.GONE);
        String code = codeField.getText() == null ? "" : codeField.getText().toString();
        if (code.trim().length() == 0) {
            showError(R.string.AccessCodeEmpty);
            return;
        }

        redeeming = true;
        AndroidUtilities.hideKeyboard(codeField);
        floatingButton.setProgressVisible(true, true);

        AccessCodeController.redeem(code, result -> {
            redeeming = false;
            floatingButton.setProgressVisible(false, true);
            if (result == AccessCodeController.RESULT_OK) {
                // Authorised: hand over to Telegram's own login flow.
                presentFragment(new IntroActivity(), true);
                return;
            }
            switch (result) {
                case AccessCodeController.RESULT_USED:
                    showError(R.string.AccessCodeUsed);
                    break;
                case AccessCodeController.RESULT_REVOKED:
                case AccessCodeController.RESULT_INVALID:
                    showError(R.string.AccessCodeInvalid);
                    break;
                case AccessCodeController.RESULT_EMPTY:
                    showError(R.string.AccessCodeEmpty);
                    break;
                default:
                    showError(R.string.AccessCodeConnectionError);
                    break;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.runOnUIThread(() -> {
            if (codeField != null) {
                codeField.requestFocus();
                AndroidUtilities.showKeyboard(codeField);
            }
        }, 100);
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        // The gate cannot be dismissed; leaving the screen means leaving the app.
        return false;
    }
}
