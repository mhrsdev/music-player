package com.mhrsdev.musicplayer.smoke;

import static org.junit.Assert.assertTrue;

import androidx.recyclerview.widget.RecyclerView;
import com.mhrsdev.musicplayer.MainActivity;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator;
import com.mhrsdev.musicplayer.ui.widget.ArtworkLoader;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

/**
 * C1 (D32): Home, Library and the player with 2,000 tracks inside a 256 MB heap (a phone's per-app limit is 128-512 MB,
 * and this JVM also holds the Robolectric framework). Run with {@code ./gradlew :app:testSmallHeap}; in the normal 2 GB
 * test task it is skipped, because there it would prove nothing. No OutOfMemoryError on any thread, and the artwork
 * cache stays inside its budget.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 35, qualifiers = "w360dp-h780dp-xxhdpi")
public class SmallHeapTest {
    private static final long SMALL = 300L * 1024 * 1024;
    private final SmokeHostileDataTest h = new SmokeHostileDataTest();

    @Before public void setUp() throws Exception {
        Assume.assumeTrue("heap is " + Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MB: run :app:testSmallHeap",
                Runtime.getRuntime().maxMemory() <= SMALL);
        h.setUp();
    }

    @After public void tearDown() throws Exception {
        h.tearDown();
    }

    @Test public void twoThousandTracksFitInASmallHeap() throws Exception {
        HostileMediaStore.install(2_000);
        MainActivity a = h.launch();
        h.awaitLibrary(2_000);
        h.explore(a); // Home: every list and carousel
        a.openTab(ScreenNavigator.Tab.LIBRARY);
        h.frames(6);
        h.explore(a);
        h.clickIfPresent(a, R.id.hub_songs);
        h.frames(8);
        RecyclerView songs = SmokeHostileDataTest.firstVertical(a);
        for (int pass = 0; pass < 3; pass++) { // 2,000 rows scrolled end to end, flung, three times
            for (int i = 0; i < 40 && songs.canScrollVertically(1); i++) {
                songs.scrollBy(0, songs.getHeight() * 3 / 4);
                h.frames(1);
            }
            songs.fling(0, -30_000);
            h.frames(20);
            songs.scrollToPosition(0);
            h.frames(4);
        }
        for (int row = 0; row < 4; row++) {
            SmokeHostileDataTest.clickRow(songs, row);
            h.frames(8);
        }
        a.openFullPlayer();
        h.frames(30);
        h.clickIfPresent(a, R.id.player_lyrics_toggle);
        h.frames(20);
        a.openQueue(); // 2,000 queue rows
        h.frames(10);
        h.explore(a);
        h.assertClean(); // also fails on an OutOfMemoryError that reached a worker thread's handler

        ArtworkLoader artwork = ArtworkLoader.get(RuntimeEnvironment.getApplication());
        long heap = Runtime.getRuntime().maxMemory();
        System.out.println("PERF small heap " + heap / (1024 * 1024) + " MB: artwork cache " + artwork.cacheUsedBytes() / 1024
                + " / " + artwork.cacheMaxBytes() / 1024 + " KB, heap in use " + (heap - Runtime.getRuntime().freeMemory()
                - (heap - Runtime.getRuntime().totalMemory())) / (1024 * 1024) + " MB");
        assertTrue("cache over its budget: " + artwork.cacheUsedBytes(), artwork.cacheUsedBytes() <= artwork.cacheMaxBytes());
        assertTrue("cache budget is more than 1/8 of this heap: " + artwork.cacheMaxBytes(),
                artwork.cacheMaxBytes() <= Math.max(6L * 1024 * 1024, heap / 8));
    }
}
