package com.mhrsdev.musicplayer.ui.widget;

import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.mhrsdev.musicplayer.R;

import java.util.ArrayList;

/**
 * Holds back a list's async cover work (decode or generated-cover render) while the list flings fast (SC1, D39): a
 * row that crosses the screen in a few frames would only start a worker render whose result nobody sees, and every
 * render competes with the scroll for CPU, memory bandwidth and heat. Cached covers still show at once. The held
 * rows load the moment the fling ends or slows to a readable speed. One listener per RecyclerView, kept as a tag.
 */
class FlingGate extends RecyclerView.OnScrollListener {
    /** Below this many dp per frame the fling's tail is readable: covers load again. */
    static final float SLOW_DP_PER_FRAME = 8f;

    private final ArrayList<ArtworkView> held = new ArrayList<>();
    private final int slowPx;
    private boolean fast;

    private FlingGate(RecyclerView rv) {
        slowPx = Math.round(SLOW_DP_PER_FRAME * rv.getResources().getDisplayMetrics().density);
        fast = rv.getScrollState() == RecyclerView.SCROLL_STATE_SETTLING;
    }

    /** The gate of the nearest fast-flinging list around {@code v} (nested carousels: any level), else null. */
    @Nullable
    static FlingGate flinging(View v) {
        for (ViewParent p = v.getParent(); p instanceof View; p = p.getParent()) {
            if (p instanceof RecyclerView rv) {
                FlingGate g = of(rv);
                if (g.fast) return g;
            }
        }
        return null;
    }

    private static FlingGate of(RecyclerView rv) {
        Object tag = rv.getTag(R.id.artwork_fling_gate);
        if (tag instanceof FlingGate g) return g;
        FlingGate g = new FlingGate(rv);
        rv.setTag(R.id.artwork_fling_gate, g);
        rv.addOnScrollListener(g);
        return g;
    }

    void hold(ArtworkView v) {
        if (!held.contains(v)) held.add(v);
    }

    @Override public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
        boolean f = state == RecyclerView.SCROLL_STATE_SETTLING;
        if (!f) release();
        fast = f;
    }

    @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
        if (fast && Math.abs(dx) + Math.abs(dy) < slowPx) {
            fast = false;
            release();
        }
    }

    private void release() {
        if (held.isEmpty()) return;
        ArtworkView[] views = held.toArray(new ArtworkView[0]);
        held.clear();
        for (ArtworkView v : views) v.resumeLoad();
    }
}
