package com.mhrsdev.musicplayer;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Looper;
import android.view.View;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator;
import com.mhrsdev.musicplayer.ui.theme.Tokens;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowLooper;

/**
 * Edge-to-edge checks with SIMULATED window insets (Robolectric reports none by itself): status bar + punch-hole
 * cutout on top, gesture / 3-button / missing bottom inset, landscape side cutout and side nav bar. The phone's own
 * chrome is painted over the render (status band outline, cutout hole, gesture handle) so a band, a clipped control
 * or text under the status bar is visible in docs/screens/u1-fullscreen-*.png and u1-dockinsets-*.png.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(application = FakeRenderApp.class, sdk = 35)
public class U1FullscreenRenderTest {

    /** Simulated phone chrome in dp: status bar, cutout (circle on top or side), bottom bar, side nav bar. */
    record Bars(int top, int bottom, int left, int right, int cutoutTop, int cutoutLeft, boolean gesture) {
        static Bars portrait(int bottom, boolean gesture) { return new Bars(40, bottom, 0, 0, 40, 0, gesture); }

        static Bars landscapeSideCutout() { return new Bars(24, 0, 0, 48, 0, 40, false); }
    }

    private static final Bars GESTURE = Bars.portrait(24, true);

    // ---- every screen, portrait: 40dp status + punch hole, 24dp gesture bar ----
    @Test @Config(qualifiers = "w411dp-h914dp-night-xxhdpi")
    public void home() throws Exception { shot(ScreenNavigator.Tab.HOME, Extra.NONE, GESTURE, "fullscreen-home-dark", 411, 914); }

    @Test @Config(qualifiers = "w411dp-h914dp-notnight-xxhdpi")
    public void homeLight() throws Exception { shot(ScreenNavigator.Tab.HOME, Extra.NONE, GESTURE, "fullscreen-home-light", 411, 914); }

    @Test @Config(qualifiers = "w411dp-h914dp-night-xxhdpi")
    public void library() throws Exception { shot(ScreenNavigator.Tab.LIBRARY, Extra.NONE, GESTURE, "fullscreen-library", 411, 914); }

    @Test @Config(qualifiers = "fa-w411dp-h914dp-notnight-xxhdpi")
    public void settingsFa() throws Exception { shot(ScreenNavigator.Tab.SETTINGS, Extra.NONE, GESTURE, "fullscreen-settings-fa-light", 411, 914); }

    @Test @Config(qualifiers = "w411dp-h914dp-night-xxhdpi")
    public void player() throws Exception { shot(ScreenNavigator.Tab.HOME, Extra.PLAYER, GESTURE, "fullscreen-player", 411, 914); }

    @Test @Config(qualifiers = "w411dp-h914dp-night-xxhdpi")
    public void lyrics() throws Exception { shot(ScreenNavigator.Tab.HOME, Extra.LYRICS, GESTURE, "fullscreen-lyrics", 411, 914); }

    @Test @Config(qualifiers = "w411dp-h914dp-night-xxhdpi")
    public void queue() throws Exception { shot(ScreenNavigator.Tab.HOME, Extra.QUEUE, GESTURE, "fullscreen-queue", 411, 914); }

    // ---- landscape: side punch hole + side navigation bar ----
    @Test @Config(qualifiers = "w914dp-h411dp-night-xxhdpi")
    public void homeLandscape() throws Exception {
        shot(ScreenNavigator.Tab.HOME, Extra.NONE, Bars.landscapeSideCutout(), "fullscreen-home-land", 914, 411);
    }

    @Test @Config(qualifiers = "w914dp-h411dp-night-xxhdpi")
    public void playerLandscape() throws Exception {
        shot(ScreenNavigator.Tab.HOME, Extra.PLAYER, Bars.landscapeSideCutout(), "fullscreen-player-land", 914, 411);
    }

    // ---- dock vs. bottom inset (friend's phone: pill on the edge, black strip) ----
    @Test @Config(qualifiers = "w411dp-h914dp-notnight-xxhdpi")
    public void dockZeroLight() throws Exception { shot(ScreenNavigator.Tab.HOME, Extra.NONE, Bars.portrait(0, false), "dockinsets-0-light", 411, 914); }

    @Test @Config(qualifiers = "fa-w411dp-h914dp-notnight-xxhdpi")
    public void dockZeroFaLight() throws Exception { shot(ScreenNavigator.Tab.HOME, Extra.NONE, Bars.portrait(0, false), "dockinsets-0-fa-light", 411, 914); }

    @Test @Config(qualifiers = "w411dp-h914dp-night-xxhdpi")
    public void dockGestureDark() throws Exception { shot(ScreenNavigator.Tab.HOME, Extra.NONE, GESTURE, "dockinsets-24-dark", 411, 914); }

    @Test @Config(qualifiers = "fa-w360dp-h800dp-night-xxhdpi")
    public void dockThreeButtonFaDark() throws Exception { shot(ScreenNavigator.Tab.SEARCH, Extra.NONE, Bars.portrait(48, false), "dockinsets-48-fa-dark", 360, 800); }

    @Test @Config(qualifiers = "w800dp-h360dp-notnight-xxhdpi")
    public void dockLandscapeSideNavLight() throws Exception {
        shot(ScreenNavigator.Tab.LIBRARY, Extra.NONE, Bars.landscapeSideCutout(), "dockinsets-land-light", 800, 360);
    }

    enum Extra { NONE, PLAYER, LYRICS, QUEUE }

    private void shot(ScreenNavigator.Tab tab, Extra extra, Bars bars, String name, int wDp, int hDp) throws Exception {
        android.app.Application app = org.robolectric.RuntimeEnvironment.getApplication();
        org.robolectric.shadows.ShadowApplication sa = Shadow.extract(app);
        sa.grantPermissions(android.Manifest.permission.READ_MEDIA_AUDIO, android.Manifest.permission.READ_EXTERNAL_STORAGE);
        app.getSharedPreferences("nav", android.content.Context.MODE_PRIVATE).edit().putBoolean("perm_done", true).commit();
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        settle();
        if (tab != ScreenNavigator.Tab.HOME) {
            a.openTab(tab);
            settle();
        }
        if (extra != Extra.NONE) {
            a.openFullPlayer();
            settle();
        }
        if (extra == Extra.LYRICS) {
            Fragment p = a.getSupportFragmentManager().findFragmentByTag("player");
            if (p != null) p.requireView().findViewById(R.id.player_lyrics_toggle).performClick();
            settle();
        }
        if (extra == Extra.QUEUE) {
            a.openQueue();
            settle();
        }
        float d = a.getResources().getDisplayMetrics().density;
        WindowInsetsCompat insets = insets(bars, d);
        View root = a.getWindow().getDecorView();
        int w = Math.round(wDp * d), h = Math.round(hDp * d);
        for (int i = 0; i < 3; i++) {
            ViewCompat.dispatchApplyWindowInsets(root, insets);
            root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, w, h);
            settle();
        }
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        // No pre-fill: whatever the window itself paints is what the phone shows (a black strip would show here).
        c.drawColor(Color.BLACK);
        root.draw(c);
        Fragment q = a.getSupportFragmentManager().findFragmentByTag("queue");
        if (q instanceof DialogFragment df && df.getDialog() != null && df.getDialog().getWindow() != null) {
            View dv = df.getDialog().getWindow().getDecorView();
            ViewCompat.dispatchApplyWindowInsets(dv, insets);
            dv.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            dv.layout(0, 0, w, h);
            settle();
            c.drawColor(0x66000000); // dialog dim
            dv.draw(c);
        }
        paintPhoneChrome(c, bars, d, w, h, Tokens.color(a, R.attr.mpOnSurface));
        File dir = new File("../docs/screens");
        if (!dir.isDirectory()) dir = new File("build");
        File out = new File(dir, "u1-" + name + ".png");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        assertTrue(out.length() > 0);
    }

    static WindowInsetsCompat insets(Bars b, float d) {
        Insets sys = Insets.of(px(b.left(), d), px(b.top(), d), px(b.right(), d), px(b.bottom(), d));
        Insets status = Insets.of(0, px(b.top(), d), 0, 0);
        Insets nav = Insets.of(px(b.left(), d), 0, px(b.right(), d), px(b.bottom(), d));
        Insets cutout = Insets.of(px(b.cutoutLeft(), d), px(b.cutoutTop(), d), 0, 0);
        WindowInsetsCompat.Builder builder = new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), status)
                .setInsets(WindowInsetsCompat.Type.navigationBars(), nav)
                .setInsets(WindowInsetsCompat.Type.tappableElement(), b.gesture() ? Insets.of(0, px(b.top(), d), 0, 0) : sys)
                .setInsets(WindowInsetsCompat.Type.mandatorySystemGestures(), nav)
                .setInsets(WindowInsetsCompat.Type.systemGestures(), nav)
                .setInsets(WindowInsetsCompat.Type.displayCutout(), cutout);
        return builder.build();
    }

    /** Outlines the status bar, draws the punch hole and the gesture handle / 3-button bar like the phone does. */
    private static void paintPhoneChrome(Canvas c, Bars b, float d, int w, int h, int fg) {
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(0xFFFF2D55);
        line.setStrokeWidth(Math.max(1f, d));
        if (b.top() > 0) c.drawLine(0, b.top() * d, w, b.top() * d, line); // text must stay below this line
        Paint hole = new Paint(Paint.ANTI_ALIAS_FLAG);
        hole.setColor(Color.BLACK);
        if (b.cutoutTop() > 0) c.drawCircle(w / 2f, b.cutoutTop() * d / 2f, 10 * d, hole);
        if (b.cutoutLeft() > 0) c.drawCircle(b.cutoutLeft() * d / 2f, h / 2f, 10 * d, hole);
        Paint clock = new Paint(Paint.ANTI_ALIAS_FLAG);
        clock.setColor(fg);
        clock.setTextSize(14 * d);
        if (b.top() > 0) c.drawText("20:47", 24 * d, b.top() * d * 0.65f, clock);
        Paint handle = new Paint(Paint.ANTI_ALIAS_FLAG);
        handle.setColor(fg);
        if (b.gesture()) {
            float hw = 54 * d, y = h - 8 * d;
            c.drawRoundRect(new RectF(w / 2f - hw, y - 2 * d, w / 2f + hw, y + 2 * d), 2 * d, 2 * d, handle);
        } else if (b.bottom() > 0) {
            float y = h - b.bottom() * d;
            c.drawLine(0, y, w, y, line);
        } else if (b.right() > 0) {
            float x = w - b.right() * d;
            c.drawLine(x, 0, x, h, line);
        }
    }

    private static int px(int dp, float d) { return Math.round(dp * d); }

    private static void settle() throws InterruptedException {
        for (int i = 0; i < 6; i++) {
            ((ShadowLooper) Shadow.extract(Looper.getMainLooper())).idle();
            Thread.sleep(60);
        }
        ((ShadowLooper) Shadow.extract(Looper.getMainLooper())).idleFor(java.time.Duration.ofMillis(500));
    }
}
