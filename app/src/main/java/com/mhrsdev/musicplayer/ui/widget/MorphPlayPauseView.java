package com.mhrsdev.musicplayer.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.ui.motion.Motion;
import com.mhrsdev.musicplayer.ui.motion.ShapeMorph;
import com.mhrsdev.musicplayer.ui.motion.Spring;
import com.mhrsdev.musicplayer.ui.motion.SpringAnimator;
import com.mhrsdev.musicplayer.ui.theme.Tokens;

/**
 * Mowj play/pause button. PLAY (paused) is the soft squircle with the play triangle (the look the owner liked).
 * PAUSE (playing) follows {@link #PAUSE_STYLE} (owner picks; docs/screens/m1-pause-options.png): the container
 * spring-morphs through {@link ShapeMorph} and the triangle's two halves morph point by point into the two bars
 * (no crossfade, no clipping). Every state change plays the one Mowj jiggle (1 -> 0.92 -> ~1.03 -> 1) and a press
 * squishes; both are drawn inside a 4% headroom so the silhouette never touches the bounds. Media icons never
 * mirror in RTL.
 *
 * <p>Battery: frames run only while a spring moves (shared {@code FrameDriver}); nothing ticks at rest. Style C
 * breathes while playing at 30 fps, only when visible with motion allowed. Reduce motion / battery saver: instant
 * state change, no jiggle, no breathing.
 */
public class MorphPlayPauseView extends View {
    /** Pause-state options shown to the owner. */
    public enum PauseStyle {
        /** A: same squircle, shorter bars with fully round caps. */
        SQUIRCLE_ROUND_BARS,
        /** B: the squircle melts into a circle, centred bars (recommended). */
        CIRCLE,
        /** C: squircle, two soft blocks that breathe while playing. */
        BREATHING_BLOCKS,
        /** D: the squircle firms into a rounded square, wider bars in a "held" stance. */
        ROUNDED_SQUARE
    }

    /** The one switch: which pause look ships. */
    public static final PauseStyle PAUSE_STYLE = PauseStyle.CIRCLE;

    /** Scale headroom kept free for the jiggle rebound (max drawn scale). */
    static final float HEADROOM = 1.04f;
    private static final long BREATH_PERIOD_MS = 2400L;
    private static final long BREATH_FRAME_MS = 33L;

    private final Paint containerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final Path icon = new Path();
    private final RectF arcRect = new RectF();
    // Icon outlines: two quads (8 points) for play and for pause, in units of u around the centre.
    private final float[] playPts = new float[16];
    private final float[] pausePts = new float[16];
    private final float[] cur = new float[16];
    private final long origin = SystemClock.uptimeMillis();
    private final SpringAnimator morphSpring;
    private final SpringAnimator scaleSpring;

    private PauseStyle style = PAUSE_STYLE;
    private ShapeMorph morphShape;
    private float morph; // 0 play/squircle .. 1 pause shape
    private float scale = 1f; // jiggle / press, drawn inside the headroom
    private boolean playing;
    private boolean bound;
    private boolean buffering;
    private boolean visibleToUser;
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
        morphSpring = new SpringAnimator(Spring.Preset.SNAPPY, v -> { morph = v; invalidate(); }, this);
        scaleSpring = new SpringAnimator(Spring.Preset.JIGGLE, v -> { scale = v; invalidate(); }, this);
        scaleSpring.snapTo(1f);
        morphShape = new ShapeMorph(ShapeMorph.Shape.SQUIRCLE, pauseShape(style));
        buildIcons();
        setColors(Tokens.primary(context), Tokens.color(context, R.attr.mpOnPrimary));
        updateDescription();
    }

    /** For the owner's proposal sheet and tests; the app ships {@link #PAUSE_STYLE}. */
    @VisibleForTesting
    public void setPauseStyle(PauseStyle s) {
        style = s;
        morphShape = new ShapeMorph(ShapeMorph.Shape.SQUIRCLE, pauseShape(s));
        buildIcons();
        invalidate();
    }

    private static ShapeMorph.Shape pauseShape(PauseStyle s) {
        switch (s) {
            case CIRCLE: return ShapeMorph.Shape.CIRCLE;
            case ROUNDED_SQUARE: return ShapeMorph.Shape.ROUNDED_SQUARE;
            default: return ShapeMorph.Shape.SQUIRCLE;
        }
    }

    /** Sets the state; {@code animate=false} jumps (use when binding a recycled view). */
    public void setPlaying(boolean playing, boolean animate) {
        if (!bound) { // first bind shows the final shape at once, never a morph from the default state
            bound = true;
            animate = false;
            this.playing = !playing;
        }
        if (this.playing == playing) {
            if (!animate && !morphSpring.isRunning()) morphSpring.snapTo(playing ? 1f : 0f);
            return;
        }
        this.playing = playing;
        updateDescription();
        float target = playing ? 1f : 0f;
        if (animate && isLaidOut()) {
            morphSpring.animateTo(target); // snaps by itself when motion is off
            Motion.jiggle(scaleSpring);
        } else {
            morphSpring.snapTo(target);
        }
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

    /** Test hook: freeze a mid-morph / mid-jiggle frame. */
    @VisibleForTesting
    void setFrameForTest(float morph, float scale) {
        morphSpring.snapTo(morph);
        scaleSpring.snapTo(scale);
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

    /** Radius of the container inside the padded square, 1.5 px in so anti-aliasing never touches the edge. */
    static float outerRadius(float w, float h, int pl, int pt, int pr, int pb) {
        float inner = Math.min(w - pl - pr, h - pt - pb);
        return Math.max(0f, inner / 2f - 1.5f);
    }

    /**
     * Play triangle (left -0.9u, tip +1.35u, half height 1.25u; optically centred) split at its middle into two
     * quads; pause = two bars. Point i of each quad maps to point i of the other, so the halves become the bars.
     */
    private void buildIcons() {
        float x0 = -0.9f, tip = 1.35f, hh = 1.25f, mid = (x0 + tip) / 2f;
        float yAtMid = hh * (tip - mid) / (tip - x0);
        float eps = 0.02f; // keeps the tip quad non-degenerate
        set(playPts, 0, x0, -hh, mid, -yAtMid, mid, yAtMid, x0, hh);
        set(playPts, 8, mid, -yAtMid, tip, -eps, tip, eps, mid, yAtMid);
        float bw, bh, gap;
        switch (style) {
            case SQUIRCLE_ROUND_BARS: bw = 0.7f; bh = 1.05f; gap = 0.4f; break;
            case BREATHING_BLOCKS: bw = 0.86f; bh = 1.1f; gap = 0.3f; break;
            case ROUNDED_SQUARE: bw = 0.78f; bh = 1.18f; gap = 0.36f; break;
            case CIRCLE:
            default: bw = 0.7f; bh = 1.2f; gap = 0.38f; break;
        }
        set(pausePts, 0, -gap - bw, -bh, -gap, -bh, -gap, bh, -gap - bw, bh);
        set(pausePts, 8, gap, -bh, gap + bw, -bh, gap + bw, bh, gap, bh);
    }

    private static void set(float[] a, int off, float x0, float y0, float x1, float y1, float x2, float y2,
                            float x3, float y3) {
        a[off] = x0; a[off + 1] = y0; a[off + 2] = x1; a[off + 3] = y1;
        a[off + 4] = x2; a[off + 5] = y2; a[off + 6] = x3; a[off + 7] = y3;
    }

    /** Bar corner radius in u per style (the play triangle uses 0.14u, its seam stays sharp). */
    private float barCorner() {
        switch (style) {
            case SQUIRCLE_ROUND_BARS: return 0.33f;
            case BREATHING_BLOCKS: return 0.3f;
            case ROUNDED_SQUARE: return 0.2f;
            default: return 0.3f;
        }
    }

    @Override public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        visibleToUser = isVisible;
        if (isVisible) invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        morphSpring.snapTo(playing ? 1f : 0f);
        scaleSpring.snapTo(1f);
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float cx = getPaddingLeft() + (w - getPaddingLeft() - getPaddingRight()) / 2f;
        float cy = getPaddingTop() + (h - getPaddingTop() - getPaddingBottom()) / 2f;
        float s = Math.min(HEADROOM, Math.max(0.5f, scale));
        float r = outerRadius(w, h, getPaddingLeft(), getPaddingTop(), getPaddingRight(), getPaddingBottom())
                / HEADROOM * s;
        float m = Math.max(0f, Math.min(1f, morph));
        morphShape.toPath(shape, m, cx, cy, r, r);
        containerPaint.setColor(isEnabled() ? containerColor : 0x1FFFFFFF & containerColor | 0x1F000000);
        canvas.drawPath(shape, containerPaint);

        int alpha = isEnabled() ? 255 : 97;
        float u = r * 0.235f;
        boolean motion = visibleToUser && Motion.enabled(getContext());
        if (buffering) {
            arcPaint.setColor(iconColor);
            arcPaint.setAlpha(alpha);
            arcPaint.setStrokeWidth(r * 0.09f);
            arcRect.set(cx - u * 1.3f, cy - u * 1.3f, cx + u * 1.3f, cy + u * 1.3f);
            float start = motion ? (SystemClock.uptimeMillis() - origin) % 1000L * 0.36f : 0f;
            canvas.drawArc(arcRect, start, 270f, false, arcPaint);
            if (motion) postInvalidateOnAnimation();
            return;
        }
        float breathe = 1f;
        boolean breathing = style == PauseStyle.BREATHING_BLOCKS && playing && m >= 1f && motion && isEnabled();
        if (breathing) {
            double ph = 2 * Math.PI * ((SystemClock.uptimeMillis() - origin) % BREATH_PERIOD_MS) / BREATH_PERIOD_MS;
            breathe = 1f - 0.06f * (float) (0.5 - 0.5 * Math.cos(ph));
        }
        for (int i = 0; i < 16; i++) cur[i] = playPts[i] + (pausePts[i] - playPts[i]) * m;
        icon.rewind();
        float corner = (barCorner() * m + 0.14f * (1f - m)) * u;
        addQuad(icon, cur, 0, cx, cy, u, u * breathe, corner, m, true);
        addQuad(icon, cur, 8, cx, cy, u, u * breathe, corner, m, false);
        iconPaint.setColor(iconColor);
        iconPaint.setAlpha(alpha);
        canvas.drawPath(icon, iconPaint);
        if (breathing) postInvalidateDelayed(BREATH_FRAME_MS);
    }

    /**
     * Adds a rounded quad. Seam corners (where the triangle halves meet) round only as the morph progresses, so the
     * play triangle stays one clean shape. No allocation: corners are cut and joined with quadTo.
     */
    private static void addQuad(Path p, float[] pts, int off, float cx, float cy, float ux, float uy, float radius,
                                float m, boolean leftHalf) {
        for (int k = 0; k <= 4; k++) {
            int i = k % 4;
            float px = cx + pts[off + 2 * i] * ux, py = cy + pts[off + 2 * i + 1] * uy;
            int pi = (i + 3) % 4, ni = (i + 1) % 4;
            float ax = cx + pts[off + 2 * pi] * ux, ay = cy + pts[off + 2 * pi + 1] * uy;
            float bx = cx + pts[off + 2 * ni] * ux, by = cy + pts[off + 2 * ni + 1] * uy;
            boolean seam = leftHalf ? (i == 1 || i == 2) : (i == 0 || i == 3);
            float rr = seam ? radius * m : radius;
            float la = (float) Math.hypot(ax - px, ay - py), lb = (float) Math.hypot(bx - px, by - py);
            rr = Math.min(rr, Math.min(la, lb) / 2f);
            float sx = la > 0 ? px + (ax - px) * rr / la : px, sy = la > 0 ? py + (ay - py) * rr / la : py;
            float ex = lb > 0 ? px + (bx - px) * rr / lb : px, ey = lb > 0 ? py + (by - py) * rr / lb : py;
            if (k == 0) {
                p.moveTo(ex, ey);
            } else {
                p.lineTo(sx, sy);
                p.quadTo(px, py, ex, ey);
            }
        }
        p.close();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (isEnabled()) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                scaleSpring.setPreset(Spring.Preset.SNAPPY).animateTo(Motion.PRESS_SCALE);
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                scaleSpring.setPreset(Spring.Preset.JIGGLE).animateTo(1f);
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
