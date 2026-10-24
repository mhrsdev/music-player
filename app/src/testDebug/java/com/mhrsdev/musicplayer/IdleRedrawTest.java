package com.mhrsdev.musicplayer;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;

import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.ui.motion.FrameDriver;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowChoreographer;
import org.robolectric.shadows.ShadowLooper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * IDLE REDRAW audit (SC1, D39/D40: scroll jank, heat and 6 % battery in 5 minutes on an S26 Ultra). Nobody touches
 * the screen: for every top-level screen the real shell (fake data) runs on a simulated 120 Hz Choreographer for 3 s
 * and every vsync that has an invalidated view counts as a rendered frame (the views that asked are listed). A static
 * screen must render ~0 frames per second; the player only what it needs (decorative loops on the shared 30 fps beat,
 * the 4 Hz position). Each dirty frame is drawn into a software canvas, so a view that re-invalidates from onDraw
 * (an animation loop) keeps counting, exactly like on a phone.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(application = FakeRenderApp.class, sdk = 35, qualifiers = "w412dp-h915dp-night-xhdpi")
public class IdleRedrawTest {
    private static final long FRAME_NS = 8_333_333L; // 120 Hz
    private static final int SECONDS = 3;

    private MainActivity a;
    private final Map<String, String> table = new LinkedHashMap<>();

    @Test public void idleFramesPerScreen() throws Exception {
        ShadowChoreographer.setFrameDelay(Duration.ofNanos(FRAME_NS));
        // the decorative beat must see the simulated vsync clock, not the JVM's wall clock
        FrameDriver.clock = () -> android.os.SystemClock.uptimeMillis() * 1_000_000L;
        try {
            run();
        } finally {
            FrameDriver.clock = System::nanoTime;
        }
    }

    private void run() {
        android.app.Application app = RuntimeEnvironment.getApplication();
        org.robolectric.shadows.ShadowApplication sa = Shadow.extract(app);
        sa.grantPermissions(android.Manifest.permission.READ_MEDIA_AUDIO, android.Manifest.permission.READ_EXTERNAL_STORAGE);
        app.getSharedPreferences("nav", android.content.Context.MODE_PRIVATE).edit().putBoolean("perm_done", true).commit();
        a = Robolectric.buildActivity(MainActivity.class).setup().get();
        AppContainer c = ((MusicPlayerApp) a.getApplication()).getContainer();
        settle();

        double homePaused = measure("Home (paused, mini-player shown)");
        c.player.resume();
        double homePlaying = measure("Home (playing)");
        a.openTab(ScreenNavigator.Tab.LIBRARY);
        double library = measure("Library hub (playing)");
        a.findViewById(R.id.hub_songs).performClick();
        double songs = measure("Songs list (playing)");
        a.openTab(ScreenNavigator.Tab.SEARCH);
        double search = measure("Search (playing)");
        a.openTab(ScreenNavigator.Tab.SETTINGS);
        double settings = measure("Settings (playing)");
        c.player.pause();
        double settingsPaused = measure("Settings (paused)");
        c.player.resume();
        a.openFullPlayer();
        double player = measure("Player cover (playing)");
        View toggle = a.findViewById(R.id.player_lyrics_toggle);
        if (toggle != null && toggle.getVisibility() == View.VISIBLE) {
            toggle.performClick();
            measure("Player lyrics (playing)");
            toggle.performClick();
        }
        c.player.pause();
        double playerPaused = measure("Player cover (paused)");
        c.player.resume();
        a.openQueue();
        measure("Queue sheet over player (playing)");

        StringBuilder sb = new StringBuilder("PERF idle redraw (rendered frames/s at 120 Hz, nobody touching):\n");
        for (Map.Entry<String, String> e : table.entrySet()) sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        System.out.println(sb);
        // static screens must let the panel idle; playing shows only the mini-player's <= 4 Hz progress
        assertTrue("Home paused " + homePaused, homePaused <= 1);
        assertTrue("Settings paused " + settingsPaused, settingsPaused <= 1);
        assertTrue("Player paused " + playerPaused, playerPaused <= 2);
        for (double fps : new double[] {homePlaying, library, songs, search, settings}) assertTrue("" + fps, fps <= 5);
        assertTrue("Player playing " + player, player <= 36);
    }

    /** Lets one-shot transitions finish, then counts dirty vsyncs for {@link #SECONDS} seconds. */
    private double measure(String name) {
        settle();
        int frames = 0, n = (int) (SECONDS * 1_000_000_000L / FRAME_NS);
        Map<String, Integer> who = new TreeMap<>();
        long t0 = android.os.SystemClock.uptimeMillis();
        for (int i = 0; i < n; i++) {
            ShadowLooper.idleMainLooper(FRAME_NS, java.util.concurrent.TimeUnit.NANOSECONDS);
            boolean dirty = false;
            for (View root : roots()) {
                if (!root.isDirty()) continue;
                dirty = true;
                List<View> leaves = new ArrayList<>();
                dirtyLeaves(root, leaves);
                for (View v : leaves) who.merge(label(v), 1, Integer::sum);
                draw(root);
            }
            if (dirty) frames++;
        }
        // per simulated second actually elapsed (Robolectric may move its clock with a late vsync)
        double secs = Math.max(SECONDS, (android.os.SystemClock.uptimeMillis() - t0) / 1000.0);
        double fps = frames / secs;
        StringBuilder sb = new StringBuilder(String.format(java.util.Locale.ROOT, "%5.1f fps (%d frames / %.1f s)", fps,
                frames, secs));
        if (!who.isEmpty()) sb.append("  <- ").append(who);
        table.put(name, sb.toString());
        return fps;
    }

    private void settle() {
        for (int i = 0; i < 300; i++) { // 2.5 s: enter fades, crossfades, first loads
            ShadowLooper.idleMainLooper(FRAME_NS, java.util.concurrent.TimeUnit.NANOSECONDS);
            for (View root : roots()) {
                if (root.getWidth() == 0) layout(root);
                if (root.isDirty() || root.isLayoutRequested()) {
                    if (root.isLayoutRequested()) layout(root);
                    draw(root);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<View> roots() {
        try {
            Class<?> g = Class.forName("android.view.WindowManagerGlobal");
            Object inst = g.getMethod("getInstance").invoke(null);
            java.lang.reflect.Field f = g.getDeclaredField("mViews");
            f.setAccessible(true);
            List<View> out = new ArrayList<>();
            for (View v : (List<View>) f.get(inst)) if (v.getVisibility() == View.VISIBLE) out.add(v);
            return out;
        } catch (ReflectiveOperationException e) {
            return List.of(a.getWindow().getDecorView());
        }
    }

    private static void layout(View root) {
        float d = root.getResources().getDisplayMetrics().density;
        int w = Math.round(412 * d), h = Math.round(915 * d);
        int hs = root == root.getRootView() && root.getLayoutParams() != null
                && root.getLayoutParams().height == ViewGroup.LayoutParams.WRAP_CONTENT
                ? View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.AT_MOST)
                : View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY);
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), hs);
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
    }

    private static void draw(View root) {
        if (root.getWidth() <= 0 || root.getHeight() <= 0) return;
        // 1/8 scale: the whole tree draws (onDraw runs, dirty flags clear, loops re-arm) for ~1/64 of the raster cost
        Bitmap bmp = Bitmap.createBitmap(Math.max(1, root.getWidth() / 8), Math.max(1, root.getHeight() / 8),
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.scale(1 / 8f, 1 / 8f);
        root.draw(c);
        bmp.recycle();
    }

    /** The deepest invalidated views (their parents are dirty only because of them). */
    private static void dirtyLeaves(View v, List<View> out) {
        if (!v.isDirty() || v.getVisibility() != View.VISIBLE) return; // hidden subtrees keep stale flags
        boolean child = false;
        if (v instanceof ViewGroup g) {
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = g.getChildAt(i);
                if (c.isDirty() && c.getVisibility() == View.VISIBLE) {
                    child = true;
                    dirtyLeaves(c, out);
                }
            }
        }
        if (!child) out.add(v);
    }

    private static String label(View v) {
        String id = "";
        if (v.getId() != View.NO_ID) {
            try {
                id = "#" + v.getResources().getResourceEntryName(v.getId());
            } catch (RuntimeException ignored) {
                // generated id
            }
        }
        return v.getClass().getSimpleName() + id;
    }
}
