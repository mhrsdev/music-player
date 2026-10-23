package com.mhrsdev.musicplayer.ui.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

import com.mhrsdev.musicplayer.ui.motion.Spring;

/**
 * Thread &amp; beads (D25): a fine thread strung with small beads at minute marks (15 s on short songs, 5 min on long
 * mixes); played beads are filled, the rest are pale. The thumb is a larger bead that grows while dragging and, on
 * release, wobbles once on Mowj's JIGGLE spring (frames only while the spring moves; static otherwise).
 */
public class BeadsSeekView extends FrameSeekView {
    private final Paint thread = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bead = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Spring wobble = new Spring(Spring.Preset.JIGGLE).setThreshold(0.002f);
    private int pearl;
    private int pale;

    public BeadsSeekView(Context context) {
        this(context, null);
    }

    public BeadsSeekView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        thread.setStyle(Paint.Style.STROKE);
        thread.setStrokeCap(Paint.Cap.ROUND);
        bead.setStyle(Paint.Style.FILL);
        halo.setStyle(Paint.Style.FILL);
        wobble.snapTo(1f);
    }

    @Override protected boolean needsFrames() {
        return !wobble.isSettled();
    }

    /** The release wobble lasts ~0.5 s: draw it at display rate so it does not judder. */
    @Override protected float frameInterval() {
        return 0f;
    }

    @Override protected boolean advance(float dt) {
        wobble.step(dt);
        return true;
    }

    @Override public void setColors(@ColorInt int active, @ColorInt int inactive) {
        super.setColors(active, inactive);
        pearl = SeekShapes.blend(active, 0xFFFFFFFF, 0.3f);
        int a = Math.min(255, Math.round((inactive >>> 24) * 1.6f));
        pale = (a << 24) | (inactive & 0x00FFFFFF);
    }

    @SuppressLint("ClickableViewAccessibility") // BaseSeekView.onTouchEvent performs the click
    @Override public boolean onTouchEvent(MotionEvent e) {
        boolean wasDragging = isDragging();
        boolean handled = super.onTouchEvent(e);
        if (wasDragging && !isDragging()) {
            // released: dip, rebound once, rest
            if (canAnimate()) {
                wobble.setTarget(1f);
                wobble.setVelocity(Spring.kickFor(0.22f, Spring.Preset.JIGGLE.stiffness, Spring.Preset.JIGGLE.dampingRatio));
            } else {
                wobble.snapTo(1f); // reduce motion / battery saver: no wobble
            }
            updateFrames();
        }
        return handled;
    }

    @Override protected void drawTrack(Canvas canvas, float left, float right, float cy, float thumbX, long dt) {
        if (thumbX > left) {
            thread.setColor(activeColor);
            thread.setStrokeWidth(2f * density);
            canvas.drawLine(left, cy, thumbX, cy, thread);
        }
        if (thumbX < right) {
            thread.setColor(inactiveColor);
            thread.setStrokeWidth(1.25f * density);
            canvas.drawLine(thumbX, cy, right, cy, thread);
        }
        long interval = SeekShapes.beadIntervalMs(duration);
        if (interval <= 0) return;
        float clear = 12f * density;
        float r = 2.6f * density;
        for (long t = interval; t < duration; t += interval) {
            float x = SeekMath.xFor(t, duration, left, right);
            if (Math.abs(x - thumbX) < clear) continue;
            boolean played = x <= thumbX;
            bead.setColor(played ? pearl : pale);
            canvas.drawCircle(x, cy, played ? r : r * 0.85f, bead);
        }
    }

    @Override protected void drawThumb(Canvas canvas, float thumbX, float cy, float grow) {
        halo.setColor(SeekShapes.withAlpha(activeColor, 0.22f));
        float s = wobble.value();
        float rr = (7f + 3.5f * grow) * density * s;
        canvas.drawCircle(thumbX, cy, rr + (4f + 3f * grow) * density, halo);
        bead.setColor(activeColor);
        canvas.drawCircle(thumbX, cy, rr, bead);
        // a small pearly highlight, top-left, makes it read as a bead and not a dot
        bead.setColor(pearl);
        canvas.drawCircle(thumbX - rr * 0.32f, cy - rr * 0.32f, rr * 0.3f, bead);
    }
}
