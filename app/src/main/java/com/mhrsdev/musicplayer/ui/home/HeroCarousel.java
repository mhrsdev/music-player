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
            // The peeking neighbour sits in the padding zone (clipToPadding is off), which the layout does not fill by
            // itself. Reserve exactly that zone: a whole extra screen on each side (the old value) bound and decoded a
            // full-size cover for 2-3 cards nobody can see, on every hero scroll.
            @Override protected void calculateExtraLayoutSpace(@NonNull RecyclerView.State state, @NonNull int[] extra) {
                int zone = Math.max(getPaddingStart(), getPaddingEnd()) + Math.round(GAP_DP * d);
                extra[0] = zone; // layout-direction terms: RTL swaps the sides by itself
                extra[1] = zone;
            }
        });
        new StartSnap().attachToRecyclerView(rv);
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView r, int dx, int dy) {
                apply(r);
            }
        });
        rv.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int w = r - l;
            int full = cardWidth(w, d);
            // Let the last card reach the focused keyline.
            int end = Math.max(start, w - start - full);
            if (rv.getPaddingEnd() != end) {
                rv.setPaddingRelative(start, rv.getPaddingTop(), end, rv.getPaddingBottom());
            }
            // One pending pass however many layouts happen before the next frame (no lambda per layout).
            Runnable pending = (Runnable) rv.getTag(R.id.home_hero_mask);
            if (pending == null) {
                pending = () -> apply(rv);
                rv.setTag(R.id.home_hero_mask, pending);
            }
            rv.removeCallbacks(pending);
            rv.post(pending);
        });
    }

    /**
     * Neighbours are whole cards (same aspect, own rounded corners) shown slightly smaller and cut by the screen
     * edge, never squeezed into a thin bar. The scale pivots on the edge facing the focused card so the gap stays.
     */
    static void apply(RecyclerView rv) {
        float d = rv.getResources().getDisplayMetrics().density;
        boolean rtl = rv.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        float gap = GAP_DP * d;
        int keyline = rtl ? rv.getWidth() - rv.getPaddingStart() : rv.getPaddingStart();
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
            // Child lookups are cached on the card: apply() runs on every scroll frame for every attached card.
            Parts parts = c.getTag(R.id.home_hero_mask) instanceof Parts t ? t : null;
            if (parts == null) {
                parts = new Parts(c.findViewById(R.id.art_frame), c.findViewById(R.id.title), c.findViewById(R.id.subtitle));
                c.setTag(R.id.home_hero_mask, parts);
            }
            float pivotX = pivotLeft ? 0f : full;
            float pivotY = parts.art == null ? c.getHeight() / 2f : parts.art.getTop() + parts.art.getHeight() / 2f;
            if (c.getPivotX() != pivotX) c.setPivotX(pivotX);
            if (c.getPivotY() != pivotY) c.setPivotY(pivotY);
            if (c.getScaleX() != s) {
                c.setScaleX(s);
                c.setScaleY(s);
            }
            float textAlpha = Math.max(0f, 1f - Math.abs(o) * 2.5f);
            if (parts.title != null && parts.title.getAlpha() != textAlpha) parts.title.setAlpha(textAlpha);
            if (parts.sub != null && parts.sub.getAlpha() != textAlpha) parts.sub.setAlpha(textAlpha);
            int important = textAlpha > 0.5f || Math.abs(o) < 1.5f
                    ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS;
            if (parts.important != important) {
                parts.important = important;
                c.setImportantForAccessibility(important);
            }
        }
    }

    /** The views of a hero card that {@link #apply} touches. */
    private static class Parts {
        final View art, title, sub;
        int important = -1; // last accessibility mode set (getImportantForAccessibility() resolves AUTO, so it can't be compared)

        Parts(@Nullable View art, @Nullable View title, @Nullable View sub) {
            this.art = art;
            this.title = title;
            this.sub = sub;
        }
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
