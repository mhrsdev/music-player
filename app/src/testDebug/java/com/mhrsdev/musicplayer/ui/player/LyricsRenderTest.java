package com.mhrsdev.musicplayer.ui.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.View;

import androidx.fragment.app.FragmentActivity;

import com.mhrsdev.musicplayer.MusicPlayerApp;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.fake.SampleData;
import com.mhrsdev.musicplayer.ui.settings.PrefsSettingsStore;
import com.mhrsdev.musicplayer.ui.settings.SettingsStore;
import com.mhrsdev.musicplayer.ui.widget.WidgetGalleryActivity;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.FileOutputStream;

/**
 * L1 lyrics renders (docs/screens/l1-*.png): word sweep at several points of a line with real word timing (track-1),
 * emulated timing (track-2), Persian RTL with real timing (track-34), light theme, blur off (software canvas) and
 * blur on (hardware renderer through PixelCopy, the RenderEffect path), plus the top row with/without the lyrics
 * button (track-4 has no lyrics).
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LyricsRenderTest {
    private static final String DARK = "w412dp-h915dp-night-xhdpi", LIGHT = "w412dp-h915dp-notnight-xhdpi";

    @After public void reset() { System.clearProperty("robolectric.pixelCopyRenderMode"); }

    @Test @Config(sdk = 35, qualifiers = DARK)
    public void wordSweepDark() throws Exception {
        FragmentActivity a = host(0);
        openLyrics(a);
        at(a, 12_200); snap(a, "l1-word1-dark");          // "Street|lights": inside word 1
        at(a, 13_300); snap(a, "l1-word3-dark");          // "flick" being sung
        at(a, 16_300); snap(a, "l1-lineend-dark");        // whole line sung
    }

    @Test @Config(sdk = 35, qualifiers = DARK)
    public void blurOnHardware() throws Exception {
        System.setProperty("robolectric.pixelCopyRenderMode", "hardware");
        FragmentActivity a = host(0);
        openLyrics(a);
        at(a, 23_700);
        hwSnap(a, "l1-blur-on-dark");
    }

    @Test @Config(sdk = 35, qualifiers = DARK)
    public void estimatedDark() throws Exception {
        FragmentActivity a = host(1); // track-2: line-synced, emulated words
        openLyrics(a);
        at(a, 35_600); snap(a, "l1-estimated-dark");
    }

    @Test @Config(sdk = 35, qualifiers = LIGHT)
    public void wordSweepLight() throws Exception {
        FragmentActivity a = host(0);
        openLyrics(a);
        at(a, 18_900); snap(a, "l1-word-light");
    }

    @Test @Config(sdk = 35, qualifiers = "fa-ldrtl-" + DARK)
    public void persianRtl() throws Exception {
        FragmentActivity a = host(33); // track-34: Persian enhanced LRC
        openLyrics(a);
        at(a, 19_900); snap(a, "l1-fa-word2-dark");
        at(a, 23_000); snap(a, "l1-fa-lineend-dark");
    }

    @Test @Config(sdk = 35, qualifiers = DARK)
    public void noLyricsButton() throws Exception {
        FragmentActivity a = host(3); // track-4: no lyrics
        View toggle = a.findViewById(R.id.player_lyrics_toggle);
        assertEquals(View.INVISIBLE, toggle.getVisibility());
        snap(a, "l1-nolyrics-dark");
        AppContainer c = ((MusicPlayerApp) a.getApplication()).getContainer();
        c.player.previous();
        c.player.pause();
        idle();
        assertEquals(View.VISIBLE, toggle.getVisibility());
        snap(a, "l1-withlyrics-dark");
    }

    @Test @Config(sdk = 35, qualifiers = "fa-ldrtl-" + LIGHT)
    public void noLyricsButtonRtl() throws Exception {
        FragmentActivity a = host(3);
        assertEquals(View.INVISIBLE, a.findViewById(R.id.player_lyrics_toggle).getVisibility());
        snap(a, "l1-nolyrics-fa-light");
    }

    /** Shared ambient backdrop: SOFT header (static) from a two-tone cover, and FULL from a seed colour. */
    @Test @Config(sdk = 35, qualifiers = DARK)
    public void ambientBackdrop() throws Exception {
        FragmentActivity a = Robolectric.buildActivity(WidgetGalleryActivity.class).setup().get();
        android.widget.LinearLayout col = new android.widget.LinearLayout(a);
        col.setOrientation(android.widget.LinearLayout.VERTICAL);
        Bitmap cover = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        Canvas cc = new Canvas(cover);
        android.graphics.Paint pp = new android.graphics.Paint();
        pp.setShader(new android.graphics.LinearGradient(0, 0, 32, 32, 0xFFE0592A, 0xFF5B3FA8,
                android.graphics.Shader.TileMode.CLAMP));
        cc.drawRect(0, 0, 32, 32, pp);
        com.mhrsdev.musicplayer.ui.widget.AmbientBackdropView soft = new com.mhrsdev.musicplayer.ui.widget.AmbientBackdropView(a);
        com.mhrsdev.musicplayer.ui.widget.AmbientBackdropView full = new com.mhrsdev.musicplayer.ui.widget.AmbientBackdropView(a);
        col.addView(soft, new android.widget.LinearLayout.LayoutParams(-1, 0, 1f));
        col.addView(full, new android.widget.LinearLayout.LayoutParams(-1, 0, 1f));
        a.setContentView(col);
        idle();
        layout(col);
        soft.setArtwork(cover, "test-cover", 0, false);
        full.setMode(com.mhrsdev.musicplayer.ui.widget.AmbientBackdropView.Mode.FULL);
        full.setSource(null, 0xFF2A9D8F, false);
        layout(col);
        Bitmap bmp = Bitmap.createBitmap(col.getWidth(), col.getHeight(), Bitmap.Config.ARGB_8888);
        col.draw(new Canvas(bmp));
        write(bmp, "l1-ambient-soft-full-dark");
    }

    // ------------------------------------------------------------------ helpers

    private static FragmentActivity host(int index) throws Exception {
        FragmentActivity a = Robolectric.buildActivity(WidgetGalleryActivity.class).setup().get();
        MusicPlayerApp app = (MusicPlayerApp) a.getApplication();
        AppContainer fakes = new AppContainer(app, true);
        java.lang.reflect.Field f = MusicPlayerApp.class.getDeclaredField("container");
        f.setAccessible(true);
        f.set(app, fakes);
        new PrefsSettingsStore(a).setSeekStyle(SettingsStore.SeekStyle.WAVY);
        fakes.player.play(SampleData.TRACKS, index, "NEFFEX");
        fakes.player.pause();
        idle();
        PlayerFragment pf = PlayerFragment.newInstance();
        a.getSupportFragmentManager().beginTransaction().replace(android.R.id.content, pf).commitNow();
        idle();
        return a;
    }

    private static void openLyrics(FragmentActivity a) {
        a.findViewById(R.id.player_lyrics_toggle).performClick();
        idle();
    }

    private static void at(FragmentActivity a, long ms) {
        ((MusicPlayerApp) a.getApplication()).getContainer().player.seekTo(ms);
        idle();
        layout(a.findViewById(android.R.id.content));
        idle();
    }

    private static void idle() { ShadowLooper.idleMainLooper(3, java.util.concurrent.TimeUnit.SECONDS); }

    private static void layout(View v) {
        int w = 412 * 2, h = 915 * 2;
        for (int i = 0; i < 2; i++) {
            v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            v.layout(0, 0, w, h);
            idle();
        }
    }

    private static void snap(FragmentActivity a, String name) throws Exception {
        View v = a.findViewById(android.R.id.content);
        layout(v);
        Bitmap bmp = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(bmp));
        write(bmp, name);
    }

    /** Hardware renderer (real RenderNodes, RenderEffect blur) via Robolectric's PixelCopy. */
    private static void hwSnap(FragmentActivity a, String name) throws Exception {
        View decor = a.getWindow().getDecorView();
        layout(a.findViewById(android.R.id.content));
        Bitmap bmp = Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888);
        int[] result = {-1};
        PixelCopy.request(a.getWindow(), bmp, r -> result[0] = r, new Handler(Looper.getMainLooper()));
        idle();
        assertEquals(PixelCopy.SUCCESS, result[0]);
        write(bmp, name);
    }

    private static void write(Bitmap bmp, String name) throws Exception {
        File dir = new File("../docs/screens");
        dir.mkdirs();
        File out = new File(dir, name + ".png");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        assertTrue(out.length() > 0);
    }
}
