package com.mhrsdev.musicplayer.ui.player;

import static org.junit.Assert.assertTrue;
import org.robolectric.shadows.ShadowLooper;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import androidx.fragment.app.FragmentActivity;

import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.ui.widget.WidgetGalleryActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/** Renders the full player (fake data) to docs/screens/p2-player-*.png for visual review against design v1. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PlayerRenderTest {

    @Test @Config(sdk = 35, qualifiers = "w412dp-h915dp-night-xhdpi")
    public void renderDark() throws Exception { render("dark"); }

    @Test @Config(sdk = 35, qualifiers = "w412dp-h915dp-notnight-xhdpi")
    public void renderLight() throws Exception { render("light"); }

    private void render(String theme) throws Exception {
        FragmentActivity a = Robolectric.buildActivity(WidgetGalleryActivity.class).setup().get();
        AppContainer c = ((com.mhrsdev.musicplayer.MusicPlayerApp) a.getApplication()).getContainer();
        androidx.lifecycle.LiveData<List<Track>> all = c.library.getAllTracks();
        all.observeForever(t -> { });
        idle();
        List<Track> tracks = all.getValue();
        if (tracks != null && !tracks.isEmpty()) c.player.play(tracks, 0, null);
        PlayerFragment f = PlayerFragment.newInstance();
        a.getSupportFragmentManager().beginTransaction().replace(android.R.id.content, f).commitNow();
        idle();
        snap(f.requireView(), "p2-player-" + theme);
        f.requireView().findViewById(R.id.player_lyrics_toggle).performClick();
        idle();
        snap(f.requireView(), "p2-player-lyrics-" + theme);
    }

    private static void idle() { ShadowLooper.idleMainLooper(3, java.util.concurrent.TimeUnit.SECONDS); }

    private static void snap(View v, String name) throws Exception {
        int w = 412 * 2, h = 915 * 2;
        for (int i = 0; i < 2; i++) {
            v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            v.layout(0, 0, w, h);
            idle();
        }
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(bmp));
        File dir = new File("../docs/screens");
        dir.mkdirs();
        File out = new File(dir, name + ".png");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        assertTrue(out.length() > 0);
    }
}
