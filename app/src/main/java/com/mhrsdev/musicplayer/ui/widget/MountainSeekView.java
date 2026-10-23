package com.mhrsdev.musicplayer.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

/**
 * Mountain (D25): one bold, smooth ridge of the real loudness envelope standing on a thin progress line. The played
 * side is filled with an accent gradient fading into the ground and edged with a bright crest line; the unplayed side
 * is a quiet silhouette. Ridge path and shaders are rebuilt only on size / waveform / colour change; drawing is two
 * clipped passes. Static. Before the envelope arrives it shows a low, calm placeholder hill.
 */
public class MountainSeekView extends BaseSeekView {
    static final int POINTS = 56;
    private final Path ridge = new Path();
    private final Path crest = new Path();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mute = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] heights = new float[POINTS];
    private final float maxH;
    private boolean pathValid;
    private boolean shadersValid;
    private float base;

    public MountainSeekView(Context context) {
        this(context, null);
    }

    public MountainSeekView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        maxH = 30f * density;
        fill.setStyle(Paint.Style.FILL);
        mute.setStyle(Paint.Style.FILL);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(1.75f * density);
        edge.setStrokeJoin(Paint.Join.ROUND);
        edge.setStrokeCap(Paint.Cap.ROUND);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeWidth(2f * density);
    }

    @Override public void setColors(@ColorInt int active, @ColorInt int inactive) {
        super.setColors(active, inactive);
        shadersValid = false;
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        pathValid = false;
        shadersValid = false;
    }

    @Override protected void onWaveformChanged() {
        pathValid = false;
    }

    /** Ridge heights 0..1 at {@link #POINTS} points: peak-pooled, then a 5-tap smooth so it reads as one ridge. */
    static void ridgeHeights(@Nullable float[] wf, float[] out) {
        int n = out.length;
        float[] tmp = out; // first pass in place, second pass via neighbours
        for (int i = 0; i < n; i++) tmp[i] = SeekShapes.peakIn(wf, i / (float) n, (i + 1) / (float) n);
        float p2 = tmp[0], p1 = tmp[0];
        for (int i = 0; i < n; i++) {
            float c = tmp[i];
            float n1 = tmp[Math.min(n - 1, i + 1)], n2 = tmp[Math.min(n - 1, i + 2)];
            float v = (p2 + 2 * p1 + 3 * c + 2 * n1 + n2) / 9f;
            p2 = p1;
            p1 = c;
            out[i] = wf == null ? 0.25f + 0.5f * (v - 0.25f) : v;
        }
    }

    private void buildPath(float left, float right) {
        ridgeHeights(waveform, heights);
        ridge.rewind();
        crest.rewind();
        float step = (right - left) / (POINTS - 1);
        float px = left, py = yAt(0);
        ridge.moveTo(left, base);
        ridge.lineTo(px, py);
        crest.moveTo(px, py);
        for (int i = 1; i < POINTS; i++) {
            float x = left + i * step, y = yAt(i);
            float mx = (px + x) / 2f, my = (py + y) / 2f;
            ridge.quadTo(px, py, mx, my);
            crest.quadTo(px, py, mx, my);
            px = x;
            py = y;
        }
        ridge.lineTo(right, py);
        crest.lineTo(right, py);
        ridge.lineTo(right, base);
        ridge.close();
        pathValid = true;
    }

    private float yAt(int i) {
        return base - (2f * density + heights[i] * (maxH - 2f * density));
    }

    /** Ridge top at {@code x} (for the thumb), linear between points. */
    private float ridgeY(float x, float left, float right) {
        float f = (x - left) / Math.max(1f, right - left) * (POINTS - 1);
        int i = Math.max(0, Math.min(POINTS - 2, (int) f));
        float t = Math.max(0f, Math.min(1f, f - i));
        return yAt(i) + (yAt(i + 1) - yAt(i)) * t;
    }

    @Override protected void drawTrack(Canvas canvas, float left, float right, float cy, float thumbX, long dt) {
        float newBase = cy + maxH / 2f - 1f * density;
        if (newBase != base) {
            base = newBase;
            pathValid = false;
            shadersValid = false;
        }
        if (!pathValid) buildPath(left, right);
        if (!shadersValid) {
            fill.setShader(new LinearGradient(0f, base - maxH, 0f, base,
                    SeekShapes.withAlpha(activeColor, 0.9f), SeekShapes.withAlpha(activeColor, 0.08f),
                    Shader.TileMode.CLAMP));
            mute.setColor(inactiveColor);
            edge.setColor(SeekShapes.blend(activeColor, 0xFFFFFFFF, 0.4f));
            shadersValid = true;
        }
        float top = base - maxH - 2f * density;
        if (thumbX > left) {
            int s = canvas.save();
            canvas.clipRect(left - 2f * density, top, thumbX, base + 2f * density);
            canvas.drawPath(ridge, fill);
            canvas.drawPath(crest, edge);
            canvas.restoreToCount(s);
        }
        if (thumbX < right) {
            int s = canvas.save();
            canvas.clipRect(thumbX, top, right + 2f * density, base + 2f * density);
            canvas.drawPath(ridge, mute);
            canvas.restoreToCount(s);
        }
        line.setColor(inactiveColor);
        canvas.drawLine(Math.max(left, thumbX), base, right, base, line);
        if (thumbX > left) {
            line.setColor(activeColor);
            canvas.drawLine(left, base, thumbX, base, line);
        }
    }

    /** A thin mast from the ground to the ridge with a dot on the summit (it grows while dragging). */
    @Override protected void drawThumb(Canvas canvas, float thumbX, float cy, float grow) {
        if (!pathValid) return;
        float y = ridgeY(thumbX, trackLeft(), trackRight()) - (2f + 4f * grow) * density;
        float w = (1f + 0.5f * grow) * density;
        canvas.drawRoundRect(thumbX - w, y, thumbX + w, base + 3f * density, w, w, thumbPaint);
        canvas.drawCircle(thumbX, y, (4f + 2f * grow) * density, thumbPaint);
    }
}
