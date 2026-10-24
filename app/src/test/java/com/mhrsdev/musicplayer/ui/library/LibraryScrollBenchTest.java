package com.mhrsdev.musicplayer.ui.library;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.ui.widget.ScrollBench;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** SC1 (D39) library scroll path: Songs / track lists, Albums and Artists grids, owner-shaped library. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w412dp-h915dp-night-xxhdpi")
public class LibraryScrollBenchTest {

    static int[] range(int n) {
        int[] r = new int[n];
        for (int i = 0; i < n; i++) r[i] = i;
        return r;
    }

    @Test public void songsList() throws Exception {
        Context c = ScrollBench.context();
        ScrollBench.installLoader(c);
        List<Track> lib = ScrollBench.library();
        RecyclerView rv = new RecyclerView(c);
        rv.setLayoutManager(new LinearLayoutManager(c));
        rv.setHasFixedSize(true);
        TrackAdapter a = new TrackAdapter(new TrackAdapter.Listener() {
            @Override public void onTrackClick(List<Track> list, int position) { }

            @Override public void onTrackMore(View anchor, Track track) { }
        });
        rv.setAdapter(a);
        a.submitList(lib);
        ScrollBench.layOut(rv, 915);
        ScrollBench.settle(10);
        ScrollBench.RowCost row = ScrollBench.rowCost("songs row", rv, 0, range(lib.size()), 400);
        System.out.println(row);
        // SC1 budget (JVM bytes per bind; v0.3.2 was 10.1 KB): text, cover spec and artwork key only
        assertTrue(row.toString(), row.bindBytes() < 8 * 1024);
        System.out.println(ScrollBench.scroll("songs", rv, 300, 0, 40, null));
    }

    /**
     * A fast fling down the cover-less Songs list: how many generated covers the workers render while the list flies
     * by (nobody can see them) and after it stops (the rows on screen).
     */
    @Test public void flingRendersOnlyWhatIsSeen() throws Exception {
        org.junit.Assume.assumeTrue("timing-based: SC1_BENCH=1", ScrollBench.FULL);
        android.app.Activity act = org.robolectric.Robolectric.buildActivity(android.app.Activity.class).setup().get();
        act.setTheme(com.mhrsdev.musicplayer.R.style.Theme_MusicPlayer);
        ScrollBench.CountingCover covers = new ScrollBench.CountingCover();
        ScrollBench.installLoader(act, covers);
        List<Track> lib = ScrollBench.library();
        RecyclerView rv = new RecyclerView(act);
        rv.setLayoutManager(new LinearLayoutManager(act));
        TrackAdapter a = new TrackAdapter(new TrackAdapter.Listener() {
            @Override public void onTrackClick(List<Track> list, int position) { }

            @Override public void onTrackMore(View anchor, Track track) { }
        });
        rv.setAdapter(a);
        a.submitList(lib);
        act.setContentView(rv);
        for (int i = 0; i < 60; i++) frame(); // first screen + its covers
        int start = covers.renders.get();
        // A fling as the platform runs it (state SETTLING, ~1.4 s from 14,000 px/s to rest at 120 Hz), driven frame by
        // frame: Robolectric's own fling jumps its clock and ends in one frame.
        java.lang.reflect.Method state = RecyclerView.class.getDeclaredMethod("setScrollState", int.class);
        state.setAccessible(true);
        state.invoke(rv, RecyclerView.SCROLL_STATE_SETTLING);
        int frames = 168, during;
        for (int i = 0; i < frames; i++) {
            float left = 1f - i / (float) frames;
            rv.scrollBy(0, Math.max(1, Math.round(14000f / 120f * left * left)));
            frame();
        }
        state.invoke(rv, RecyclerView.SCROLL_STATE_IDLE);
        during = covers.renders.get() - start;
        int last = ((LinearLayoutManager) rv.getLayoutManager()).findLastVisibleItemPosition();
        for (int i = 0; i < 90; i++) frame();
        int after = covers.renders.get() - start - during;
        System.out.println("PERF fling songs: " + frames + " frames to rest, ~" + last + " rows passed; generated renders "
                + "while flinging " + during + ", after it stopped " + after);
    }

    private static void frame() {
        org.robolectric.shadows.ShadowLooper.shadowMainLooper().idleFor(java.time.Duration.ofMillis(8));
        try {
            Thread.sleep(8); // the workers run in real time beside the simulated frames
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static ListAdapter<CollectionFragment.Cell, ?> cellAdapter() throws Exception {
        Class<?> cls = Class.forName(CollectionFragment.class.getName() + "$CellAdapter");
        Class<?> listener = Class.forName(cls.getName() + "$Listener");
        Constructor<?> k = cls.getDeclaredConstructor(listener);
        k.setAccessible(true);
        Object l = Proxy.newProxyInstance(cls.getClassLoader(), new Class<?>[] {listener}, (p, m, args) -> null);
        return (ListAdapter) k.newInstance(l);
    }

    @Test public void albumsAndArtistsGrids() throws Exception {
        Context c = ScrollBench.context();
        ScrollBench.installLoader(c);
        List<Track> lib = ScrollBench.library();
        // owner's library: every cover-less download is its own "single" album on the Albums grid (folder album)
        Map<String, CollectionFragment.Cell> albums = new LinkedHashMap<>();
        Map<String, CollectionFragment.Cell> artists = new LinkedHashMap<>();
        for (Track t : lib) {
            String key = "Telegram".equals(t.albumTitle()) ? "s" + t.id() : t.albumId();
            albums.putIfAbsent(key, new CollectionFragment.Cell(key, "Telegram".equals(t.albumTitle()) ? t.title()
                    : t.albumTitle(), t.artistName(), t.artworkUri(), t.placeholderSeed(), false));
            artists.putIfAbsent(t.artistName() + t.id().length(), new CollectionFragment.Cell(t.artistId() + t.id().length(),
                    t.artistName() + " " + t.id().length(), "12 songs", null, t.placeholderSeed(), true));
        }
        for (int i = 0; i < 300; i++) {
            artists.put("x" + i, new CollectionFragment.Cell("x" + i, "Artist " + i, "3 songs", null, i, true));
        }
        bench(c, "albums grid", new ArrayList<>(albums.values()));
        bench(c, "artists grid", new ArrayList<>(artists.values()));
    }

    private void bench(Context c, String name, List<CollectionFragment.Cell> cells) throws Exception {
        RecyclerView rv = new RecyclerView(c);
        rv.setLayoutManager(new GridLayoutManager(c, 2));
        ListAdapter<CollectionFragment.Cell, ?> a = cellAdapter();
        rv.setAdapter(a);
        a.submitList(cells);
        ScrollBench.settle(3);
        ScrollBench.layOut(rv, 915);
        ScrollBench.settle(10);
        ScrollBench.RowCost cell = ScrollBench.rowCost(name + " cell", rv, 0, range(cells.size()), 200);
        System.out.println(cell);
        assertTrue(cell.toString(), cell.bindBytes() < 8 * 1024); // v0.3.2 albums: 11.8 KB
        System.out.println(ScrollBench.scroll(name, rv, 200, 0, 40, null));
    }
}
