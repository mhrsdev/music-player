package com.mhrsdev.musicplayer.smoke;

import android.os.Build;
import androidx.recyclerview.widget.RecyclerView;
import com.mhrsdev.musicplayer.MainActivity;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.ui.diagnostics.DiagnosticsFragment;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

/**
 * C1 (D32): the key flows on every API generation the app supports (26 Oreo, 28 Pie, 29, 30, 31 Android 12 with
 * RenderEffect and dynamic colour, 33 notification permission / predictive back, 34, 35), driven through the X1 smoke
 * harness ({@link SmokeHostileDataTest}: real app, SQLite library, scanner, ExoPlayer, hostile MediaStore rows).
 * Any exception on any thread, error log with an app frame or crash report fails the run.
 *
 * <p>Not in the list: 27 (same code paths as 26 and 28 for everything the app gates on), 32 (as 31), 36 (Robolectric
 * 4.16 has no media_metrics service there: ExoPlayer's thread dies) and 37 (no Robolectric SDK yet); the API-37 code
 * paths are checked by reading in docs/COMPATIBILITY.md. Real hardware behaviour (blur cost, OEM kills, jank) is not
 * covered by any JVM test.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = {26, 28, 29, 30, 31, 33, 34, 35}, qualifiers = "w360dp-h780dp-xhdpi")
public class ApiMatrixSmokeTest {
    private final SmokeHostileDataTest h = new SmokeHostileDataTest();

    @Before public void setUp() throws Exception {
        h.setUp();
    }

    @After public void tearDown() throws Exception {
        h.tearDown();
    }

    /** Launch, Home, Library, tap-to-play, player open / lyrics / queue / close, Settings, Diagnostics (kept light: x8 APIs). */
    @Test public void keyFlowsWithALibrary() throws Exception {
        HostileMediaStore.install(120);
        MainActivity a = h.launch();
        h.awaitLibrary(120);
        h.frames(8); // Home
        a.openTab(ScreenNavigator.Tab.LIBRARY);
        h.frames(6);
        Assert.assertTrue("no Songs hub", h.clickIfPresent(a, R.id.hub_songs));
        h.frames(8);
        RecyclerView songs = SmokeHostileDataTest.firstVertical(a);
        Assert.assertNotNull(songs);
        songs.scrollBy(0, songs.getHeight()); // a scroll on every API's RecyclerView / fast scroller
        h.frames(3);
        songs.scrollBy(0, -songs.getHeight());
        h.frames(3);
        for (int row = 0; row < 3; row++) { // tap-to-play (D31 trigger 1)
            SmokeHostileDataTest.clickRow(songs, row);
            h.frames(6);
        }
        a.openFullPlayer();
        h.frames(12);
        h.clickIfPresent(a, R.id.player_lyrics_toggle); // lyrics view (blur only on 31+ and capable phones)
        h.frames(12);
        h.clickIfPresent(a, R.id.player_lyrics_toggle);
        h.clickIfPresent(a, R.id.player_next);
        h.clickIfPresent(a, R.id.player_play);
        a.openQueue();
        h.frames(8);
        a.getOnBackPressedDispatcher().onBackPressed(); // predictive-back dispatcher on 33+, legacy below
        h.frames(6);
        a.closeFullPlayer();
        h.frames(10);
        settingsAndDiagnostics(a);
        h.assertClean();
        Assert.assertTrue(Build.VERSION.SDK_INT >= 26);
    }

    /** A fresh install: nothing scanned yet (or nothing on the phone). */
    @Test public void firstRunWithAnEmptyLibrary() throws Exception {
        HostileMediaStore.install(0);
        SmokeHostileDataTest.clearRows();
        MainActivity a = h.launch();
        h.frames(8);
        a.openTab(ScreenNavigator.Tab.LIBRARY);
        h.frames(6);
        a.openTab(ScreenNavigator.Tab.SEARCH);
        h.frames(6);
        settingsAndDiagnostics(a);
        h.assertClean();
    }

    private void settingsAndDiagnostics(MainActivity a) throws Exception {
        a.openTab(ScreenNavigator.Tab.SETTINGS); // includes Settings > Performance
        h.frames(8);
        h.explore(a); // Settings is one scrolling page: Performance, Playback, Diagnostics entry...
        a.openFragment(new DiagnosticsFragment(), DiagnosticsFragment.TAG);
        h.frames(10);
        a.openTab(ScreenNavigator.Tab.HOME);
        h.frames(6);
    }
}
