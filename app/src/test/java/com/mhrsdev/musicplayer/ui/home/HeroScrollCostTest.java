package com.mhrsdev.musicplayer.ui.home;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.mhrsdev.musicplayer.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Perf guard for the Home hero carousel: how many cards are bound for a screen, and that scrolling it costs no layout
 * request (apply() only sets scale / alpha, and only when a value changed).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w411dp-h800dp-xxhdpi")
public class HeroScrollCostTest {
    static class CountingRv extends RecyclerView {
        int layoutRequests;

        CountingRv(Context c) {
            super(c);
        }

        @Override public void requestLayout() {
            layoutRequests++;
            super.requestLayout();
        }
    }

    static class Cards extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        final Context ctx;
        int binds;

        Cards(Context ctx) {
            this.ctx = ctx;
        }

        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.home_card, p, false);
            v.getLayoutParams().width = (int) (300 * ctx.getResources().getDisplayMetrics().density);
            return new RecyclerView.ViewHolder(v) { };
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int i) {
            binds++;
        }

        @Override public int getItemCount() {
            return 40;
        }
    }

    @Test public void bindsOnlyWhatCanBeSeenAndScrollingRequestsNoLayout() {
        Context ctx = new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_MusicPlayer);
        CountingRv rv = new CountingRv(ctx);
        Cards adapter = new Cards(ctx);
        HeroCarousel.attach(rv);
        rv.setAdapter(adapter);
        int w = ctx.getResources().getDisplayMetrics().widthPixels;
        rv.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY));
        rv.layout(0, 0, w, 900);
        int initialBinds = adapter.binds;
        System.out.println("PERF hero: initial binds=" + initialBinds + " children=" + rv.getChildCount());
        assertTrue("cards bound for the first screen: " + initialBinds, initialBinds <= 4);

        rv.layoutRequests = 0;
        for (int frame = 0; frame < 120; frame++) rv.scrollBy(24, 0); // ~2 s of a slow drag
        System.out.println("PERF hero: after 120 scroll frames binds=" + adapter.binds + " layoutRequests=" + rv.layoutRequests);
        // One request per card that enters (its text views invalidate the row); none per scroll frame.
        assertTrue("layout requests while scrolling: " + rv.layoutRequests, rv.layoutRequests <= adapter.binds + 1); // never one per frame (120)
        assertTrue("binds while scrolling ~1 card/ " + adapter.binds, adapter.binds <= initialBinds + 8);
    }
}
