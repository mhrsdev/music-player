package com.mhrsdev.musicplayer.ui.player;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.mhrsdev.musicplayer.MusicPlayerApp;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.fake.SampleData;
import com.mhrsdev.musicplayer.ui.queue.QueueFragment;
import com.mhrsdev.musicplayer.ui.settings.PrefsSettingsStore;
import com.mhrsdev.musicplayer.ui.settings.SettingsStore;
import com.mhrsdev.musicplayer.ui.widget.WidgetGalleryActivity;

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
 * Renders the full player with a LOADED track (fake container: queue, synced lyrics, waveform) to
 * docs/screens/u3-*.png for visual review against design v1 (05/06/07/11/15).
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PlayerRenderTest {
    private static final String DARK = "w412dp-h915dp-night-xhdpi", LIGHT = "w412dp-h915dp-notnight-xhdpi";

    @Test @Config(sdk = 35, qualifiers = DARK)
    public void dark() throws Exception {
        FragmentActivity a = host(SettingsStore.SeekStyle.WAVEFORM, 1);
        PlayerFragment f = player(a);
        snap(content(a), "u3-player-cover-waveform-dark");
        f.requireView().findViewById(R.id.player_lyrics_toggle).performClick();
        idle();
        snap(content(a), "u3-player-lyrics-dark");
        overlay(a, new QueueFragment());
        snap(content(a), "u3-queue-dark");
        overlay(a, new com.mhrsdev.musicplayer.ui.queue.SleepTimerSheet());
        snap(content(a), "u3-sleep-dark");
    }

    @Test @Config(sdk = 35, qualifiers = DARK)
    public void darkBars() throws Exception {
        FragmentActivity a = host(SettingsStore.SeekStyle.BARS, 1);
        player(a);
        snap(content(a), "u3-player-cover-bars-dark");
    }

    @Test @Config(sdk = 35, qualifiers = LIGHT)
    public void light() throws Exception {
        FragmentActivity a = host(SettingsStore.SeekStyle.WAVY, 1);
        PlayerFragment f = player(a);
        snap(content(a), "u3-player-cover-wavy-light");
        f.requireView().findViewById(R.id.player_lyrics_toggle).performClick();
        idle();
        snap(content(a), "u3-player-lyrics-light");
        overlay(a, new QueueFragment());
        snap(content(a), "u3-queue-light");
        overlay(a, new com.mhrsdev.musicplayer.ui.queue.SleepTimerSheet());
        snap(content(a), "u3-sleep-light");
    }

    @Test @Config(sdk = 35, qualifiers = "fa-ldrtl-" + DARK)
    public void persianRtl() throws Exception {
        FragmentActivity a = host(SettingsStore.SeekStyle.BARS, 33); // track-34: Persian, synced lyrics
        PlayerFragment f = player(a);
        snap(content(a), "u3-player-cover-fa-dark");
        f.requireView().findViewById(R.id.player_lyrics_toggle).performClick();
        idle();
        snap(content(a), "u3-player-lyrics-fa-dark");
        overlay(a, new QueueFragment());
        snap(content(a), "u3-queue-fa-dark");
        overlay(a, new com.mhrsdev.musicplayer.ui.queue.SleepTimerSheet());
        snap(content(a), "u3-sleep-fa-dark");
    }

    @Test @Config(sdk = 35, qualifiers = DARK, fontScale = 1.3f)
    public void bigFont() throws Exception {
        FragmentActivity a = host(SettingsStore.SeekStyle.WAVY, 5);
        PlayerFragment f = player(a);
        snap(content(a), "u3-player-cover-font130-dark");
        f.requireView().findViewById(R.id.player_lyrics_toggle).performClick();
        idle();
        snap(content(a), "u3-player-lyrics-font130-dark");
    }

    private static final com.mhrsdev.musicplayer.domain.model.Track LONG_EN = new com.mhrsdev.musicplayer.domain.model.Track(
            "long-en", "Concerto pour piano no. 2 en ut mineur, op. 18: II. Adagio sostenuto (Piano et orchestre)",
            "a-x", "Orchestre philharmonique de Radio France, Mikko Franck", "al-x", "Rachmaninov", 683_000L, null, 77,
            null, false, false, 2020, 2, 0L);
    private static final com.mhrsdev.musicplayer.domain.model.Track LONG_FA = new com.mhrsdev.musicplayer.domain.model.Track(
            "long-fa", "آهنگ خیلی بلند با نامی طولانی برای آزمایش نمایش عنوان در پخش‌کننده‌ی موج", "a-y",
            "گروه موسیقی سهراب نوری و دوستان", "al-y", "آلبوم", 245_000L, null, 12, null, false, false, 2019, 1, 0L);

    @Test @Config(sdk = 35, qualifiers = "w360dp-h800dp-night-xhdpi", fontScale = 1.3f)
    public void longTitle360() throws Exception { longTitles("360", 360, 800); }

    @Test @Config(sdk = 35, qualifiers = "w411dp-h915dp-notnight-xhdpi", fontScale = 1.3f)
    public void longTitle411() throws Exception { longTitles("411", 411, 915); }

    @Test @Config(sdk = 35, qualifiers = "fa-ldrtl-w360dp-h800dp-night-xhdpi", fontScale = 1.3f)
    public void longTitleFa360() throws Exception { longTitles("fa-360", 360, 800); }

    private void longTitles(String tag, int wDp, int hDp) throws Exception {
        FragmentActivity a = host(SettingsStore.SeekStyle.WAVY, 0);
        AppContainer c = ((MusicPlayerApp) a.getApplication()).getContainer();
        c.player.play(java.util.List.of(LONG_EN, LONG_FA), 0, null);
        c.player.pause();
        idle();
        player(a);
        snap(content(a), "u3-title-en-" + tag, wDp, hDp);
        c.player.next();
        c.player.pause();
        idle();
        snap(content(a), "u3-title-fa-" + tag, wDp, hDp);
    }

    /** Host activity with a fake-backed container and a loaded queue starting at {@code index}. */
    private static FragmentActivity host(SettingsStore.SeekStyle style, int index) throws Exception {
        FragmentActivity a = Robolectric.buildActivity(WidgetGalleryActivity.class).setup().get();
        MusicPlayerApp app = (MusicPlayerApp) a.getApplication();
        AppContainer fakes = new AppContainer(app, true);
        java.lang.reflect.Field f = MusicPlayerApp.class.getDeclaredField("container");
        f.setAccessible(true);
        f.set(app, fakes);
        new PrefsSettingsStore(a).setSeekStyle(style);
        fakes.player.play(SampleData.TRACKS, index, "NEFFEX");
        fakes.player.pause();
        fakes.player.seekTo(52_000L);
        idle();
        return a;
    }

    private static PlayerFragment player(FragmentActivity a) {
        PlayerFragment f = PlayerFragment.newInstance();
        a.getSupportFragmentManager().beginTransaction().replace(android.R.id.content, f).commitNow();
        idle();
        return f;
    }

    /** Adds a (dialog) fragment inline over the player, bottom-aligned like the sheet. */
    static void overlay(FragmentActivity a, Fragment sheet) {
        a.getSupportFragmentManager().beginTransaction().add(android.R.id.content, sheet).commitNow();
        idle();
        View v = sheet.requireView();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        lp.gravity = Gravity.BOTTOM;
        v.setLayoutParams(lp);
    }

    private static View content(FragmentActivity a) { return a.findViewById(android.R.id.content); }

    private static void idle() { ShadowLooper.idleMainLooper(3, java.util.concurrent.TimeUnit.SECONDS); }

    private static void snap(View v, String name) throws Exception { snap(v, name, 412, 915); }

    private static void snap(View v, String name, int wDp, int hDp) throws Exception {
        int w = wDp * 2, h = hDp * 2;
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
