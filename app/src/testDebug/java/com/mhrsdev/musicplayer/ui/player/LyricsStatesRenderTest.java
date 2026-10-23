package com.mhrsdev.musicplayer.ui.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;

import com.mhrsdev.musicplayer.MusicPlayerApp;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.domain.model.Lyrics;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.repository.LyricsRepository;
import com.mhrsdev.musicplayer.fake.SampleData;
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

/** L2 lyrics states (docs/screens/l2-*.png): No lyrics found, Finding lyrics…, failed with Retry; dark/light/fa. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LyricsStatesRenderTest {
    private static final String DARK = "w412dp-h915dp-night-xhdpi", LIGHT = "w412dp-h915dp-notnight-xhdpi";

    @Test @Config(sdk = 35, qualifiers = DARK)
    public void notFoundDark() throws Exception { state("l2-notfound-dark", t -> new MutableLiveData<>(Lyrics.none(t.id())), R.id.lyrics_search_again); }

    @Test @Config(sdk = 35, qualifiers = LIGHT)
    public void notFoundLight() throws Exception { state("l2-notfound-light", t -> new MutableLiveData<>(Lyrics.none(t.id())), R.id.lyrics_search_again); }

    @Test @Config(sdk = 35, qualifiers = "fa-ldrtl-" + DARK)
    public void notFoundFa() throws Exception { state("l2-notfound-fa-dark", t -> new MutableLiveData<>(Lyrics.none(t.id())), R.id.lyrics_search_again); }

    @Test @Config(sdk = 35, qualifiers = DARK)
    public void loadingDark() throws Exception { state("l2-loading-dark", t -> new MutableLiveData<>(), 0); }

    @Test @Config(sdk = 35, qualifiers = DARK)
    public void failedDark() throws Exception { state("l2-failed-dark", t -> new MutableLiveData<>(Lyrics.failed(t.id())), R.id.lyrics_retry); }

    @Test @Config(sdk = 35, qualifiers = DARK)
    public void offlineDark() throws Exception {
        offline = true;
        state("l2-offline-dark", t -> new MutableLiveData<>(Lyrics.failed(t.id())), R.id.lyrics_retry);
    }

    @Test @Config(sdk = 35, qualifiers = "fa-ldrtl-" + LIGHT)
    public void failedFaLight() throws Exception { state("l2-failed-fa-light", t -> new MutableLiveData<>(Lyrics.failed(t.id())), R.id.lyrics_retry); }

    interface Answer { LiveData<Lyrics> of(Track t); }

    private boolean offline;

    /** Robolectric's default network has no INTERNET capability: give it one unless the test wants offline. */
    private void network(FragmentActivity a) {
        android.net.ConnectivityManager cm = a.getSystemService(android.net.ConnectivityManager.class);
        android.net.NetworkCapabilities caps = org.robolectric.shadows.ShadowNetworkCapabilities.newInstance();
        if (!offline) {
            org.robolectric.shadows.ShadowNetworkCapabilities sc = org.robolectric.shadow.api.Shadow.extract(caps);
            sc.addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }
        org.robolectric.shadows.ShadowConnectivityManager scm = org.robolectric.shadow.api.Shadow.extract(cm);
        scm.setNetworkCapabilities(cm.getActiveNetwork(), caps);
    }

    private void state(String name, Answer answer, int visibleChip) throws Exception {
        FragmentActivity a = Robolectric.buildActivity(WidgetGalleryActivity.class).setup().get();
        network(a);
        MusicPlayerApp app = (MusicPlayerApp) a.getApplication();
        AppContainer fakes = new AppContainer(app, true);
        java.lang.reflect.Field lyr = AppContainer.class.getDeclaredField("lyrics");
        lyr.setAccessible(true);
        lyr.set(fakes, (LyricsRepository) answer::of);
        java.lang.reflect.Field f = MusicPlayerApp.class.getDeclaredField("container");
        f.setAccessible(true);
        f.set(app, fakes);
        fakes.player.play(SampleData.TRACKS, 3, null);
        fakes.player.pause();
        idle();
        PlayerFragment pf = PlayerFragment.newInstance();
        a.getSupportFragmentManager().beginTransaction().replace(android.R.id.content, pf).commitNow();
        idle();
        new ViewModelProvider(pf).get(PlayerViewModel.class).openLyricsTools();
        idle();
        View content = a.findViewById(android.R.id.content);
        if (visibleChip != 0) assertEquals(View.VISIBLE, a.findViewById(visibleChip).getVisibility());
        assertEquals(View.GONE, a.findViewById(R.id.lyrics_back_to_current).getVisibility());
        snap(content, name);
    }

    private static void idle() { ShadowLooper.idleMainLooper(3, java.util.concurrent.TimeUnit.SECONDS); }

    private static void snap(View v, String name) throws Exception {
        int w = 824, h = 1830;
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
