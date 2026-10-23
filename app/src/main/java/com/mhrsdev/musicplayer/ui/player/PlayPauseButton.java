package com.mhrsdev.musicplayer.ui.player;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;

/**
 * Shape-morphing play/pause (DESIGN §4/§9): paused = squircle, playing = 9-lobed cookie that turns once per 24 s.
 * Both shapes are polar radius functions sampled at {@link #N} points, so the morph is a per-point lerp into one
 * pre-allocated Path. Rotation runs only while playing, attached, visible and motion is allowed.
 */
public final class PlayPauseButton extends View {
    private static final int N = 90;
    private static final long TURN_MS = 24_000L;
    private final float[] squircle = new float[N];
    private final float[] cookie = new float[N];
    private final Path shape = new Path();
    private final Path icon = new Path();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float morph; // 0 squircle (paused) .. 1 cookie (playing)
    private boolean playing;
    private boolean motion = true;
    private long rotationBaseMs;
    private float rotationDeg;
    private ValueAnimator morphAnim;

    public PlayPauseButton(Context c, @Nullable AttributeSet a) {
        super(c, a);
        for (int i = 0; i < N; i++) {
            double t = 2 * Math.PI * i / N;
            double cs = Math.abs(Math.cos(t)), sn = Math.abs(Math.sin(t));
            squircle[i] = (float) Math.pow(Math.pow(cs, 4) + Math.pow(sn, 4), -0.25) * 0.86f;
            cookie[i] = (float) (0.93 + 0.07 * Math.cos(9 * t));
        }
        halo.setStyle(Paint.Style.FILL);
        setClickable(true);
        setFocusable(true);
        motion = PlayerPalette.motionAllowed(c);
        setColors(0xFFA9D4F5, 0xFF00344D);
    }

    public void setColors(int container, int onContainer) {
        fill.setColor(container);
        halo.setColor((container & 0x00FFFFFF) | 0x33000000);
        glyph.setColor(onContainer);
        invalidate();
    }

    public void setPlaying(boolean p, boolean animate) {
        if (p == playing && morphAnim == null && morph == (p ? 1f : 0f)) return;
        playing = p;
        setContentDescription(getResources().getString(p ? com.mhrsdev.musicplayer.R.string.player_pause
                : com.mhrsdev.musicplayer.R.string.player_play));
        if (morphAnim != null) morphAnim.cancel();
        float target = p ? 1f : 0f;
        if (!animate || !motion) {
            morph = target;
            morphAnim = null;
        } else {
            morphAnim = ValueAnimator.ofFloat(morph, target);
            morphAnim.setDuration(380);
            morphAnim.setInterpolator(new OvershootInterpolator(1.2f));
            morphAnim.addUpdateListener(v -> { morph = (float) v.getAnimatedValue(); invalidate(); });
            morphAnim.start();
        }
        rotationBaseMs = android.os.SystemClock.uptimeMillis() - (long) (rotationDeg / 360f * TURN_MS);
        invalidate();
    }

    @Override public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        animate().scaleX(pressed ? 0.92f : 1f).scaleY(pressed ? 0.92f : 1f).setDuration(pressed ? 90 : 220).start();
    }

    @Override protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) / 2f;
        canvas.drawCircle(cx, cy, r, halo);
        float rr = r * 0.82f;
        boolean spin = playing && motion && isShown();
        if (spin) rotationDeg = ((android.os.SystemClock.uptimeMillis() - rotationBaseMs) % TURN_MS) * 360f / TURN_MS;
        shape.rewind();
        for (int i = 0; i < N; i++) {
            double t = 2 * Math.PI * i / N + Math.toRadians(rotationDeg) * morph;
            float rad = rr * (squircle[i] + (cookie[i] - squircle[i]) * morph);
            float x = cx + (float) (Math.cos(t) * rad), y = cy + (float) (Math.sin(t) * rad);
            if (i == 0) shape.moveTo(x, y); else shape.lineTo(x, y);
        }
        shape.close();
        canvas.drawPath(shape, fill);
        drawGlyph(canvas, cx, cy, rr * 0.36f);
        if (spin) postInvalidateOnAnimation();
    }

    /** Pause bars fade in with the morph, the play triangle fades out (icon crossfade in the first 40%). */
    private void drawGlyph(Canvas c, float cx, float cy, float s) {
        float k = Math.max(0f, Math.min(1f, morph / 0.4f));
        int base = glyph.getColor();
        glyph.setAlpha((int) (255 * k));
        float bw = s * 0.42f, gap = s * 0.30f, rad = bw * 0.35f;
        c.drawRoundRect(cx - gap - bw, cy - s, cx - gap, cy + s, rad, rad, glyph);
        c.drawRoundRect(cx + gap, cy - s, cx + gap + bw, cy + s, rad, rad, glyph);
        glyph.setAlpha((int) (255 * (1f - k)));
        icon.rewind();
        icon.moveTo(cx - s * 0.62f, cy - s);
        icon.lineTo(cx + s * 0.98f, cy);
        icon.lineTo(cx - s * 0.62f, cy + s);
        icon.close();
        c.drawPath(icon, glyph);
        glyph.setColor(base);
    }

    @Override protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        if (visibility == VISIBLE) invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        if (morphAnim != null) morphAnim.cancel();
        super.onDetachedFromWindow();
    }
}
