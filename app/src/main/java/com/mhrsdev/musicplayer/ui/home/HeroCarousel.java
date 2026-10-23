package com.mhrsdev.musicplayer.ui.home;

import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.OrientationHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.mhrsdev.musicplayer.R;

/**
 * Home hero carousel (DESIGN.md 01): one large focused card at the start keyline, the next card peeking after it
 * (slightly smaller, cut by the screen edge), snap to the start keyline. Only scale/alpha in the scroll callback
 * (no allocation per frame); RecyclerView keeps recycling normally. RTL mirrors.
 */
final class HeroCarousel {
    static final int GAP_DP = 8, PEEK_DP = 68, TAIL_DP = 40, START_DP = 16;
    static final float NEIGHBOUR_SCALE = 0.86f;

    private HeroCarousel() { }

    /** Focused card width for a carousel {@code parentPx} wide. */
    static int cardWidth(int parentPx, float density) {
        if (parentPx <= 0) return Math.round(272 * density);
        int w = parentPx - Math.round((START_DP + GAP_DP + PEEK_DP + GAP_DP + TAIL_DP) * density);
        return Math.max(Math.round(200 * density), w);
    }

    /** Scale of a card {@code o} slots from the focused keyline: 1 when focused, NEIGHBOUR_SCALE one slot away. */
    static float scaleFor(float o) {
        return 1f - (1f - NEIGHBOUR_SCALE) * Math.min(1f, Math.abs(o));
    }

    static void attach(RecyclerView rv) {
        float d = rv.getResources().getDisplayMetrics().density;
        int start = Math.round(START_DP * d);
        rv.setPaddingRelative(start, rv.getPaddingTop(), start, rv.getPaddingBottom());
        rv.setClipToPadding(false);
        rv.setLayoutManager(new LinearLayoutManager(rv.getContext(), RecyclerView.HORIZONTAL, false) {
            // Lay out one extra screen at the end so the narrow tail cards (translated in) exist.
            @Override protected void calculateExtraLayoutSpace(@NonNull RecyclerView.State state, @NonNull int[] extra) {
                extra[0] = getWidth(); // RTL lays the tail out on the "before" side
                extra[1] = getWidth();
            }
        });
        new StartSnap().attachToRecyclerView(rv);
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView r, int dx, int dy) {
                apply(r);
            }
        });
        // Symmetric padding: the last card ends at the same margin the first starts at (no blank page at the end).
        rv.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> rv.post(() -> apply(rv)));
    }

    /**
     * Neighbours are whole cards (same aspect, own rounded corners) shown slightly smaller and cut by the screen
     * edge, never squeezed into a thin bar. The scale pivots on the edge facing the focused card so the gap stays.
     */
    static void apply(RecyclerView rv) {
        float d = rv.getResources().getDisplayMetrics().density;
        boolean rtl = rv.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        float gap = GAP_DP * d;
        float keyline = rtl ? rv.getWidth() - rv.getPaddingStart() : rv.getPaddingStart();
        // Near the end the focus keyline slides toward the end edge, so the last card (which cannot reach the start
        // keyline) is shown focused, full size, ending at the end margin, with its neighbour peeking before it.
        float shift = endShift(rv, rtl);
        keyline += rtl ? -shift : shift;
        for (int i = 0; i < rv.getChildCount(); i++) {
            View c = rv.getChildAt(i);
            float full = c.getWidth();
            if (full <= 0) continue;
            float fromKey = rtl ? keyline - c.getRight() : c.getLeft() - keyline;
            float o = fromKey / (full + gap);
            float s = scaleFor(o);
            boolean after = o >= 0;
            // Edge facing the focused card: the start edge for cards after it, the end edge for cards before it.
            boolean pivotLeft = after != rtl;
            View art = c.findViewById(R.id.art_frame);
            c.setPivotX(pivotLeft ? 0f : full);
            c.setPivotY(art == null ? c.getHeight() / 2f : art.getTop() + art.getHeight() / 2f);
            c.setScaleX(s);
            c.setScaleY(s);
            float textAlpha = Math.max(0f, 1f - Math.abs(o) * 2.5f);
            View title = c.findViewById(R.id.title), sub = c.findViewById(R.id.subtitle);
            if (title != null) title.setAlpha(textAlpha);
            if (sub != null) sub.setAlpha(textAlpha);
            c.setImportantForAccessibility(textAlpha > 0.5f || Math.abs(o) < 1.5f
                    ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
    }

    /** 0 until the last card is closer than one "peek area" to its final position, then up to that area. */
    static float endShift(RecyclerView rv, boolean rtl) {
        RecyclerView.Adapter<?> a = rv.getAdapter();
        if (a == null || a.getItemCount() < 2) return 0;
        int w = rv.getWidth();
        int full = cardWidth(w, rv.getResources().getDisplayMetrics().density);
        float max = Math.max(0, w - rv.getPaddingStart() - rv.getPaddingEnd() - full);
        RecyclerView.ViewHolder last = rv.findViewHolderForAdapterPosition(a.getItemCount() - 1);
        if (last == null || max == 0) return 0;
        View v = last.itemView;
        float overshoot = rtl ? rv.getPaddingEnd() - v.getLeft() : v.getRight() - (w - rv.getPaddingEnd());
        return max - Math.max(0f, Math.min(max, overshoot));
    }

    /** Snaps the nearest card's start edge to the start padding (LTR and RTL). */
    private static final class StartSnap extends LinearSnapHelper {
        @Nullable private OrientationHelper helper;

        private OrientationHelper helper(RecyclerView.LayoutManager lm) {
            if (helper == null || helper.getLayoutManager() != lm) helper = OrientationHelper.createHorizontalHelper(lm);
            return helper;
        }

        @Override public int[] calculateDistanceToFinalSnap(@NonNull RecyclerView.LayoutManager lm, @NonNull View target) {
            return new int[] {distance(lm, target), 0};
        }

        private int distance(RecyclerView.LayoutManager lm, View v) {
            boolean rtl = lm.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            if (rtl) return lm.getDecoratedRight(v) - (lm.getWidth() - lm.getPaddingStart());
            return lm.getDecoratedLeft(v) - lm.getPaddingStart();
        }

        @Nullable @Override public View findSnapView(RecyclerView.LayoutManager lm) {
            if (!(lm instanceof LinearLayoutManager)) return super.findSnapView(lm);
            helper(lm);
            View best = null;
            int bestD = Integer.MAX_VALUE;
            for (int i = 0; i < lm.getChildCount(); i++) {
                View c = lm.getChildAt(i);
                int dist = Math.abs(distance(lm, c));
                if (dist < bestD) {
                    bestD = dist;
                    best = c;
                }
            }
            return best;
        }
    }
}
