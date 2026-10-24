package com.mhrsdev.musicplayer.ui.sound;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.ui.theme.Tokens;
import java.util.List;
import java.util.Locale;

/**
 * Equalizer drawn by hand: one vertical slider per band (frequency below, gain above), the smooth response curve
 * through the thumbs and a zero line. Always laid out left-to-right (low to high frequency), also in RTL.
 * Each band column is the touch target (and a virtual accessibility slider with a range and +/- 1 dB actions).
 * Idle cost: nothing is animated; it only redraws on input or new data.
 */
public class EqualizerView extends View {
    public interface Listener {
        void onBandChanged(int band, int levelMb);
    }

    private static final int STEP_MB = 50;
    private static final int A11Y_STEP_MB = 100;
    private static final int CURVE_SAMPLES = 64;

    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint zero = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint active = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curve = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbCore = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path curvePath = new Path();
    private final Path fillPath = new Path();
    private final float density;
    private final int primary;
    private final int tertiary;
    private final int outline;
    private final int outlineVariant;
    private final int onSurface;
    private final int onSurfaceVariant;
    private final EqualizerA11y a11y;

    private int[] centersHz = new int[0];
    private int[] levelsMb = new int[0];
    private int minMb = -1500;
    private int maxMb = 1500;
    private boolean on;
    private int dragging = -1;
    @Nullable private Listener listener;

    public EqualizerView(Context context) {
        this(context, null);
    }

    public EqualizerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        primary = Tokens.primary(context);
        tertiary = Tokens.color(context, R.attr.mpTertiary);
        outline = Tokens.color(context, R.attr.mpOutline);
        outlineVariant = Tokens.outlineVariant(context);
        onSurface = Tokens.onSurface(context);
        onSurfaceVariant = Tokens.onSurfaceVariant(context);
        setLayoutDirection(LAYOUT_DIRECTION_LTR);
        setMinimumHeight(Math.round(240 * density));

        grid.setColor(outlineVariant);
        grid.setStrokeWidth(density);
        zero.setColor(outline);
        zero.setStrokeWidth(1.5f * density);
        track.setColor(outlineVariant);
        track.setStrokeWidth(4 * density);
        track.setStrokeCap(Paint.Cap.ROUND);
        active.setStrokeWidth(4 * density);
        active.setStrokeCap(Paint.Cap.ROUND);
        curve.setStyle(Paint.Style.STROKE);
        curve.setStrokeWidth(2.5f * density);
        curve.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
        thumbCore.setColor(Tokens.color(context, R.attr.mpBackground));
        Typeface face = null;
        try {
            face = ResourcesCompat.getFont(context, R.font.mp_sans);
        } catch (RuntimeException ignored) {
            // falls back to the default typeface
        }
        for (Paint p : new Paint[] {label, value}) {
            p.setTextAlign(Paint.Align.CENTER);
            if (face != null) p.setTypeface(face);
        }
        label.setTextSize(Tokens.sp(context, 12f));
        label.setColor(onSurfaceVariant);
        value.setTextSize(Tokens.sp(context, 12f));
        a11y = new EqualizerA11y(this);
        ViewCompat.setAccessibilityDelegate(this, a11y);
        applyLook();
    }

    public void setListener(@Nullable Listener l) {
        listener = l;
    }

    /** Whether the equalizer is switched on; off = muted colours, still adjustable. */
    public void setActiveLook(boolean enabled) {
        if (on == enabled) return;
        on = enabled;
        applyLook();
        invalidate();
    }

    public void setBands(int[] centersHz, int[] levelsMb, int minMb, int maxMb) {
        this.centersHz = centersHz.clone();
        this.levelsMb = levelsMb.clone();
        this.minMb = minMb;
        this.maxMb = Math.max(maxMb, minMb + 1);
        invalidate();
        a11y.invalidateRoot();
    }

    private void applyLook() {
        int accent = on ? primary : outline;
        active.setColor(accent);
        thumb.setColor(accent);
        curve.setColor(on ? tertiary : outlineVariant);
        fill.setColor((on ? tertiary : outlineVariant) & 0x00FFFFFF | 0x2E000000);
        value.setColor(on ? onSurface : onSurfaceVariant);
    }

    // --- geometry -----------------------------------------------------------------------------

    private float labelBand() { return 26 * density; }
    private float thumbRadius() { return 11 * density; }
    private float top() { return getPaddingTop() + labelBand() + thumbRadius(); }
    private float bottom() { return getHeight() - getPaddingBottom() - labelBand() - thumbRadius(); }
    private float columnWidth() { return (getWidth() - getPaddingLeft() - getPaddingRight()) / (float) Math.max(1, levelsMb.length); }
    private float centerX(int band) { return getPaddingLeft() + (band + 0.5f) * columnWidth(); }

    private int bandAt(float x) {
        if (levelsMb.length == 0) return -1;
        int band = (int) ((x - getPaddingLeft()) / columnWidth());
        return Math.max(0, Math.min(levelsMb.length - 1, band));
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), widthSpec),
                resolveSize(getSuggestedMinimumHeight(), heightSpec));
    }

    // --- drawing ------------------------------------------------------------------------------

    private float[] xs = new float[0];
    private float[] ys = new float[0];

    /** The band count never changes after setup: allocated once, not per frame. */
    private void ensureBuffers(int n) {
        if (xs.length == n) return;
        xs = new float[n];
        ys = new float[n];
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        int n = levelsMb.length;
        if (n == 0) return;
        float top = top();
        float bottom = bottom();
        float left = getPaddingLeft();
        float right = getWidth() - getPaddingRight();
        float zeroY = EqCurve.yFor(0, minMb, maxMb, top, bottom);
        canvas.drawLine(left, top, right, top, grid);
        canvas.drawLine(left, bottom, right, bottom, grid);
        canvas.drawLine(left, zeroY, right, zeroY, zero);

        ensureBuffers(n);
        for (int i = 0; i < n; i++) {
            xs[i] = centerX(i);
            ys[i] = EqCurve.yFor(levelsMb[i], minMb, maxMb, top, bottom);
            canvas.drawLine(xs[i], top, xs[i], bottom, track);
            canvas.drawLine(xs[i], zeroY, xs[i], ys[i], active);
        }

        float[] sampled = EqCurve.sample(xs, ys, CURVE_SAMPLES);
        curvePath.reset();
        fillPath.reset();
        float x0 = xs[0];
        float x1 = xs[n - 1];
        fillPath.moveTo(x0, zeroY);
        for (int k = 0; k < sampled.length; k++) {
            float x = x0 + (x1 - x0) * k / (sampled.length - 1);
            if (k == 0) curvePath.moveTo(x, sampled[k]); else curvePath.lineTo(x, sampled[k]);
            fillPath.lineTo(x, sampled[k]);
        }
        fillPath.lineTo(x1, zeroY);
        fillPath.close();
        canvas.drawPath(fillPath, fill);
        canvas.drawPath(curvePath, curve);

        float r = thumbRadius();
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        for (int i = 0; i < n; i++) {
            boolean grabbed = i == dragging;
            canvas.drawCircle(xs[i], ys[i], grabbed ? r * 1.2f : r, thumb);
            canvas.drawCircle(xs[i], ys[i], (grabbed ? r * 1.2f : r) - 4 * density, thumbCore);
            canvas.drawText(EqCurve.formatDb(levelsMb[i], locale), xs[i], getPaddingTop() + labelBand() - 8 * density, value);
            canvas.drawText(EqCurve.formatHz(centersHz[i], locale), xs[i], getHeight() - getPaddingBottom() - 8 * density, label);
        }
    }

    // --- input --------------------------------------------------------------------------------

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (levelsMb.length == 0) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                dragging = bandAt(e.getX());
                move(e.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging < 0) return false;
                move(e.getY());
                return true;
            case MotionEvent.ACTION_UP:
                performClick();
                // fall through
            case MotionEvent.ACTION_CANCEL:
                dragging = -1;
                getParent().requestDisallowInterceptTouchEvent(false);
                invalidate();
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    @Override public boolean performClick() {
        return super.performClick();
    }

    private void move(float y) {
        setLevel(dragging, EqCurve.levelFor(y, minMb, maxMb, top(), bottom(), STEP_MB));
    }

    private void setLevel(int band, int levelMb) {
        if (band < 0 || band >= levelsMb.length || levelsMb[band] == levelMb) {
            invalidate();
            return;
        }
        levelsMb[band] = levelMb;
        invalidate();
        if (listener != null) listener.onBandChanged(band, levelMb);
    }

    @Override public boolean dispatchHoverEvent(MotionEvent event) {
        return a11y.dispatchHoverEvent(event) || super.dispatchHoverEvent(event);
    }

    // --- accessibility: each band is a virtual slider ---------------------------------------

    private class EqualizerA11y extends ExploreByTouchHelper {
        EqualizerA11y(View host) {
            super(host);
        }

        @Override protected int getVirtualViewAt(float x, float y) {
            return levelsMb.length == 0 ? INVALID_ID : bandAt(x);
        }

        @Override protected void getVisibleVirtualViews(List<Integer> ids) {
            for (int i = 0; i < levelsMb.length; i++) ids.add(i);
        }

        @Override protected void onPopulateNodeForVirtualView(int id, @NonNull AccessibilityNodeInfoCompat node) {
            Locale locale = getResources().getConfiguration().getLocales().get(0);
            node.setClassName("android.widget.SeekBar");
            node.setContentDescription(getContext().getString(R.string.sound_band_desc,
                    EqCurve.formatHz(centersHz[id], locale)));
            node.setStateDescription(getContext().getString(R.string.sound_band_state,
                    EqCurve.formatDb(levelsMb[id], locale)));
            node.setRangeInfo(AccessibilityNodeInfoCompat.RangeInfoCompat.obtain(
                    AccessibilityNodeInfoCompat.RangeInfoCompat.RANGE_TYPE_INT, minMb, maxMb, levelsMb[id]));
            float cw = columnWidth();
            int l = Math.round(getPaddingLeft() + id * cw);
            node.setBoundsInParent(new android.graphics.Rect(l, 0, Math.round(l + cw), getHeight()));
            node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_FORWARD);
            node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_BACKWARD);
        }

        @Override protected boolean onPerformActionForVirtualView(int id, int action, @Nullable Bundle args) {
            int step = action == AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD ? A11Y_STEP_MB
                    : action == AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD ? -A11Y_STEP_MB : 0;
            if (step == 0) return false;
            setLevel(id, Math.max(minMb, Math.min(maxMb, levelsMb[id] + step)));
            invalidateVirtualView(id);
            sendEventForVirtualView(id, android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SELECTED);
            return true;
        }
    }
}
