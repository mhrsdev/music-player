package com.mhrsdev.musicplayer.ui.player;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.Nullable;

/**
 * Player seek bar with three styles (setting "seek_bar_style"): WAVY (default; sine on the played part whose phase
 * travels while playing), WAVEFORM (layered loudness envelope) and BARS (voice-memo bars from the real waveform).
 * Temporary player-local implementation until T2's SeekBarView family is merged; same public shape
 * (setProgress / setWaveform / setPlaying / listener). Always LTR (tape direction, DESIGN §11.2).
 * Draw-phase only: no allocation in onDraw, animation only while playing + shown + motion allowed.
 */
public final class PlayerSeekBar extends View {
    public enum Style { WAVY, WAVEFORM, BARS }

    public interface Listener {
        void onScrub(float fraction);
        void onSeek(float fraction);
        void onStep(int direction);
    }

    private static final int BAR_COUNT = 52;
    private final Paint active = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inactive = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint layer = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float dp;
    private Style style = Style.WAVY;
    private float progress;
    private boolean dragging;
    private boolean playing;
    private final boolean motion;
    private float amplitude; // 0..1, eases to 0 on pause
    private ValueAnimator ampAnim;
    private float[] wave;
    private final float[] bars = new float[BAR_COUNT];
    private int lastTickSecond = -1;
    private long durationMs;
    @Nullable private Listener listener;

    public PlayerSeekBar(Context c, @Nullable AttributeSet a) {
        super(c, a);
        dp = getResources().getDisplayMetrics().density;
        setLayoutDirection(LAYOUT_DIRECTION_LTR);
        active.setStyle(Paint.Style.STROKE);
        active.setStrokeCap(Paint.Cap.ROUND);
        active.setStrokeWidth(4 * dp);
        inactive.set(active);
        inactive.setColor(0x47FFFFFF);
        thumb.setStrokeCap(Paint.Cap.ROUND);
        thumb.setStrokeWidth(4 * dp);
        thumb.setColor(0xFFFFFFFF);
        layer.setStyle(Paint.Style.FILL);
        motion = PlayerPalette.motionAllowed(c);
        setColor(0xFFA9D4F5);
        setFocusable(true);
        setClickable(true);
    }

    public void setListener(@Nullable Listener l) { listener = l; }

    public void setStyle(Style s) { style = s; invalidate(); }

    public void setColor(int c) { active.setColor(c); layer.setColor(c); thumb.setColor(c); invalidate(); }

    public void setDuration(long ms) { durationMs = ms; }

    public void setProgress(float f) {
        if (dragging) return;
        progress = Math.max(0f, Math.min(1f, f));
        invalidate();
    }

    /** Loudness buckets in [0,1]; null falls back to wavy-looking placeholder heights. */
    public void setWaveform(@Nullable float[] w) {
        wave = w;
        for (int i = 0; i < BAR_COUNT; i++) {
            if (w == null || w.length == 0) { bars[i] = 0.18f; continue; }
            int from = i * w.length / BAR_COUNT, to = Math.max(from + 1, (i + 1) * w.length / BAR_COUNT);
            float m = 0f;
            for (int j = from; j < to && j < w.length; j++) m = Math.max(m, w[j]);
            bars[i] = Math.max(0.10f, m);
        }
        invalidate();
    }

    public void setPlaying(boolean p) {
        if (p == playing && ampAnim == null) return;
        playing = p;
        if (ampAnim != null) ampAnim.cancel();
        float target = p ? 1f : 0f;
        if (!motion) { amplitude = p ? 1f : 0f; ampAnim = null; invalidate(); return; }
        ampAnim = ValueAnimator.ofFloat(amplitude, target).setDuration(300);
        ampAnim.addUpdateListener(v -> { amplitude = (float) v.getAnimatedValue(); invalidate(); });
        ampAnim.start();
    }

    @Override protected void onMeasure(int w, int h) {
        int desired = (int) (48 * dp) + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), w), resolveSize(desired, h));
    }

    @Override protected void onDraw(Canvas c) {
        float left = getPaddingLeft() + 6 * dp, right = getWidth() - getPaddingRight() - 6 * dp;
        float cy = getHeight() * (style == Style.WAVY ? 0.5f : 0.72f);
        float x = left + (right - left) * progress;
        switch (style) {
            case WAVY: drawWavy(c, left, right, cy, x); break;
            case WAVEFORM: drawWaveform(c, left, right, cy, x); break;
            default: drawBars(c, left, right, x); break;
        }
        float th = style == Style.BARS ? getHeight() * 0.40f : 12 * dp;
        float tcy = style == Style.BARS ? getHeight() / 2f : cy;
        c.drawLine(x, tcy - th, x, tcy + th, thumb);
        if (style == Style.WAVY && playing && motion && amplitude > 0f && isShown()) postInvalidateOnAnimation();
    }

    private void drawWavy(Canvas c, float left, float right, float cy, float x) {
        float amp = 3 * dp * amplitude, wl = 26 * dp;
        float phase = motion ? (SystemClock.uptimeMillis() % 1600L) / 1600f * (float) (2 * Math.PI) : 0f;
        path.rewind();
        path.moveTo(left, cy);
        float end = Math.max(left, x - 8 * dp);
        for (float px = left; px <= end; px += 2 * dp) {
            path.lineTo(px, cy + amp * (float) Math.sin((px - left) / wl * 2 * Math.PI - phase));
        }
        c.drawPath(path, active);
        if (x + 8 * dp < right) c.drawLine(x + 8 * dp, cy, right, cy, inactive);
    }

    private void drawWaveform(Canvas c, float left, float right, float base, float x) {
        float h = getHeight() * 0.55f;
        int[] alphas = {107, 128, 217};
        for (int l = 0; l < 3; l++) {
            path.rewind();
            path.moveTo(left, base);
            int n = wave == null ? 0 : wave.length;
            for (int i = 0; i <= 60; i++) {
                float f = i / 60f;
                float v = n == 0 ? 0.15f : wave[Math.min(n - 1, (int) ((f + l * 0.013f) * (n - 1)))];
                path.lineTo(left + (right - left) * f, base - h * v * (1f - l * 0.22f));
            }
            path.lineTo(right, base);
            path.close();
            c.save();
            layer.setColor(inactive.getColor());
            layer.setAlpha(alphas[l] / 3);
            c.drawPath(path, layer);
            c.clipRect(left, 0, x, getHeight());
            layer.setColor(active.getColor());
            layer.setAlpha(alphas[l]);
            c.drawPath(path, layer);
            c.restore();
        }
        c.drawLine(left, base, x, base, active);
        c.drawLine(x, base, right, base, inactive);
    }

    private void drawBars(Canvas c, float left, float right, float x) {
        float step = (right - left) / BAR_COUNT, bw = Math.max(2 * dp, step * 0.55f), cy = getHeight() / 2f;
        float maxH = getHeight() * 0.42f;
        Paint p;
        for (int i = 0; i < BAR_COUNT; i++) {
            float bx = left + step * (i + 0.5f);
            p = bx <= x ? active : inactive;
            float half = Math.max(bw / 2f, maxH * bars[i]);
            float w0 = p.getStrokeWidth();
            p.setStrokeWidth(bw);
            c.drawLine(bx, cy - half, bx, cy + half, p);
            p.setStrokeWidth(w0);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) return false;
        float left = getPaddingLeft() + 6 * dp, right = getWidth() - getPaddingRight() - 6 * dp;
        float f = Math.max(0f, Math.min(1f, (e.getX() - left) / Math.max(1f, right - left)));
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                dragging = true;
                setPressed(true);
                // fall through
            case MotionEvent.ACTION_MOVE:
                progress = f;
                int sec10 = (int) (f * durationMs / 10_000L);
                if (sec10 != lastTickSecond && lastTickSecond != -1) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                lastTickSecond = sec10;
                if (listener != null) listener.onScrub(f);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                dragging = false;
                setPressed(false);
                lastTickSecond = -1;
                progress = f;
                if (listener != null) listener.onSeek(f);
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                setPressed(false);
                lastTickSecond = -1;
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.SeekBar");
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_PERCENT, 0f, 100f, progress * 100f));
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }

    @Override public boolean performAccessibilityAction(int action, @Nullable Bundle args) {
        if (listener != null && action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) { listener.onStep(1); return true; }
        if (listener != null && action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) { listener.onStep(-1); return true; }
        return super.performAccessibilityAction(action, args);
    }

    @Override protected void onDetachedFromWindow() {
        if (ampAnim != null) ampAnim.cancel();
        super.onDetachedFromWindow();
    }
}
