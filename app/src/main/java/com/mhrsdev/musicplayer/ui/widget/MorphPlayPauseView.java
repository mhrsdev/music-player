package com.mhrsdev.musicplayer.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.ui.theme.MotionPolicy;
import com.mhrsdev.musicplayer.ui.theme.Tokens;

/**
 * Play/pause button whose container morphs from a squircle (paused) to a 10-lobe cookie (playing), with the
 * play triangle crossfading to pause bars in the first 40% of the morph. While playing it turns slowly
 * (1 turn / 24 s, 20 fps, only when visible and decorative motion is allowed). Buffering shows a small arc.
 * Square: size it with layout params (e.g. 88 dp in the player, 48 dp in the mini-player).
 */
public class MorphPlayPauseView extends View {
    private static final int POINTS = 120;
    private static final long SPIN_FRAME_MS = 50L;

    private final Paint containerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final Path playIcon = new Path();
    private final Path pauseIcon = new Path();
    private final RectF arcRect = new RectF();
    private final ValueAnimator morphAnim = ValueAnimator.ofFloat(0f, 1f);
    private final long origin = SystemClock.uptimeMillis();

    private boolean playing;
    private boolean buffering;
    private float morph; // 0 squircle/play .. 1 cookie/pause
    private float rotationDeg;
    private long lastFrame;
    private boolean visibleToUser;
    private boolean motionAllowed = true;
    @ColorInt private int containerColor;
    @ColorInt private int iconColor;

    public MorphPlayPauseView(Context context) {
        this(context, null);
    }

    public MorphPlayPauseView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setClickable(true);
        setFocusable(true);
        containerPaint.setStyle(Paint.Style.FILL);
        iconPaint.setStyle(Paint.Style.FILL);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        morphAnim.setDuration(Tokens.MOTION_MORPH);
        morphAnim.setInterpolator(new OvershootInterpolator(1.6f));
        morphAnim.addUpdateListener(a -> {
            morph = (float) a.getAnimatedValue();
            invalidate();
        });
        setColors(Tokens.primary(context), Tokens.color(context, R.attr.mpOnPrimary));
        updateDescription();
    }

    /** Sets the state; {@code animate=false} jumps (use when binding a recycled view). */
    public void setPlaying(boolean playing, boolean animate) {
        if (this.playing == playing && !morphAnim.isRunning()) {
            if (!animate) setMorphNow(playing ? 1f : 0f);
            return;
        }
        this.playing = playing;
        motionAllowed = MotionPolicy.decorativeMotionEnabled(getContext());
        updateDescription();
        morphAnim.cancel();
        if (animate && motionAllowed && isLaidOut()) {
            morphAnim.setFloatValues(morph, playing ? 1f : 0f);
            morphAnim.start();
        } else {
            setMorphNow(playing ? 1f : 0f);
        }
        lastFrame = 0;
        invalidate();
    }

    public boolean isPlaying() {
        return playing;
    }

    public void setBuffering(boolean buffering) {
        if (this.buffering == buffering) return;
        this.buffering = buffering;
        invalidate();
    }

    public void setColors(@ColorInt int container, @ColorInt int icon) {
        containerColor = container;
        iconColor = icon;
        invalidate();
    }

    private void setMorphNow(float m) {
        morph = m;
        invalidate();
    }

    private void updateDescription() {
        setContentDescription(getContext().getString(playing ? R.string.mp_cd_pause : R.string.mp_cd_play));
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int def = (int) Tokens.dp(getContext(), 64f);
        int w = resolveSize(def, wSpec), h = resolveSize(def, hSpec);
        int s = Math.min(w, h);
        setMeasuredDimension(s, s);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float s = Math.min(w, h);
        float cx = w / 2f, cy = h / 2f, u = s * 0.11f;
        // Play: rounded-ish triangle, optically centred (+0.12u).
        playIcon.rewind();
        playIcon.moveTo(cx - u * 0.9f, cy - u * 1.25f);
        playIcon.lineTo(cx + u * 1.35f, cy);
        playIcon.lineTo(cx - u * 0.9f, cy + u * 1.25f);
        playIcon.close();
        pauseIcon.rewind();
        float bw = u * 0.62f, bh = u * 1.2f, gap = u * 0.42f, r = bw * 0.45f;
        pauseIcon.addRoundRect(cx - gap - bw, cy - bh, cx - gap, cy + bh, r, r, Path.Direction.CW);
        pauseIcon.addRoundRect(cx + gap, cy - bh, cx + gap + bw, cy + bh, r, r, Path.Direction.CW);
        arcPaint.setStrokeWidth(s * 0.045f);
        arcRect.set(cx - u * 1.3f, cy - u * 1.3f, cx + u * 1.3f, cy + u * 1.3f);
    }

    @Override public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        visibleToUser = isVisible;
        lastFrame = 0;
        if (isVisible) invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        motionAllowed = MotionPolicy.decorativeMotionEnabled(getContext());
    }

    @Override protected void onDetachedFromWindow() {
        morphAnim.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        boolean spin = playing && motionAllowed && visibleToUser && isEnabled();
        if (spin && lastFrame != 0) rotationDeg = (rotationDeg + 360f * (now - lastFrame) / Tokens.COOKIE_TURN_MS) % 360f;
        lastFrame = now;

        float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) / 2f;
        double rot = Math.toRadians(rotationDeg);
        shape.rewind();
        for (int i = 0; i <= POINTS; i++) {
            double t = WaveMath.TWO_PI * i / POINTS;
            float rr = r * ShapeMath.radius(t - rot, morph);
            float x = cx + rr * (float) Math.cos(t), y = cy + rr * (float) Math.sin(t);
            if (i == 0) shape.moveTo(x, y); else shape.lineTo(x, y);
        }
        shape.close();
        containerPaint.setColor(isEnabled() ? containerColor : 0x1FFFFFFF & containerColor | 0x1F000000);
        canvas.drawPath(shape, containerPaint);

        int iconAlphaBase = isEnabled() ? 255 : 97;
        if (buffering) {
            arcPaint.setColor(iconColor);
            arcPaint.setAlpha(iconAlphaBase);
            float sweepStart = motionAllowed ? (now - origin) % 1000L * 0.36f : 0f;
            canvas.drawArc(arcRect, sweepStart, 270f, false, arcPaint);
            if (motionAllowed && visibleToUser) postInvalidateOnAnimation();
            return;
        }
        // Icon crossfade happens in the first 40% of the morph.
        float k = Math.max(0f, Math.min(1f, morph / 0.4f));
        iconPaint.setColor(iconColor);
        if (k < 1f) {
            iconPaint.setAlpha(Math.round(iconAlphaBase * (1f - k)));
            canvas.drawPath(playIcon, iconPaint);
        }
        if (k > 0f) {
            iconPaint.setAlpha(Math.round(iconAlphaBase * k));
            canvas.drawPath(pauseIcon, iconPaint);
        }
        if (spin && !morphAnim.isRunning()) postInvalidateDelayed(SPIN_FRAME_MS);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (isEnabled() && motionAllowed) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                animate().scaleX(0.92f).scaleY(0.92f).setDuration(Tokens.MOTION_FAST).start();
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                animate().scaleX(1f).scaleY(1f).setDuration(Tokens.MOTION_MORPH)
                        .setInterpolator(new OvershootInterpolator(2f)).start();
            }
        }
        return super.onTouchEvent(e);
    }

    @Override public boolean performClick() {
        return super.performClick();
    }

    @Override public CharSequence getAccessibilityClassName() {
        return Button.class.getName();
    }
}
