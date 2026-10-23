package com.mhrsdev.musicplayer.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.SeekBar;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.ui.theme.MotionPolicy;
import com.mhrsdev.musicplayer.ui.theme.Tokens;

import java.util.Locale;

/**
 * Shared behaviour of all seek styles: touch + drag with haptic ticks every 10 s, keyboard and TalkBack
 * (scroll forward/backward = 10 s, set progress), a 48 dp touch target, LTR even in RTL, a line thumb that grows
 * while dragging, and battery-aware frame scheduling: subclasses get frames only while {@link #wantsFrames()} is
 * true, the view is visible, and decorative motion is allowed. No allocation in {@link #onDraw}.
 */
public abstract class BaseSeekView extends View implements SeekBarView {
    protected final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final Paint inactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final float density;
    protected final float strokeWidth;

    @ColorInt protected int activeColor;
    @ColorInt protected int inactiveColor;
    protected long duration;
    protected long position;
    protected long buffered;
    @Nullable protected float[] waveform;
    protected boolean playing;
    protected boolean motionAllowed = true;
    private boolean visibleToUser;
    private boolean dragging;
    private float thumbGrow; // 0 idle, 1 dragging
    private long lastFrameMs;
    private long lastTickBucket = -1;
    @Nullable private OnSeekListener listener;

    protected BaseSeekView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        strokeWidth = 4f * density;
        setLayoutDirection(LAYOUT_DIRECTION_LTR);
        setFocusable(true);
        setClickable(true);
        setContentDescription(context.getString(R.string.mp_cd_seek));
        activePaint.setStyle(Paint.Style.STROKE);
        activePaint.setStrokeCap(Paint.Cap.ROUND);
        activePaint.setStrokeJoin(Paint.Join.ROUND);
        activePaint.setStrokeWidth(strokeWidth);
        inactivePaint.setStyle(Paint.Style.STROKE);
        inactivePaint.setStrokeCap(Paint.Cap.ROUND);
        inactivePaint.setStrokeWidth(strokeWidth);
        thumbPaint.setStyle(Paint.Style.FILL);
        setColors(Tokens.primary(context), Tokens.outlineVariant(context));
    }

    // ---- SeekBarView ----

    @Override public void setDuration(long durationMs) {
        if (duration == durationMs) return;
        duration = Math.max(0, durationMs);
        invalidate();
    }

    @Override public void setPosition(long positionMs) {
        if (dragging || position == positionMs) return;
        position = positionMs;
        invalidate();
    }

    @Override public void setBuffered(long bufferedMs) {
        if (buffered == bufferedMs) return;
        buffered = bufferedMs;
        invalidate();
    }

    @Override public void setWaveform(@Nullable float[] amplitudes) {
        waveform = amplitudes;
        onWaveformChanged();
        invalidate();
    }

    @Override public void setPlaying(boolean playing) {
        if (this.playing == playing) return;
        this.playing = playing;
        motionAllowed = MotionPolicy.decorativeMotionEnabled(getContext());
        lastFrameMs = 0;
        invalidate();
    }

    @Override public void setColors(@ColorInt int active, @ColorInt int inactive) {
        activeColor = active;
        inactiveColor = inactive;
        activePaint.setColor(active);
        inactivePaint.setColor(inactive);
        thumbPaint.setColor(active);
        invalidate();
    }

    @Override public void setOnSeekListener(@Nullable OnSeekListener l) { listener = l; }
    @Override public long getPosition() { return position; }
    @Override public long getDuration() { return duration; }
    @Override public boolean isDragging() { return dragging; }

    // ---- Subclass hooks ----

    /** Draw the track; {@code thumbX} splits active (left) from inactive (right). */
    protected abstract void drawTrack(Canvas canvas, float left, float right, float cy, float thumbX, long dtMs);

    /** True while the style needs further frames (e.g. the wave is travelling or settling). */
    protected boolean wantsFrames() { return false; }

    protected void onWaveformChanged() {}

    protected final boolean canAnimate() { return motionAllowed && visibleToUser; }

    protected final float trackLeft() { return getPaddingLeft() + 8f * density; }

    protected final float trackRight() { return getWidth() - getPaddingRight() - 8f * density; }

    // ---- Layout / drawing ----

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int minH = (int) (48f * density) + getPaddingTop() + getPaddingBottom();
        int w = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        setMeasuredDimension(w, resolveSize(minH, heightMeasureSpec));
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        motionAllowed = MotionPolicy.decorativeMotionEnabled(getContext());
    }

    @Override public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        visibleToUser = isVisible;
        if (isVisible) {
            lastFrameMs = 0;
            invalidate();
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        long dt = lastFrameMs == 0 ? 0 : Math.min(100, now - lastFrameMs);
        lastFrameMs = now;
        float left = trackLeft(), right = trackRight();
        float cy = getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom()) / 2f;
        float thumbX = SeekMath.xFor(position, duration, left, right);

        float growTarget = dragging ? 1f : 0f;
        thumbGrow = motionAllowed ? WaveMath.approach(thumbGrow, growTarget, dt, Tokens.MOTION_FAST) : growTarget;

        drawTrack(canvas, left, right, cy, thumbX, dt);

        // Line thumb: 4 dp wide, 20 dp tall, 32 dp while dragging.
        float half = (10f + 6f * thumbGrow) * density;
        float tw = (2f + 1f * thumbGrow) * density;
        canvas.drawRoundRect(thumbX - tw, cy - half, thumbX + tw, cy + half, tw, tw, thumbPaint);

        boolean more = thumbGrow != growTarget || (wantsFrames() && canAnimate());
        if (more) postInvalidateDelayed(Tokens.DECOR_FRAME_MS);
    }

    // ---- Input ----

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled() || duration <= 0) return false;
        float left = trackLeft(), right = trackRight();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                dragging = true;
                position = SeekMath.positionFor(e.getX(), left, right, duration);
                lastTickBucket = position / Tokens.SEEK_STEP_MS;
                if (listener != null) listener.onSeekStart(position);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging) return false;
                position = SeekMath.positionFor(e.getX(), left, right, duration);
                long bucket = position / Tokens.SEEK_STEP_MS;
                if (bucket != lastTickBucket) {
                    lastTickBucket = bucket;
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                }
                if (listener != null) listener.onSeek(position);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) performClick();
                // fall through
            case MotionEvent.ACTION_CANCEL:
                if (!dragging) return false;
                dragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                if (listener != null) listener.onSeekEnd(position);
                invalidate();
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    @Override public boolean performClick() {
        return super.performClick();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return seekBy(-Tokens.SEEK_STEP_MS);
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return seekBy(Tokens.SEEK_STEP_MS);
        return super.onKeyDown(keyCode, event);
    }

    private boolean seekTo(long target) {
        if (duration <= 0) return false;
        position = Math.max(0, Math.min(duration, target));
        if (listener != null) {
            listener.onSeekStart(position);
            listener.onSeekEnd(position);
        }
        invalidate();
        return true;
    }

    private boolean seekBy(long delta) {
        return seekTo(SeekMath.step(position, delta, duration));
    }

    // ---- Accessibility ----

    @Override public CharSequence getAccessibilityClassName() {
        return SeekBar.class.getName();
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, 0f, duration / 1000f, position / 1000f));
        if (isEnabled() && duration > 0) {
            if (position > 0) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
            if (position < duration) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
        }
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            Locale l = getResources().getConfiguration().getLocales().get(0);
            info.setStateDescription(getContext().getString(R.string.mp_seek_state,
                    SeekMath.formatTime(position, l), SeekMath.formatTime(duration, l)));
        }
    }

    @Override public boolean performAccessibilityAction(int action, @Nullable Bundle args) {
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) return seekBy(Tokens.SEEK_STEP_MS);
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) return seekBy(-Tokens.SEEK_STEP_MS);
        if (action == android.R.id.accessibilityActionSetProgress && args != null) {
            float sec = args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, position / 1000f);
            return seekTo((long) (sec * 1000f));
        }
        return super.performAccessibilityAction(action, args);
    }
}
