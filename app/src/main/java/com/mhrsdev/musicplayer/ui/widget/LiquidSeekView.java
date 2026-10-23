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
 * Liquid (D25): a pill-shaped glass track filled by a liquid whose leading edge is a soft convex meniscus. While
 * playing (visible, motion allowed) the meniscus ripples gently at 30 fps through the shared FrameDriver, its
 * amplitude following the real loudness at the current position; paused it is perfectly still. The pill clip path
 * and the liquid gradient are built on size / colour change only; the edge path is rewound, never re-allocated.
 */
public class LiquidSeekView extends FrameSeekView {
    private static final int EDGE_POINTS = 12;
    private final Path pill = new Path();
    private final Path liquid = new Path();
    private final Path meniscus = new Path();
    private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cap = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubble = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float pillH;
    private boolean shapesValid;
    private float builtCy = -1f;

    public LiquidSeekView(Context context) {
        this(context, null);
    }

    public LiquidSeekView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        pillH = 18f * density;
        glass.setStyle(Paint.Style.FILL);
        fill.setStyle(Paint.Style.FILL);
        shine.setStyle(Paint.Style.STROKE);
        shine.setStrokeCap(Paint.Cap.ROUND);
        shine.setStrokeWidth(1.5f * density);
        cap.setStyle(Paint.Style.FILL);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2f * density);
        bubble.setStyle(Paint.Style.FILL);
    }

    @Override protected boolean needsFrames() {
        return playing;
    }

    @Override public void setColors(@ColorInt int active, @ColorInt int inactive) {
        super.setColors(active, inactive);
        shapesValid = false;
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        shapesValid = false;
    }

    private void buildShapes(float left, float right, float cy) {
        float r = pillH / 2f;
        pill.rewind();
        pill.addRoundRect(left, cy - r, right, cy + r, r, r, Path.Direction.CW);
        int bright = SeekShapes.blend(activeColor, 0xFFFFFFFF, 0.38f);
        int deep = SeekShapes.blend(activeColor, 0xFF000000, 0.12f);
        fill.setShader(new LinearGradient(0f, cy - r, 0f, cy + r, new int[] {bright, activeColor, deep},
                new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        glass.setColor(SeekShapes.withAlpha(inactiveColor, 0.75f));
        shine.setColor(SeekShapes.withAlpha(0xFFFFFFFF, 0.7f));
        cap.setColor(SeekShapes.blend(activeColor, 0xFFFFFFFF, 0.88f));
        ring.setColor(activeColor);
        bubble.setColor(SeekShapes.withAlpha(0xFFFFFFFF, 0.45f));
        builtCy = cy;
        shapesValid = true;
    }

    @Override protected void drawTrack(Canvas canvas, float left, float right, float cy, float thumbX, long dt) {
        if (!shapesValid || builtCy != cy) buildShapes(left, right, cy);
        float r = pillH / 2f;
        canvas.drawPath(pill, glass);
        if (thumbX <= left + 0.5f) return;
        boolean moving = playing && isAnimating();
        float loud = SeekShapes.loudnessAt(waveform, SeekMath.fraction(position, duration));
        // paused: the last ripple stays frozen (still reads as liquid, costs nothing)
        float ripple = (moving ? 0.8f + 1.8f * loud : 0.7f) * density;
        float bulge = 7f * density;
        liquid.rewind();
        meniscus.rewind();
        liquid.moveTo(left - r, cy - r);
        for (int i = 0; i <= EDGE_POINTS; i++) {
            float t = i / (float) EDGE_POINTS;           // 0 top .. 1 bottom
            float u = t * 2f - 1f;                       // -1 .. 1
            float x = thumbX + bulge * (1f - u * u) - bulge
                    + ripple * (float) Math.sin(clock * 6.2f + t * 6.0f) * (1f - 0.4f * u * u);
            float y = cy - r + t * pillH;
            liquid.lineTo(x, y);
            if (i == 0) meniscus.moveTo(x, y + 1.5f * density);
            else if (i < EDGE_POINTS) meniscus.lineTo(x, y);
        }
        liquid.lineTo(left - r, cy + r);
        liquid.close();
        int s = canvas.save();
        canvas.clipPath(pill);
        canvas.drawPath(liquid, fill);
        canvas.drawPath(meniscus, shine);
        // surface sheen along the top of the liquid
        shine.setAlpha(70);
        canvas.drawLine(left + r * 0.6f, cy - r * 0.45f, thumbX - r, cy - r * 0.45f, shine);
        shine.setAlpha(178);
        // three small bubbles suspended in the liquid
        float span = thumbX - left;
        if (span > 6f * r) {
            canvas.drawCircle(left + span * 0.28f, cy + r * 0.25f, 1.4f * density, bubble);
            canvas.drawCircle(left + span * 0.55f, cy - r * 0.05f, 1.0f * density, bubble);
            canvas.drawCircle(left + span * 0.78f, cy + r * 0.35f, 1.7f * density, bubble);
        }
        canvas.restoreToCount(s);
    }

    /**
     * A pearl drop at the tip of the meniscus: 10 dp (16 dp while dragging), small enough that the curved liquid
     * edge stays visible above and below it; ringed in the accent so it reads in both themes.
     */
    @Override protected void drawThumb(Canvas canvas, float thumbX, float cy, float grow) {
        float rr = (5f + 3f * grow) * density;
        float x = Math.max(trackLeft() + rr, thumbX);
        canvas.drawCircle(x, cy, rr, cap);
        canvas.drawCircle(x, cy, rr - ring.getStrokeWidth() / 2f, ring);
    }
}
