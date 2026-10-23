package com.mhrsdev.musicplayer.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.Checkable;
import android.widget.CompoundButton;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.ui.theme.Tokens;

/**
 * Filter / assist chip: 32 dp visual height inside a 48 dp touch target, 12 dp corners. Checked = secondaryContainer
 * fill; unchecked = 1 dp outline. Toggles on click when {@link #setCheckable(boolean)} is true.
 */
public class ChipView extends AppCompatTextView implements Checkable {
    public interface OnCheckedChangeListener {
        void onCheckedChanged(ChipView chip, boolean checked);
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float radius;
    private final float inset;
    private final int onColor;
    private final int offColor;
    private boolean checked;
    private boolean checkable = true;
    @Nullable private OnCheckedChangeListener listener;

    public ChipView(Context context) {
        this(context, null);
    }

    public ChipView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        radius = Tokens.dp(context, Tokens.RADIUS_CHIP);
        inset = Tokens.dp(context, 8f);
        setTextAppearance(R.style.TextAppearance_MusicPlayer_Label);
        setGravity(Gravity.CENTER);
        setMinHeight((int) Tokens.dp(context, 48f));
        int ph = (int) Tokens.dp(context, 16f);
        setPadding(ph, 0, ph, 0);
        setClickable(true);
        setFocusable(true);
        fill.setColor(Tokens.color(context, R.attr.mpSecondaryContainer));
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Tokens.dp(context, 1f));
        stroke.setColor(Tokens.color(context, R.attr.mpOutline));
        onColor = Tokens.color(context, R.attr.mpOnSecondaryContainer);
        offColor = Tokens.onSurfaceVariant(context);
        setTextColor(offColor);
        setOnClickListener(v -> { if (checkable) toggle(); });
    }

    public void setCheckable(boolean checkable) {
        this.checkable = checkable;
    }

    public void setOnCheckedChangeListener(@Nullable OnCheckedChangeListener l) {
        listener = l;
    }

    @Override public void setChecked(boolean c) {
        if (checked == c) return;
        checked = c;
        setTextColor(c ? onColor : offColor);
        refreshDrawableState();
        invalidate();
        if (listener != null) listener.onCheckedChanged(this, c);
    }

    @Override public boolean isChecked() {
        return checked;
    }

    @Override public void toggle() {
        setChecked(!checked);
    }

    @Override protected void onDraw(Canvas canvas) {
        float half = stroke.getStrokeWidth() / 2f;
        rect.set(half, inset, getWidth() - half, getHeight() - inset);
        if (checked) canvas.drawRoundRect(rect, radius, radius, fill);
        else canvas.drawRoundRect(rect, radius, radius, stroke);
        super.onDraw(canvas);
    }

    @Override public CharSequence getAccessibilityClassName() {
        return CompoundButton.class.getName();
    }

    @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setCheckable(checkable);
        info.setChecked(checked);
    }
}
