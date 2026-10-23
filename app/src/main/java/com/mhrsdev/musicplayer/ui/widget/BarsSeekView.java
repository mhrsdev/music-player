package com.mhrsdev.musicplayer.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

/**
 * Bars seek bar: rounded vertical bars (3 dp wide, 2 dp gap) sized by the waveform, active color left of the thumb.
 * Fully static, the cheapest style (good for battery saver). Without a waveform the bars form a gentle pattern.
 */
public class BarsSeekView extends BaseSeekView {
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[] heights = new float[0];
    private float barW;
    private float barGap;

    public BarsSeekView(Context context) {
        this(context, null);
    }

    public BarsSeekView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        bar.setStyle(Paint.Style.FILL);
        barW = 3f * density;
        barGap = 2f * density;
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
        int count = span <= 0 ? 0 : (int) ((span + barGap) / (barW + barGap));
        heights = resample(waveform, count);
    }

    /** Resamples (max-pooling) {@code wf} to {@code count} bars in [0.12, 1]; a soft pattern when wf is null. */
    static float[] resample(@Nullable float[] wf, int count) {
        float[] out = new float[Math.max(0, count)];
        for (int i = 0; i < out.length; i++) {
            float v;
            if (wf == null || wf.length == 0) {
                v = 0.3f + 0.15f * (float) Math.sin(i * 0.55f) + 0.1f * (float) Math.sin(i * 1.7f);
            } else {
                int a = (int) ((long) i * wf.length / out.length);
                int b = Math.max(a + 1, (int) ((long) (i + 1) * wf.length / out.length));
                v = 0f;
                for (int j = a; j < b && j < wf.length; j++) v = Math.max(v, wf[j]);
            }
            out[i] = Math.max(0.12f, Math.min(1f, v));
        }
        return out;
    }

    @Override protected void drawTrack(Canvas canvas, float left, float right, float cy, float thumbX, long dt) {
        float maxH = 28f * density;
        float r = barW / 2f;
        for (int i = 0; i < heights.length; i++) {
            float x = left + i * (barW + barGap);
            float h = heights[i] * maxH / 2f;
            bar.setColor(x + barW / 2f <= thumbX ? activeColor : inactiveColor);
            canvas.drawRoundRect(x, cy - h, x + barW, cy + h, r, r, bar);
        }
    }
}
