package com.mhrsdev.musicplayer;

import static org.junit.Assert.assertTrue;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import com.mhrsdev.musicplayer.domain.model.Playlist;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.ui.library.SortOrder;
import com.mhrsdev.musicplayer.ui.library.SortSheet;
import com.mhrsdev.musicplayer.ui.library.TrackMenuSheet;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator;
import com.mhrsdev.musicplayer.ui.theme.Tokens;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

/** Debug-only renders of the U2 library screens (fake data) to docs/screens/u2-*.png. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(application = FakeRenderApp.class)
public class U2RenderTest {
    private static final String DARK = "w412dp-h915dp-night-xhdpi";
    private static final String LIGHT = "w412dp-h915dp-notnight-xhdpi";
    private static final int W = 412 * 2, H = 915 * 2;

    @Test @Config(sdk = 35, qualifiers = DARK)
    public void playlistDetail() throws Exception {
        MainActivity a = start();
        a.openTrackList(ScreenNavigator.TrackList.PLAYLIST, firstPlaylist().id());
        settle();
        save(a, null, "playlist-detail-dark");
    }

    @Test @Config(sdk = 35, qualifiers = LIGHT)
    public void playlistDetailLight() throws Exception {
        MainActivity a = start();
        a.openTrackList(ScreenNavigator.TrackList.PLAYLIST, firstPlaylist().id());
        settle();
        save(a, null, "playlist-detail-light");
    }

    @Test @Config(sdk = 35, qualifiers = DARK)
    public void selectionMode() throws Exception {
        MainActivity a = start();
        a.openTrackList(ScreenNavigator.TrackList.SONGS, null);
        settle();
        layout(a);
        RecyclerView list = a.findViewById(R.id.list);
        list.getChildAt(2).performLongClick();
        settle();
        list.getChildAt(3).performClick();
        list.getChildAt(5).performClick();
        settle();
        save(a, null, "selection-dark");
    }

    @Test @Config(sdk = 35, qualifiers = "fa-" + DARK)
    public void selectionModeFa() throws Exception {
        MainActivity a = start();
        a.openTrackList(ScreenNavigator.TrackList.SONGS, null);
        settle();
        layout(a);
        RecyclerView list = a.findViewById(R.id.list);
        list.getChildAt(2).performLongClick();
        settle();
        save(a, null, "selection-fa-dark");
    }

    @Test @Config(sdk = 35, qualifiers = DARK)
    public void trackMenu() throws Exception {
        MainActivity a = start();
        a.openTrackList(ScreenNavigator.TrackList.PLAYLIST, firstPlaylist().id());
        settle();
        List<Track> tracks = observe(container(a).library.getPlaylistTracks(firstPlaylist().id()));
        TrackMenuSheet.show(a, tracks.get(0), firstPlaylist().id());
        settle();
        save(a, "track_menu", "track-menu-dark");
    }

    @Test @Config(sdk = 35, qualifiers = DARK)
    public void sortSheet() throws Exception {
        MainActivity a = start();
        a.openTrackList(ScreenNavigator.TrackList.SONGS, null);
        settle();
        SortSheet.show(a.getSupportFragmentManager(), new SortOrder(SortOrder.Field.TITLE, true), o -> { });
        settle();
        save(a, "sort_sheet", "sort-sheet-dark");
    }

    @Test @Config(sdk = 35, qualifiers = DARK)
    public void libraryHub() throws Exception {
        MainActivity a = start();
        a.openTab(ScreenNavigator.Tab.LIBRARY);
        settle();
        save(a, null, "library-hub-dark");
    }

    private static com.mhrsdev.musicplayer.di.AppContainer container(MainActivity a) {
        return ((MusicPlayerApp) a.getApplication()).getContainer();
    }

    private static Playlist firstPlaylist() {
        MusicPlayerApp app = (MusicPlayerApp) RuntimeEnvironment.getApplication();
        return observe(app.getContainer().library.getPlaylists()).get(0);
    }

    private static <T> T observe(androidx.lifecycle.LiveData<T> live) {
        live.observeForever(v -> { });
        return live.getValue();
    }

    private static MainActivity start() throws Exception {
        android.app.Application app = RuntimeEnvironment.getApplication();
        org.robolectric.shadows.ShadowApplication sa = org.robolectric.shadow.api.Shadow.extract(app);
        sa.grantPermissions(android.Manifest.permission.READ_MEDIA_AUDIO,
                android.Manifest.permission.READ_EXTERNAL_STORAGE);
        app.getSharedPreferences("nav", android.content.Context.MODE_PRIVATE).edit().putBoolean("perm_done", true).commit();
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        settle();
        return a;
    }

    private static void layout(MainActivity a) throws Exception {
        View root = a.getWindow().getDecorView();
        root.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, W, H);
        settle();
    }

    private static void save(MainActivity a, String dialogTag, String name) throws Exception {
        layout(a);
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Tokens.color(a, R.attr.mpBackground));
        a.getWindow().getDecorView().draw(c);
        if (dialogTag != null) {
            Fragment f = a.getSupportFragmentManager().findFragmentByTag(dialogTag);
            Dialog d = f instanceof DialogFragment df ? df.getDialog() : null;
            if (d != null && d.getWindow() != null) {
                c.drawColor(0x99000000);
                View sheet = f.requireView();
                sheet.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.AT_MOST));
                sheet.layout(0, 0, W, sheet.getMeasuredHeight());
                settle();
                c.save();
                c.translate(0, H - sheet.getMeasuredHeight());
                sheet.draw(c);
                c.restore();
            }
        }
        File dir = new File("../docs/screens");
        if (!dir.isDirectory()) dir = new File("build");
        File out = new File(dir, "u2-" + name + ".png");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        assertTrue(out.length() > 0);
    }

    private static void settle() throws InterruptedException {
        for (int i = 0; i < 6; i++) {
            ((org.robolectric.shadows.ShadowLooper) org.robolectric.shadow.api.Shadow.extract(Looper.getMainLooper())).idle();
            Thread.sleep(60);
        }
        ((org.robolectric.shadows.ShadowLooper) org.robolectric.shadow.api.Shadow.extract(Looper.getMainLooper()))
                .idleFor(java.time.Duration.ofMillis(500));
    }
}
