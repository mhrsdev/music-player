package com.mhrsdev.musicplayer.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.ui.theme.MotionPolicy;
import com.mhrsdev.musicplayer.ui.theme.Tokens;

import java.util.ArrayList;
import java.util.List;

/**
 * Loading skeleton (DESIGN.md 13-states): static rounded blocks shaped like a section title, a hero card and a few
 * track rows. Instead of a spinner. When decorative motion is allowed, every visible skeleton shares one alpha
 * pulse ticked at 15 fps by a single main-thread handler; it stops as soon as no skeleton is visible.
 * Under reduce-motion / battery saver the frame is static (no ticks at all).
 */
public final class SkeletonView extends View {
    private static final long FRAME_MS = 66; // <= 15 fps
    private static final long PERIOD_MS = 1400;
    private static final List<SkeletonView> LIVE = new ArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Runnable TICK = new Runnable() {
        @Override public void run() {
            if (LIVE.isEmpty()) return;
            for (int i = 0; i < LIVE.size(); i++) LIVE.get(i).invalidate();
            MAIN.postDelayed(this, FRAME_MS);
        }
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private boolean registered;

    public SkeletonView(Context context) {
        this(context, null);
    }

    public SkeletonView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        d = getResources().getDisplayMetrics().density;
        paint.setColor(Tokens.color(context, R.attr.mpSurfaceContainerHigh));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    /** Pure pulse curve: alpha in [0.55, 1] for a time in ms (JVM-testable). */
    public static float pulse(long nowMs) {
        float t = (nowMs % PERIOD_MS) / (float) PERIOD_MS;
        float tri = t < 0.5f ? t * 2f : 2f - t * 2f;
        return 0.55f + 0.45f * tri;
    }

    @Override protected void onDraw(@NonNull Canvas c) {
        paint.setAlpha(Math.round(255 * (registered ? pulse(SystemClock.uptimeMillis()) : 0.8f)));
        float w = getWidth(), h = getHeight();
        float pad = 16 * d, y = 8 * d;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        // Section title.
        block(c, rtl, pad, y, w * 0.45f, 28 * d, 14 * d, w);
        y += 48 * d;
        // Hero card + peeking neighbour.
        float hero = Math.min(w * 0.66f, h * 0.45f);
        block(c, rtl, pad, y, hero, hero, 28 * d, w);
        block(c, rtl, pad + hero + 8 * d, y, 68 * d, hero, 28 * d, w);
        block(c, rtl, pad + hero + 84 * d, y, 68 * d, hero, 28 * d, w);
        y += hero + 16 * d;
        block(c, rtl, pad, y, w * 0.4f, 18 * d, 9 * d, w);
        y += 26 * d;
        block(c, rtl, pad, y, w * 0.6f, 14 * d, 7 * d, w);
        y += 40 * d;
        // Track rows.
        while (y + 64 * d < h) {
            block(c, rtl, pad, y, 56 * d, 56 * d, 12 * d, w);
            block(c, rtl, pad + 72 * d, y + 10 * d, w * 0.5f, 16 * d, 8 * d, w);
            block(c, rtl, pad + 72 * d, y + 34 * d, w * 0.3f, 12 * d, 6 * d, w);
            y += 72 * d;
        }
    }

    private void block(Canvas c, boolean rtl, float x, float y, float bw, float bh, float r, float w) {
        float l = rtl ? w - x - bw : x;
        if (l > w || l + bw < 0) return;
        c.drawRoundRect(l, y, l + bw, y + bh, r, r, paint);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        sync();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        unregister();
    }

    @Override public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        sync();
    }

    private void sync() {
        boolean want = isAttachedToWindow() && isShown() && MotionPolicy.decorativeMotionEnabled(getContext());
        if (want && !registered) {
            registered = true;
            LIVE.add(this);
            if (LIVE.size() == 1) MAIN.postDelayed(TICK, FRAME_MS);
        } else if (!want) {
            unregister();
        }
        invalidate();
    }

    private void unregister() {
        if (!registered) return;
        registered = false;
        LIVE.remove(this);
        if (LIVE.isEmpty()) MAIN.removeCallbacks(TICK);
    }
}
