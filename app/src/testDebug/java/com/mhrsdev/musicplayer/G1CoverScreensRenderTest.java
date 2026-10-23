package com.mhrsdev.musicplayer;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;

import androidx.fragment.app.FragmentActivity;

import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.fake.SampleData;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator;
import com.mhrsdev.musicplayer.ui.player.PlayerFragment;
import com.mhrsdev.musicplayer.ui.theme.Tokens;
import com.mhrsdev.musicplayer.ui.widget.WidgetGalleryActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.FileOutputStream;

/**
 * G1: the real shell on the sample library, whose tracks have no artwork, so every cover on Home / Library /
 * Artists / Player / mini-player is a generated one (docs/screens/g1-*.png).
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(application = FakeRenderApp.class)
public class G1CoverScreensRenderTest {
    private static final String DARK = "w412dp-h915dp-night-xhdpi";

    @Test @Config(sdk = 35, qualifiers = DARK)
    public void homeDark() throws Exception { shell(ScreenNavigator.Tab.HOME, 0, "g1-home-dark"); }

    @Test @Config(sdk = 35, qualifiers = "fa-ldrtl-" + DARK)
    public void homeFaDark() throws Exception { shell(ScreenNavigator.Tab.HOME, 0, "g1-home-fa-dark"); }

    @Test @Config(sdk = 35, qualifiers = "w412dp-h915dp-notnight-xhdpi")
    public void homeLight() throws Exception { shell(ScreenNavigator.Tab.HOME, 0, "g1-home-light"); }

    @Test @Config(sdk = 35, qualifiers = DARK)
    public void libraryDark() throws Exception { shell(ScreenNavigator.Tab.LIBRARY, 0, "g1-library-dark"); }

    @Test @Config(sdk = 35, qualifiers = DARK)
    public void albumsDark() throws Exception { shell(ScreenNavigator.Tab.LIBRARY, R.id.hub_albums, "g1-albums-dark"); }

    @Test @Config(sdk = 35, qualifiers = DARK)
    public void artistsDark() throws Exception { shell(ScreenNavigator.Tab.LIBRARY, R.id.hub_artists, "g1-artists-dark"); }

    @Test @Config(sdk = 35, qualifiers = DARK)
    public void playerDark() throws Exception { player(0, "g1-player-dark"); }

    @Test @Config(sdk = 35, qualifiers = "fa-ldrtl-" + DARK)
    public void playerFaDark() throws Exception { player(33, "g1-player-fa-dark"); }

    private void shell(ScreenNavigator.Tab tab, int clickId, String name) throws Exception {
        android.app.Application app = org.robolectric.RuntimeEnvironment.getApplication();
        org.robolectric.shadows.ShadowApplication sa = Shadow.extract(app);
        sa.grantPermissions(android.Manifest.permission.READ_MEDIA_AUDIO,
                android.Manifest.permission.READ_EXTERNAL_STORAGE);
        app.getSharedPreferences("nav", android.content.Context.MODE_PRIVATE).edit().putBoolean("perm_done", true).commit();
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        settle();
        if (tab != ScreenNavigator.Tab.HOME) {
            a.openTab(tab);
            settle();
        }
        if (clickId != 0) {
            a.findViewById(clickId).performClick();
            settle();
        }
        snap(a.getWindow().getDecorView(), Tokens.color(a, R.attr.mpBackground), name);
    }

    private void player(int index, String name) throws Exception {
        FragmentActivity a = Robolectric.buildActivity(WidgetGalleryActivity.class).setup().get();
        MusicPlayerApp app = (MusicPlayerApp) a.getApplication();
        AppContainer fakes = app.getContainer();
        fakes.player.play(SampleData.TRACKS, index, "NEFFEX");
        fakes.player.pause();
        fakes.player.seekTo(52_000L);
        settle();
        a.getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, PlayerFragment.newInstance()).commitNow();
        settle();
        snap(a.findViewById(android.R.id.content), 0xFF0F1216, name);
    }

    private static void snap(View root, int bg, String name) throws Exception {
        int w = 412 * 2, h = 915 * 2;
        for (int i = 0; i < 3; i++) { // layout, let generated covers render on the loader threads, relayout
            root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, w, h);
            settle();
        }
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(bg);
        root.draw(c);
        File dir = new File("../docs/screens");
        if (!dir.isDirectory()) dir = new File("build");
        File out = new File(dir, name + ".png");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        assertTrue(out.length() > 0);
    }

    private static void settle() throws InterruptedException {
        ShadowLooper main = Shadow.extract(Looper.getMainLooper());
        for (int i = 0; i < 10; i++) {
            main.idle();
            Thread.sleep(80);
        }
        main.idleFor(java.time.Duration.ofMillis(500));
    }
}
