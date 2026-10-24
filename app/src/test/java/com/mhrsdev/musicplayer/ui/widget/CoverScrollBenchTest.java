package com.mhrsdev.musicplayer.ui.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;

import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.ui.widget.cover.CoverSpec;
import com.mhrsdev.musicplayer.ui.widget.cover.GeneratedCover;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.List;
import java.util.Locale;

/** SC1 (D39): generated-cover renders per size, the cover spec, and ArtworkView's bind path. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w412dp-h915dp-night-xxhdpi")
public class CoverScrollBenchTest {

    @Test public void generatedCoverRenders() {
        org.junit.Assume.assumeTrue("timings: SC1_BENCH=1", ScrollBench.FULL);
        Context c = ScrollBench.context();
        GeneratedCover g = GeneratedCover.get(c);
        List<Track> lib = ScrollBench.library();
        for (int i = 0; i < 40; i++) g.render(CoverSpec.forTrack(lib.get(i)), 128); // warm-up
        for (int px : new int[] {64, 96, 128, 160, 192, 256, 384, 512, 640, 768, 1024}) {
            int n = px <= 256 ? 40 : px <= 512 ? 12 : 4;
            long a0 = ScrollBench.allocated(), t0 = System.nanoTime();
            for (int i = 0; i < n; i++) g.render(CoverSpec.forTrack(lib.get(100 + i)), px);
            double ms = (System.nanoTime() - t0) / 1e6 / n;
            System.out.println(String.format(Locale.ROOT, "PERF GeneratedCover.render %4d px: %7.2f ms, %8d B java heap/render",
                    px, ms, (ScrollBench.allocated() - a0) / n));
        }
    }

    @Test public void coverSpecAndKeys() {
        List<Track> lib = ScrollBench.library();
        for (int r = 0; r < 2; r++) {
            int n = 20_000;
            long a0 = ScrollBench.allocated(), t0 = System.nanoTime();
            int sink = 0;
            for (int i = 0; i < n; i++) sink += CoverSpec.forTrack(lib.get(i % lib.size())).identity().length();
            double us = (System.nanoTime() - t0) / 1e3 / n;
            long b = (ScrollBench.allocated() - a0) / n;
            long a1 = ScrollBench.allocated(), t1 = System.nanoTime();
            CoverSpec s = CoverSpec.forTrack(lib.get(3));
            for (int i = 0; i < n; i++) sink += ArtworkLoader.generatedKey(s.textless(), 156).length();
            double kus = (System.nanoTime() - t1) / 1e3 / n;
            long kb = (ScrollBench.allocated() - a1) / n;
            if (r == 1) {
                System.out.println(String.format(Locale.ROOT, "PERF CoverSpec.forTrack %6.2f us %5d B | textless+generatedKey "
                        + "%6.2f us %5d B (%d)", us, b, kus, kb, sink & 1));
            }
        }
    }

    /** One ArtworkView rebound to cover-less tracks whose covers are cached (the steady state of a scroll). */
    @Test public void artworkViewBind() throws Exception {
        Context c = ScrollBench.context();
        ArtworkLoader loader = ScrollBench.installLoader(c);
        List<Track> lib = ScrollBench.library();
        ArtworkView v = new ArtworkView(c);
        int px = Math.round(52 * c.getResources().getDisplayMetrics().density);
        v.measure(View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, px, px);
        // warm: every track once, until all covers are cached
        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < 300; i++) {
                v.bind(lib.get(i));
                if (i % 20 == 0) ScrollBench.settle(1);
            }
            ScrollBench.settle(40);
        }
        int n = 3000;
        long a0 = ScrollBench.allocated(), t0 = System.nanoTime();
        for (int i = 0; i < n; i++) v.bind(lib.get(i % 300));
        double us = (System.nanoTime() - t0) / 1e3 / n;
        long b = (ScrollBench.allocated() - a0) / n;
        Bitmap bm = v.getBitmap();
        System.out.println(String.format(Locale.ROOT, "PERF ArtworkView.bind (cached cover, %d px view) %6.2f us %5d B, "
                + "bitmap %s", px, us, b, bm == null ? "none" : bm.getWidth() + " px " + bm.getConfig()));
        ScrollBench.settle(5);
        loader.trim();
        org.junit.Assert.assertTrue("bytes per cached bind " + b, b < 4096); // v0.3.2: ~5 KB (4 keys, 2 specs)
    }

    /**
     * Binding a cover-less row never renders its generated cover on the main thread at phone densities (rows, cards,
     * grids and the mini-player are all above the 96 px synchronous limit): the render runs on the artwork worker.
     */
    @Test public void bindNeverRendersOnTheMainThread() throws Exception {
        Context c = ScrollBench.context();
        Thread main = Thread.currentThread();
        int[] onMain = {0};
        ScrollBench.CountingCover covers = new ScrollBench.CountingCover() {
            @Override public Bitmap render(CoverSpec spec, int sizePx, Bitmap.Config config) {
                if (Thread.currentThread() == main) onMain[0]++;
                return super.render(spec, sizePx, config);
            }
        };
        ScrollBench.installLoader(c, covers);
        List<Track> lib = ScrollBench.library();
        float d = c.getResources().getDisplayMetrics().density;
        for (int dp : new int[] {40, 48, 52, 56, 144, 180}) {
            ArtworkView v = new ArtworkView(c);
            int px = Math.round(dp * d);
            v.measure(View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY));
            v.layout(0, 0, px, px);
            for (int i = 0; i < 20; i++) v.bind(lib.get(dp * 11 + i));
        }
        ScrollBench.settle(20);
        org.junit.Assert.assertEquals("generated covers rendered on the main thread during bind", 0, onMain[0]);
        org.junit.Assert.assertTrue("worker renders " + covers.renders.get(), covers.renders.get() > 0);
    }
}
