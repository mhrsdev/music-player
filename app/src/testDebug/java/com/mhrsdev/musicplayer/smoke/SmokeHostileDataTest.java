package com.mhrsdev.musicplayer.smoke;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.mhrsdev.musicplayer.MainActivity;
import com.mhrsdev.musicplayer.MusicPlayerApp;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.diag.Diagnostics;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.ui.diagnostics.DiagnosticsFragment;
import com.mhrsdev.musicplayer.ui.nav.FeatureScreens;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowLooper;

/**
 * X1 (D30, D31): the REAL app (SQLite library, scanner, home feed, ExoPlayer controller) on Android 15 at a large
 * xxxhdpi window, fed a hostile MediaStore (see {@link HostileMediaStore}), driven through every screen: Home with its
 * section pages, all Library hubs, Search with odd queries, tap-to-play, full player + queue + lyrics, Settings and
 * its sub-screens, plus scroll / fling through every list, configuration changes and rapid Back. Any exception on the
 * main thread fails directly; exceptions on worker threads are captured by a default handler and fail at the end, as
 * do error logs that carry one of our stack frames.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 35, qualifiers = "w360dp-h780dp-xxxhdpi") // 36: Robolectric 4.16 has no media_metrics service, which kills ExoPlayer's thread
public class SmokeHostileDataTest {
    private static final List<Throwable> BACKGROUND = Collections.synchronizedList(new ArrayList<>());
    private Thread.UncaughtExceptionHandler previous;
    private ActivityController<MainActivity> ctl;
    private Bitmap frame;

    @Before public void setUp() throws Exception {
        BACKGROUND.clear();
        previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            // PlayerHolder.resetForTest releases on a helper thread on purpose (see there): its complaint is test-only
            if ("release-test-player".equals(t.getName())) return;
            BACKGROUND.add(new AssertionError("uncaught on thread " + t.getName(), e));
        });
        resetPlayer();
        Diagnostics.get(RuntimeEnvironment.getApplication()).clear();
        ShadowLog.clear();
        // Debug StrictMode (MusicPlayerApp) flags Robolectric's own non-UI contexts thousands of times: noise + minutes.
        android.os.StrictMode.setVmPolicy(android.os.StrictMode.VmPolicy.LAX);
        android.os.StrictMode.setThreadPolicy(android.os.StrictMode.ThreadPolicy.LAX);
        android.app.Application app = RuntimeEnvironment.getApplication();
        org.robolectric.shadows.ShadowApplication sa = Shadow.extract(app);
        sa.grantPermissions(android.Manifest.permission.READ_MEDIA_AUDIO, android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.POST_NOTIFICATIONS);
        app.getSharedPreferences("nav", android.content.Context.MODE_PRIVATE).edit().putBoolean("perm_done", true)
                .putBoolean("notif_asked", true).commit();
    }

    @After public void tearDown() throws Exception {
        try {
            if (ctl != null) ctl.pause().stop().destroy();
        } catch (RuntimeException ignored) {
            // already destroyed by a test step
        }
        Thread.setDefaultUncaughtExceptionHandler(previous);
        resetPlayer();
    }

    /** PlayerHolder is package-private in playback: a fresh ExoPlayer per test (see PlaybackNotificationTest). */
    private static void resetPlayer() throws Exception {
        java.lang.reflect.Method m = Class.forName("com.mhrsdev.musicplayer.playback.PlayerHolder")
                .getDeclaredMethod("resetForTest");
        m.setAccessible(true);
        m.invoke(null);
    }

    // ---------------------------------------------------------------------------------------------------- tests

    @Test public void emptyLibrary() throws Exception {
        HostileMediaStore.install(0);
        clearRows();
        MainActivity a = launch();
        visitEverything(a, false);
        assertClean();
    }

    @Test public void hostileLibraryEveryScreenAndTapToPlay() throws Exception {
        HostileMediaStore.install(2_000);
        MainActivity a = launch();
        awaitLibrary(2_000);
        visitEverything(a, true);
        assertClean();
    }

    @Test public void configurationChangesAndRapidBack() throws Exception {
        HostileMediaStore.install(300);
        MainActivity a = launch();
        awaitLibrary(300);
        play(a);
        a.openFullPlayer();
        frames(10);
        // rotation, dark/light, Persian, font scale 2, display size: each recreates the shell with the player open
        a = reconfigure("+land");
        a = reconfigure("+night");
        a = reconfigure("+notnight");
        a = reconfigure("fa");
        RuntimeEnvironment.setFontScale(2.0f);
        a = reconfigure("+port");
        a = reconfigure("w411dp-h891dp-xxhdpi");
        explore(a);
        // tabs hammered, then Back faster than the transitions finish
        for (int i = 0; i < 3; i++) {
            for (ScreenNavigator.Tab t : ScreenNavigator.Tab.values()) a.openTab(t);
            ShadowLooper.idleMainLooper(5, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        a.openTab(ScreenNavigator.Tab.LIBRARY);
        clickIfPresent(a, R.id.hub_albums);
        clickIfPresent(a, R.id.hub_artists);
        a.openFullPlayer();
        for (int i = 0; i < 8; i++) {
            a.getOnBackPressedDispatcher().onBackPressed();
            ShadowLooper.idleMainLooper(5, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        frames(20);
        ctl.pause().stop();
        frames(5);
        ctl.start().resume();
        frames(10);
        RuntimeEnvironment.setFontScale(1.0f);
        assertClean();
    }

    // ---------------------------------------------------------------------------------------------------- flows

    private void visitEverything(MainActivity a, boolean hasTracks) throws Exception {
        // Home: every list (vertical + every carousel), every "See all" page
        explore(a);
        for (View action : findAll(a.getWindow().getDecorView(), R.id.action)) {
            if (action.getVisibility() != View.VISIBLE || !action.isAttachedToWindow()) continue;
            action.performClick();
            frames(6);
            explore(a);
            a.getOnBackPressedDispatcher().onBackPressed();
            frames(6);
        }
        // Library hubs
        int[] hubs = {R.id.hub_songs, R.id.hub_albums, R.id.hub_artists, R.id.hub_playlists, R.id.hub_history,
                R.id.hub_most_played, R.id.hub_recent_added, R.id.hub_never_played, R.id.hub_years, R.id.hub_genres,
                R.id.hub_folders};
        for (int hub : hubs) {
            a.openTab(ScreenNavigator.Tab.LIBRARY);
            frames(6);
            if (!clickIfPresent(a, hub)) continue;
            frames(6);
            explore(a);
            // one level deeper: first album / artist / playlist / year / genre / folder
            RecyclerView list = firstVertical(a);
            if (list != null && clickRow(list, 0)) {
                frames(8);
                explore(a);
                a.getOnBackPressedDispatcher().onBackPressed();
                frames(4);
            }
        }
        // Search: odd queries, every filter chip
        a.openTab(ScreenNavigator.Tab.SEARCH);
        frames(6);
        EditText q = a.findViewById(R.id.query);
        assertNotNull(q);
        String[] queries = {"a", "%", "_", "'", "\"", "\\", "*", "آهنگ", "🎵", "​", " ",
                "Track 1", "x".repeat(600), "'; DROP TABLE tracks; --", ""};
        int[] chips = {R.id.chip_all, R.id.chip_songs, R.id.chip_albums, R.id.chip_artists, R.id.chip_playlists};
        for (int i = 0; i < queries.length; i++) {
            q.setText(queries[i]);
            frames(3);
            clickIfPresent(a, chips[i % chips.length]);
            frames(3);
            if (i % 4 == 0) explore(a);
        }
        // Tap to play (owner D31 trigger 1), then the full player, lyrics and queue
        if (hasTracks) {
            play(a);
            playerCoverEnterDragExit(a);
            a.openFullPlayer();
            frames(20);
            explore(a);
            clickIfPresent(a, R.id.player_lyrics_toggle);
            frames(20);
            explore(a);
            clickIfPresent(a, R.id.player_lyrics_toggle);
            clickIfPresent(a, R.id.player_next);
            clickIfPresent(a, R.id.player_play);
            clickIfPresent(a, R.id.player_prev);
            clickIfPresent(a, R.id.player_shuffle);
            clickIfPresent(a, R.id.player_repeat);
            clickIfPresent(a, R.id.player_like);
            frames(10);
            a.openQueue();
            frames(10);
            explore(a);
            a.getOnBackPressedDispatcher().onBackPressed();
            frames(6);
            a.closeFullPlayer();
            frames(10);
        }
        // Settings and every sub-screen
        a.openTab(ScreenNavigator.Tab.SETTINGS);
        frames(8);
        explore(a);
        for (FeatureScreens.Entry e : FeatureScreens.available(a)) {
            a.openTab(ScreenNavigator.Tab.SETTINGS);
            frames(4);
            androidx.fragment.app.Fragment f = FeatureScreens.instantiate(a.getSupportFragmentManager(), a, e.className());
            assertNotNull(e.className(), f);
            a.openFragment(f, e.tag());
            frames(10);
            explore(a);
        }
        a.openFragment(new DiagnosticsFragment(), DiagnosticsFragment.TAG);
        frames(10);
        explore(a);
        a.openTab(ScreenNavigator.Tab.HOME);
        frames(10);
        explore(a);
    }

    /**
     * PF-A's PlayerCover: the shell under the opaque player is hidden only while the player rests; a drag shows it, a
     * short drag returns, a long drag closes and everything under it is visible again; Back during the close animation
     * must not crash (the end action runs after the fragment is gone).
     */
    private void playerCoverEnterDragExit(MainActivity a) throws Exception {
        View shell = a.findViewById(R.id.screen_container);
        a.openFullPlayer();
        frames(40);
        assertTrue("player missing", a.getSupportFragmentManager().findFragmentByTag("player") != null);
        View root = a.findViewById(R.id.player_root);
        drag(root, 300, 600); // short and slow: snaps back
        frames(30);
        drag(root, 300, 2600); // long: closes
        frames(40);
        assertTrue("player still open", a.getSupportFragmentManager().findFragmentByTag("player") == null);
        assertEquals("shell left hidden after closing the player", View.VISIBLE, shell.getVisibility());
        assertEquals(View.VISIBLE, a.findViewById(R.id.chrome).getVisibility());
        assertNotNull("root background not restored", a.findViewById(R.id.root).getBackground());
        // close by drag, then Back before the 200 ms close animation ends
        a.openFullPlayer();
        frames(40);
        drag(a.findViewById(R.id.player_root), 300, 2600);
        a.getOnBackPressedDispatcher().onBackPressed();
        frames(40);
        assertEquals(View.VISIBLE, shell.getVisibility());
    }

    private static void drag(View v, float fromY, float toY) {
        long t = android.os.SystemClock.uptimeMillis();
        v.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, 200, fromY, 0));
        for (int i = 1; i <= 10; i++) {
            float y = fromY + (toY - fromY) * i / 10f;
            v.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + i * 100, android.view.MotionEvent.ACTION_MOVE, 200, y, 0));
        }
        v.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 1100, android.view.MotionEvent.ACTION_UP, 200, toY, 0));
    }

    /** Library > Songs, tap the first rows like a person picking a song (the hostile ones come first by title). */
    private void play(MainActivity a) throws Exception {
        a.openTab(ScreenNavigator.Tab.LIBRARY);
        frames(6);
        assertTrue("no Songs hub", clickIfPresent(a, R.id.hub_songs));
        frames(10);
        RecyclerView songs = firstVertical(a);
        assertNotNull(songs);
        for (int i = 0; i < 6; i++) {
            clickRow(songs, i);
            frames(15);
        }
        explore(a);
    }

    // ---------------------------------------------------------------------------------------------------- driving

    private MainActivity launch() throws Exception {
        ctl = Robolectric.buildActivity(MainActivity.class).setup();
        frames(10);
        return ctl.get();
    }

    private MainActivity reconfigure(String qualifiers) throws Exception {
        RuntimeEnvironment.setQualifiers(qualifiers);
        Configuration c = new Configuration(RuntimeEnvironment.getApplication().getResources().getConfiguration());
        ctl.configurationChange(c);
        frames(12);
        MainActivity a = ctl.get();
        explore(a);
        return a;
    }

    private static void clearRows() throws Exception {
        java.lang.reflect.Field f = HostileMediaStore.class.getDeclaredField("ROWS");
        f.setAccessible(true);
        ((List<?>) f.get(null)).clear();
    }

    private void awaitLibrary(int atLeast) throws Exception {
        MusicPlayerApp app = (MusicPlayerApp) RuntimeEnvironment.getApplication();
        int[] seen = {0};
        Observer<List<Track>> o = t -> seen[0] = t == null ? 0 : t.size();
        app.getContainer().library.getAllTracks().observeForever(o);
        for (int i = 0; i < 400 && seen[0] < atLeast; i++) {
            ShadowLooper.idleMainLooper();
            Thread.sleep(50);
        }
        app.getContainer().library.getAllTracks().removeObserver(o);
        assertTrue("library never scanned: " + seen[0], seen[0] >= atLeast);
        frames(10);
    }

    /** Scrolls every scrollable thing on screen to its end and back, drawing frames as it goes; flings the first list. */
    private void explore(MainActivity a) throws Exception {
        frames(2);
        List<View> scrollers = new ArrayList<>();
        collectScrollers(a.getWindow().getDecorView(), scrollers);
        boolean flung = false;
        for (View v : scrollers) {
            if (!v.isAttachedToWindow() || v.getWidth() == 0) continue;
            if (v instanceof RecyclerView rv) {
                boolean horizontal = rv.getLayoutManager() instanceof LinearLayoutManager lm
                        && lm.getOrientation() == RecyclerView.HORIZONTAL;
                int step = horizontal ? Math.max(1, rv.getWidth() * 4 / 5) : Math.max(1, rv.getHeight() * 3 / 4);
                for (int i = 0; i < 16 && (horizontal ? rv.canScrollHorizontally(1) : rv.canScrollVertically(1)); i++) {
                    if (horizontal) rv.scrollBy(step, 0); else rv.scrollBy(0, step);
                    frames(1);
                }
                for (int i = 0; i < 16 && (horizontal ? rv.canScrollHorizontally(-1) : rv.canScrollVertically(-1)); i++) {
                    if (horizontal) rv.scrollBy(-step, 0); else rv.scrollBy(0, -step);
                    frames(1);
                }
                if (!flung && !horizontal && rv.isAttachedToWindow()) { // a real fling: momentum + prefetch + recycling
                    flung = true;
                    rv.fling(0, 24_000);
                    frames(24);
                    rv.fling(0, -24_000);
                    frames(24);
                }
            } else if (v instanceof NestedScrollView ns) {
                ns.fullScroll(View.FOCUS_DOWN);
                frames(3);
                ns.fullScroll(View.FOCUS_UP);
                frames(2);
            }
        }
    }

    private static void collectScrollers(View v, List<View> out) {
        if (v instanceof RecyclerView || v instanceof NestedScrollView) out.add(v);
        if (v instanceof ViewGroup g) for (int i = 0; i < g.getChildCount(); i++) collectScrollers(g.getChildAt(i), out);
    }

    private static List<View> findAll(View v, int id) {
        List<View> out = new ArrayList<>();
        collect(v, id, out);
        return out;
    }

    private static void collect(View v, int id, List<View> out) {
        if (v.getId() == id) out.add(v);
        if (v instanceof ViewGroup g) for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), id, out);
    }

    private static RecyclerView firstVertical(MainActivity a) {
        View container = a.findViewById(R.id.screen_container);
        List<View> all = new ArrayList<>();
        collectScrollers(container, all);
        for (View v : all) {
            if (v instanceof RecyclerView rv && v.isShown() && !(rv.getLayoutManager() instanceof LinearLayoutManager lm
                    && lm.getOrientation() == RecyclerView.HORIZONTAL)) return rv;
        }
        return null;
    }

    private static boolean clickRow(RecyclerView rv, int position) {
        RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(position);
        if (h == null) return false;
        return h.itemView.performClick();
    }

    private boolean clickIfPresent(MainActivity a, int id) throws Exception {
        View v = a.findViewById(id);
        if (v == null || !v.isShown()) return false;
        v.performClick();
        frames(4);
        return true;
    }

    /** Advances {@code n} 16 ms frames: main looper + workers' posts, then a real measure / layout / draw. */
    private void frames(int n) throws Exception {
        for (int i = 0; i < n; i++) {
            ShadowLooper.idleMainLooper(16, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (i % 4 == 3) Thread.sleep(3); // let the artwork / io threads deliver
            if (ctl == null) continue;
            MainActivity a = ctl.get();
            if (a == null || a.isDestroyed()) continue;
            View root = a.getWindow().getDecorView();
            float d = a.getResources().getDisplayMetrics().density;
            Configuration c = a.getResources().getConfiguration();
            int w = Math.round(c.screenWidthDp * d), h = Math.round(c.screenHeightDp * d);
            root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, w, h);
            if (frame == null || frame.getWidth() != w || frame.getHeight() != h) {
                frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            }
            root.draw(new Canvas(frame));
        }
        ((ShadowLooper) Shadow.extract(Looper.getMainLooper())).idleFor(Duration.ofMillis(1));
        failOnBackground();
    }

    private static void failOnBackground() {
        synchronized (BACKGROUND) {
            if (!BACKGROUND.isEmpty()) {
                AssertionError e = new AssertionError("exception on a worker thread", BACKGROUND.get(0));
                BACKGROUND.clear();
                throw e;
            }
        }
    }

    /** No worker-thread exception, no error log carrying one of our frames, no crash file written. */
    private void assertClean() {
        failOnBackground();
        List<String> ours = new ArrayList<>();
        for (ShadowLog.LogItem item : ShadowLog.getLogs()) {
            if (item.type < Log.ERROR || "StrictMode".equals(item.tag)) continue;
            String trace = item.throwable == null ? "" : Log.getStackTraceString(item.throwable);
            System.out.println("[smoke] E/" + item.tag + ": " + item.msg + (item.throwable == null ? "" : " " + item.throwable));
            if ("AndroidRuntime".equals(item.tag) || trace.contains("com.mhrsdev.musicplayer")) {
                ours.add(item.tag + ": " + item.msg + "\n" + trace);
            }
        }
        if (!ours.isEmpty()) fail("error logs from the app:\n" + String.join("\n", ours));
        String crash = Diagnostics.get(RuntimeEnvironment.getApplication()).lastCrash();
        if (crash != null) fail("crash report written:\n" + crash);
    }
}
