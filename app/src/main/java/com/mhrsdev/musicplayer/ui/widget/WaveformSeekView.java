package com.mhrsdev.musicplayer.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

/**
 * Waveform seek bar: three phase-shifted, smoothed loudness envelopes (alpha .42/.5/.85) rising from a baseline,
 * active color left of the thumb, inactive right. Paths are rebuilt only on size or waveform change; drawing is two
 * clipped passes. Static (no per-frame animation). Until {@link #setWaveform} delivers data it draws the wavy style.
 */
public class WaveformSeekView extends WavySeekView {
    private static final float[] LAYER_ALPHA = {0.42f, 0.5f, 0.85f};
    private static final int[] LAYER_SHIFT = {2, -2, 0};
    private static final float[] LAYER_SCALE = {0.92f, 0.78f, 0.62f};

    private final Path[] layers = {new Path(), new Path(), new Path()};
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean pathsValid;

    public WaveformSeekView(Context context) {
        this(context, null);
    }

    public WaveformSeekView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        fill.setStyle(Paint.Style.FILL);
    }

    @Override protected boolean wantsFrames() {
        return waveform == null && super.wantsFrames();
    }

    @Override protected void onWaveformChanged() {
        pathsValid = false;
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        pathsValid = false;
    }

    @Override protected void drawTrack(Canvas canvas, float left, float right, float cy, float thumbX, long dt) {
        float[] wf = waveform;
        if (wf == null || wf.length < 2) {
            drawWave(canvas, left, right, cy, thumbX, dt);
            return;
        }
        float maxH = 16f * density;
        float base = cy + maxH / 2f;
        if (!pathsValid) {
            for (int i = 0; i < layers.length; i++) {
                buildEnvelope(layers[i], wf, LAYER_SHIFT[i], LAYER_SCALE[i], left, right, base, maxH * 2f);
            }
            pathsValid = true;
        }
        drawLayers(canvas, left, base - maxH * 2f, thumbX, base + 1f, activeColor);
        drawLayers(canvas, thumbX, base - maxH * 2f, right, base + 1f, inactiveColor);
        inactivePaint.setStrokeWidth(1f * density);
        canvas.drawLine(thumbX, base, right, base, inactivePaint);
        inactivePaint.setStrokeWidth(strokeWidth);
    }

    private void drawLayers(Canvas canvas, float l, float t, float r, float b, int color) {
        if (r <= l) return;
        int save = canvas.save();
        canvas.clipRect(l, t, r, b);
        for (int i = 0; i < layers.length; i++) {
            fill.setColor(color);
            fill.setAlpha(Math.round(255 * LAYER_ALPHA[i] * (color >>> 24) / 255f));
            canvas.drawPath(layers[i], fill);
        }
        canvas.restoreToCount(save);
    }

    /** Smooth closed envelope through bucket midpoints (quadratic segments). */
    static void buildEnvelope(Path p, float[] wf, int shift, float scale,
                              float left, float right, float base, float maxH) {
        p.rewind();
        int n = wf.length;
        float step = (right - left) / (n - 1);
        p.moveTo(left, base);
        float px = left, py = base - sample(wf, shift) * scale * maxH;
        p.lineTo(px, py);
        for (int i = 1; i < n; i++) {
            float x = left + i * step;
            float y = base - sample(wf, i + shift) * scale * maxH;
            p.quadTo(px, py, (px + x) / 2f, (py + y) / 2f);
            px = x;
            py = y;
        }
        p.lineTo(right, py);
        p.lineTo(right, base);
        p.close();
    }

    private static float sample(float[] wf, int i) {
        int j = Math.max(0, Math.min(wf.length - 1, i));
        float v = wf[j];
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
