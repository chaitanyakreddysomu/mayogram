/*
 * Mayogram: small green "BETA" pill appended after a title - shown only in
 * the beta build type (BuildConfig.IS_BETA), next to the home screen title.
 */
package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;

public class BetaPillSpan extends ReplacementSpan {

    private static final int BG_COLOR = 0xFF2ECC71;

    public static CharSequence applySpan(CharSequence name) {
        SpannableStringBuilder ssb = new SpannableStringBuilder(name);
        ssb.append(" b");
        BetaPillSpan span = new BetaPillSpan();
        ssb.setSpan(span, ssb.length() - 1, ssb.length(), 0);
        return ssb;
    }

    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private StaticLayout layout;
    private float width, height;

    public BetaPillSpan() {
        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint.setTextSize(dp(11));
        textPaint.setColor(Color.WHITE);
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(BG_COLOR);
    }

    private void makeLayout() {
        if (layout == null) {
            layout = new StaticLayout("BETA", textPaint, AndroidUtilities.displaySize.x, Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
            width = layout.getLineWidth(0);
            height = layout.getHeight();
        }
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
        makeLayout();
        return (int) (dp(14) + width);
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        makeLayout();
        float left = x + dp(6);
        float topY = y - height + dp(2f);
        AndroidUtilities.rectTmp.set(left, topY, left + width, topY + height);
        AndroidUtilities.rectTmp.inset(dp(-5f), dp(-2f));
        float r = dp(8f);
        canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, bgPaint);
        canvas.save();
        canvas.translate(left, topY);
        layout.draw(canvas);
        canvas.restore();
    }
}
