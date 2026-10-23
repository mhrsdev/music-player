package com.mhrsdev.musicplayer.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

/**
 * Pulse dots (D25): a row of dots sized by the real loudness; near the thumb they swell and lift like a wave passing
 * under a magnifier, and the thumb rides the crest. While playing (and visible, motion allowed) the crest breathes
 * gently at 30 fps through the shared FrameDriver; paused it is a still frame.
 */
public class PulseDotsSeekView extends FrameSeekView {
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float spacing;
    private final float lift;
    private float[] sizes = new float[0];
    private int highlight;
    private boolean shadersValid;
    private float crestY;

    public PulseDotsSeekView(Context context) {
        this(context, null);
    }

    public PulseDotsSeekView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        spacing = 7f * density;
        lift = 7f * density;
        dot.setStyle(Paint.Style.FILL);
        halo.setStyle(Paint.Style.FILL);
    }

    @Override protected boolean needsFrames() {
        return playing;
    }

    @Override public void setColors(@ColorInt int active, @ColorInt int inactive) {
        super.setColors(active, inactive);
        shadersValid = false;
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuild();
    }

    @Override protected void onWaveformChanged() {
        rebuild();
    }

    private void rebuild() {
        float span = trackRight() - trackLeft();
        int n = span <= 0 ? 0 : (int) (span / spacing) + 1;
        sizes = new float[n];
        for (int i = 0; i < n; i++) {
            float f0 = (i - 0.5f) / Math.max(1, n - 1), f1 = (i + 0.5f) / Math.max(1, n - 1);
            sizes[i] = SeekShapes.peakIn(waveform, f0, f1);
        }
    }

    @Override protected void drawTrack(Canvas canvas, float left, float right, float cy, float thumbX, long dt) {
        if (!shadersValid) {
            highlight = SeekShapes.blend(activeColor, 0xFFFFFFFF, 0.4f);
            halo.setShader(new RadialGradient(0f, 0f, 16f * density,
                    new int[] {SeekShapes.withAlpha(activeColor, 0.45f), 0}, null, Shader.TileMode.CLAMP));
            shadersValid = true;
        }
        float radius = (46f + 18f * dragGrow()) * density;
        float breathe = playing && isAnimating() ? 0.5f + 0.5f * (float) Math.sin(clock * 2.6f) : 0.5f;
        float n = sizes.length;
        float step = n > 1 ? (right - left) / (n - 1) : 0f;
        for (int i = 0; i < n; i++) {
            float x = left + i * step;
            float m = SeekShapes.magnify(x - thumbX, radius);
            // ripple: dots under the crest bob slightly out of phase while playing
            float wob = playing && isAnimating() ? 0.18f * m * (float) Math.sin(clock * 5f - i * 0.7f) : 0f;
            float base = (1.1f + 1.5f * sizes[i]) * density;
            float r = base * (1f + (0.9f + 0.25f * breathe) * m);
            float y = cy - lift * (m + wob);
            boolean played = x <= thumbX;
            dot.setColor(played ? SeekShapes.blend(activeColor, highlight, m) : inactiveColor);
            if (!played) dot.setAlpha(Math.min(255, Math.round((inactiveColor >>> 24) * (1f + 1.2f * m))));
            canvas.drawCircle(x, y, r, dot);
        }
        crestY = cy - lift;
    }

    @Override protected void drawThumb(Canvas canvas, float thumbX, float cy, float grow) {
        float y = crestY == 0f ? cy - lift : crestY;
        int save = canvas.save();
        canvas.translate(thumbX, y);
        canvas.drawCircle(0f, 0f, 16f * density, halo);
        canvas.restoreToCount(save);
        dot.setColor(highlight);
        canvas.drawCircle(thumbX, y, (5f + 2.5f * grow) * density, dot);
    }

    private float dragGrow() {
        return isDragging() ? 1f : 0f;
    }
}
