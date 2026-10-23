package com.mhrsdev.musicplayer.ui.home;

import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.OrientationHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.mhrsdev.musicplayer.R;

/**
 * Home hero carousel (DESIGN.md 01): one large focused card at the start keyline, narrow masked neighbours peeking
 * after it, snap to the start keyline. Pure layout math in the scroll callback (translation + a rounded outline
 * clip per visible child, no allocation per frame); RecyclerView keeps recycling normally. RTL mirrors.
 */
final class HeroCarousel {
    static final int GAP_DP = 8, PEEK_DP = 68, TAIL_DP = 40, START_DP = 16, RADIUS_DP = 28;

    private HeroCarousel() { }

    /** Focused card width for a carousel {@code parentPx} wide. */
    static int cardWidth(int parentPx, float density) {
        if (parentPx <= 0) return Math.round(272 * density);
        int w = parentPx - Math.round((START_DP + GAP_DP + PEEK_DP + GAP_DP + TAIL_DP) * density);
        return Math.max(Math.round(200 * density), w);
    }

    /**
     * Visible width of a card {@code o} slots from the focused keyline (0 = focused, 1 = first peek, ...),
     * interpolated between keylines. Pure for tests.
     */
    static float visibleWidth(float o, float full, float peek, float tail) {
        float a = Math.abs(o);
        if (a <= 1f) return full + (peek - full) * a;
        if (a <= 2f) return peek + (tail - peek) * (a - 1f);
        return tail;
    }

    /** Start-edge position (from the keyline) of a card {@code o} slots away. Pure for tests. */
    static float keylineStart(float o, float full, float peek, float tail, float gap) {
        if (o >= 0) {
            if (o <= 1f) return (full + gap) * o;
            if (o <= 2f) return full + gap + (peek + gap) * (o - 1f);
            return full + gap + peek + gap + (tail + gap) * (o - 2f);
        }
        // Cards before the focused one shrink to a peek and slide out past the keyline.
        if (o >= -1f) return (peek + gap) * o;
        return -(peek + gap) + (tail + gap) * (o + 1f);
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
        rv.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int w = r - l;
            int full = cardWidth(w, d);
            // Let the last card reach the focused keyline.
            int end = Math.max(start, w - start - full);
            if (rv.getPaddingEnd() != end) {
                rv.setPaddingRelative(start, rv.getPaddingTop(), end, rv.getPaddingBottom());
            }
            rv.post(() -> apply(rv));
        });
    }

    static void apply(RecyclerView rv) {
        float d = rv.getResources().getDisplayMetrics().density;
        boolean rtl = rv.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        float gap = GAP_DP * d, peek = PEEK_DP * d, tail = TAIL_DP * d;
        int keyline = rtl ? rv.getWidth() - rv.getPaddingStart() : rv.getPaddingStart();
        for (int i = 0; i < rv.getChildCount(); i++) {
            View c = rv.getChildAt(i);
            float full = c.getWidth();
            if (full <= 0) continue;
            float fromKey = rtl ? keyline - c.getRight() : c.getLeft() - keyline;
            float o = fromKey / (full + gap);
            float wantStart = keylineStart(o, full, peek, tail, gap);
            float vis = visibleWidth(o, full, peek, tail);
            // Cards after the keyline keep their leading part, cards before it keep their trailing part.
            float maskFrom = o >= 0 ? 0 : full - vis;
            float shift = wantStart - maskFrom - fromKey;
            c.setTranslationX(rtl ? -shift : shift);
            float left = rtl ? full - maskFrom - vis : maskFrom;
            setMask(c, left, vis, d, Math.max(0f, 1f - Math.abs(o) * 2.5f));
        }
    }

    private static void setMask(View c, float left, float width, float d, float textAlpha) {
        Mask m = c.getTag(R.id.home_hero_mask) instanceof Mask t ? t : null;
        if (m == null) {
            m = new Mask(RADIUS_DP * d);
            c.setTag(R.id.home_hero_mask, m);
            c.setOutlineProvider(m);
            c.setClipToOutline(true);
        }
        View art = c.findViewById(R.id.art_frame);
        int artH = art == null ? c.getHeight() : art.getBottom();
        int h = textAlpha > 0.01f ? c.getHeight() : artH;
        if (m.update(Math.round(left), Math.round(left + width), h)) {
            // Rect clip works in software rendering too; the outline adds the rounded corners on HW layers.
            m.rect.set(m.l, 0, m.r, h);
            c.setClipBounds(m.rect);
            c.invalidateOutline();
        }
        View title = c.findViewById(R.id.title), sub = c.findViewById(R.id.subtitle);
        if (title != null) title.setAlpha(textAlpha);
        if (sub != null) sub.setAlpha(textAlpha);
    }

    private static final class Mask extends ViewOutlineProvider {
        final float radius;
        final android.graphics.Rect rect = new android.graphics.Rect();
        int l, r, h;

        Mask(float radius) {
            this.radius = radius;
        }

        boolean update(int nl, int nr, int nh) {
            if (nl == l && nr == r && nh == h) return false;
            l = nl;
            r = nr;
            h = nh;
            return true;
        }

        @Override public void getOutline(View view, Outline outline) {
            outline.setRoundRect(l, 0, r, h <= 0 ? view.getHeight() : h, radius);
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
