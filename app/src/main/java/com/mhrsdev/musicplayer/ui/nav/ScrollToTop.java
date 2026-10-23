package com.mhrsdev.musicplayer.ui.nav;

import android.view.View;
import android.view.ViewGroup;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/** Tab reselect: scrolls the screen's main (first vertical) list back to the top. */
public final class ScrollToTop {
    private ScrollToTop() { }

    /** True if a scrolling container was found under {@code root}. */
    public static boolean scroll(View root) {
        View target = find(root);
        if (target instanceof RecyclerView rv) {
            RecyclerView.LayoutManager lm = rv.getLayoutManager();
            // Far down a long list a smooth scroll takes seconds: jump near the top first, then glide.
            if (lm instanceof LinearLayoutManager llm && llm.findFirstVisibleItemPosition() > 12) rv.scrollToPosition(8);
            rv.smoothScrollToPosition(0);
            return true;
        }
        if (target instanceof NestedScrollView sv) {
            sv.smoothScrollTo(0, 0);
            return true;
        }
        return false;
    }

    private static View find(View v) {
        if (v.getVisibility() != View.VISIBLE) return null;
        if (v instanceof NestedScrollView) return v;
        if (v instanceof RecyclerView rv && rv.getLayoutManager() != null && rv.getLayoutManager().canScrollVertically()) {
            return v;
        }
        if (v instanceof ViewGroup g) {
            for (int i = 0; i < g.getChildCount(); i++) {
                View found = find(g.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
}
