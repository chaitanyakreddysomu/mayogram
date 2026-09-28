/*
 * Mayogram access-code screen.
 *
 * Shown before Telegram's own login. Redeeming a one-time code authorises this
 * installation to use the app; it has nothing to do with the Telegram account,
 * which is still authenticated by phone/OTP/2FA afterwards.
 *
 * 12-character codes (format XXXX-XXXX-XXXX, matching generate_access_codes()
 * in supabase/admin.sql) are entered box-by-box. Every box's raw input is
 * normalised through cleanCode() before it's used for anything, so typing or
 * pasting with spaces, hyphens, or lowercase all collapse to the same
 * A-Z0-9 characters - the string actually sent to the API is always rebuilt
 * from that cleaned form, never from what's on screen.
 */

package org.telegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
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

    private static final int BOX_COUNT = 12;

    private final EditTextBoldCursor[] codeBoxes = new EditTextBoldCursor[BOX_COUNT];

    private TextView errorTextView;
    private FragmentFloatingButton floatingButton;

    private boolean redeeming = false;
    private boolean updatingBoxes = false;

    private static final int COLOR_NORMAL = Color.rgb(70, 91, 108);
    private static final int COLOR_FOCUS = Color.rgb(85, 170, 235);
    private static final int COLOR_GREEN = Color.rgb(52, 199, 89);
    private static final int COLOR_RED = Color.rgb(255, 69, 58);

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString(R.string.AccessCodeTitle));
        actionBar.setAllowOverlayTitle(true);

        FrameLayout container = new FrameLayout(context);
        container.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(48),
                AndroidUtilities.dp(20), AndroidUtilities.dp(24));

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

        LinearLayout codeLayout = new LinearLayout(context);
        codeLayout.setOrientation(LinearLayout.HORIZONTAL);
        codeLayout.setGravity(Gravity.CENTER);

        for (int i = 0; i < BOX_COUNT; i++) {
            final int index = i;

            EditTextBoldCursor box = new EditTextBoldCursor(context);
            codeBoxes[i] = box;

            box.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            box.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            box.setGravity(Gravity.CENTER);
            box.setSingleLine(true);
            box.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
            box.setImeOptions(EditorInfo.IME_ACTION_NEXT);
            // No LengthFilter(1): paste needs to receive the complete string
            // in one shot so fillFromIndex() can redistribute it across boxes.
            box.setFilters(new InputFilter[]{new InputFilter.AllCaps()});
            box.setPadding(0, 0, 0, 0);
            setBoxBorder(box, COLOR_NORMAL);

            box.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    if (updatingBoxes) {
                        return;
                    }

                    String raw = editable == null ? "" : editable.toString();
                    String value = cleanCode(raw);

                    if (value.length() > 1) {
                        // Paste, or an IME that committed more than one
                        // character at once - redistribute across boxes
                        // starting here.
                        fillFromIndex(index, value);
                    } else if (value.length() == 1) {
                        updatingBoxes = true;
                        if (!value.equals(raw)) {
                            box.setText(value);
                        }
                        box.setSelection(box.length());
                        updatingBoxes = false;

                        clearError();

                        if (index < BOX_COUNT - 1) {
                            codeBoxes[index + 1].requestFocus();
                        }
                    } else if (!raw.isEmpty()) {
                        // Everything the user typed was filtered out (e.g. a
                        // hyphen or space typed directly into an empty box).
                        // Without this the box keeps showing that raw,
                        // invalid character even though it contributes
                        // nothing to the code actually sent to the API.
                        updatingBoxes = true;
                        box.setText("");
                        updatingBoxes = false;
                    }
                }
            });

            box.setOnFocusChangeListener((v, hasFocus) -> {
                if (!redeeming) {
                    setBoxBorder(box, hasFocus ? COLOR_FOCUS : COLOR_NORMAL);
                }
            });

            box.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (box.getText().length() == 0 && index > 0) {
                        EditTextBoldCursor previous = codeBoxes[index - 1];
                        previous.requestFocus();
                        previous.setSelection(previous.length());
                        return true;
                    }
                }
                return false;
            });

            LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(0, AndroidUtilities.dp(54), 1.0f);
            if (i > 0) {
                boxParams.leftMargin = AndroidUtilities.dp(2);
            }

            if (i == 4 || i == 8) {
                TextView separator = new TextView(context);
                separator.setText("-");
                separator.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
                separator.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                separator.setGravity(Gravity.CENTER);

                LinearLayout.LayoutParams separatorParams = new LinearLayout.LayoutParams(
                        AndroidUtilities.dp(10), AndroidUtilities.dp(54));
                separatorParams.leftMargin = AndroidUtilities.dp(1);
                separatorParams.rightMargin = AndroidUtilities.dp(1);
                codeLayout.addView(separator, separatorParams);

                boxParams.leftMargin = AndroidUtilities.dp(2);
            }

            codeLayout.addView(box, boxParams);
        }

        root.addView(codeLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                AndroidUtilities.dp(54), Gravity.CENTER_HORIZONTAL, 0, 0, 0, 10));

        errorTextView = new TextView(context);
        errorTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        errorTextView.setGravity(Gravity.CENTER);
        errorTextView.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        errorTextView.setVisibility(View.GONE);
        root.addView(errorTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 12));

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

    private String cleanCode(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toUpperCase(value.charAt(i));
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                result.append(c);
            }
        }
        return result.toString();
    }

    private void fillFromIndex(int startIndex, String value) {
        String cleaned = cleanCode(value);
        if (cleaned.isEmpty()) {
            return;
        }

        updatingBoxes = true;
        int position = startIndex;
        for (int i = 0; i < cleaned.length() && position < BOX_COUNT; i++) {
            codeBoxes[position].setText(String.valueOf(cleaned.charAt(i)));
            position++;
        }
        updatingBoxes = false;

        if (position < BOX_COUNT) {
            codeBoxes[position].requestFocus();
        } else {
            codeBoxes[BOX_COUNT - 1].requestFocus();
            codeBoxes[BOX_COUNT - 1].setSelection(codeBoxes[BOX_COUNT - 1].length());
        }

        clearError();
    }

    private void setBoxBorder(EditTextBoldCursor box, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.TRANSPARENT);
        drawable.setCornerRadius(AndroidUtilities.dp(7));
        drawable.setStroke(AndroidUtilities.dp(2), color);
        box.setBackground(drawable);
    }

    private String getCode() {
        StringBuilder code = new StringBuilder();
        for (EditTextBoldCursor box : codeBoxes) {
            if (box.getText() != null) {
                code.append(cleanCode(box.getText().toString()));
            }
        }
        return code.toString();
    }

    private String getFormattedCode() {
        String code = getCode();
        if (code.length() <= 4) {
            return code;
        }
        if (code.length() <= 8) {
            return code.substring(0, 4) + "-" + code.substring(4);
        }
        return code.substring(0, 4) + "-" + code.substring(4, 8) + "-" + code.substring(8);
    }

    private void setBoxesEnabled(boolean enabled) {
        for (EditTextBoldCursor box : codeBoxes) {
            if (box != null) {
                box.setEnabled(enabled);
            }
        }
    }

    private void submit() {
        if (redeeming) {
            return;
        }

        clearError();
        String code = getCode();

        if (code.length() != BOX_COUNT) {
            showError("Enter the complete 12-character code");
            setAllBorders(COLOR_RED);
            return;
        }

        redeeming = true;
        setBoxesEnabled(false);
        AndroidUtilities.hideKeyboard(codeBoxes[0]);
        floatingButton.setProgressVisible(true, true);

        String formattedCode = getFormattedCode();

        AccessCodeController.redeem(formattedCode, result -> {
            redeeming = false;
            setBoxesEnabled(true);
            floatingButton.setProgressVisible(false, true);

            if (result == AccessCodeController.RESULT_OK) {
                setAllBorders(COLOR_GREEN);
                // Small delay so the user can see the green state.
                AndroidUtilities.runOnUIThread(() -> presentFragment(new LoginActivity(), true), 300);
                return;
            }

            setAllBorders(COLOR_RED);
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

    private void showError(String message) {
        errorTextView.setText(message);
        errorTextView.setVisibility(View.VISIBLE);
        AndroidUtilities.shakeView(codeBoxes[0]);
    }

    private void showError(int resId) {
        showError(LocaleController.getString(resId));
    }

    private void clearError() {
        if (errorTextView != null) {
            errorTextView.setVisibility(View.GONE);
        }
        if (!redeeming) {
            setAllBorders(COLOR_NORMAL);
        }
    }

    private void setAllBorders(int color) {
        for (EditTextBoldCursor box : codeBoxes) {
            if (box != null) {
                setBoxBorder(box, color);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.runOnUIThread(() -> {
            if (codeBoxes[0] != null) {
                codeBoxes[0].requestFocus();
                AndroidUtilities.showKeyboard(codeBoxes[0]);
            }
        }, 100);
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        // The gate cannot be dismissed; leaving the screen means leaving the app.
        return false;
    }
}
