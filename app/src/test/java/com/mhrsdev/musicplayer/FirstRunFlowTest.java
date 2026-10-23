package com.mhrsdev.musicplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import androidx.lifecycle.Observer;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

/**
 * First-run flow against the real container with a fake MediaStore: the library must be scanned (and the tracks
 * appear through the repository's LiveData) both after the user grants access in the permission screen and when
 * access was already granted at launch.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class FirstRunFlowTest {

    /** One 3-minute song, for any MediaStore audio query. */
    public static final class OneSongProvider extends ContentProvider {
        @Override public boolean onCreate() { return true; }

        @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
            MatrixCursor c = new MatrixCursor(projection);
            c.addRow(new Object[] {42L, "Song", "song.mp3", "Artist", 7L, "Album", 9L, 180_000L, 1, 2020, 1000L, 2000L,
                    "/storage/emulated/0/Music/song.mp3"});
            return c;
        }

        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues v) { return null; }
        @Override public int delete(Uri uri, String s, String[] a) { return 0; }
        @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
    }

    private static MusicPlayerApp app() {
        return (MusicPlayerApp) RuntimeEnvironment.getApplication();
    }

    /** Reflection: compiling against ShadowApplication needs android-all on the compile classpath. */
    private static void grantAudio() throws Exception {
        Object shadow = Class.forName("org.robolectric.Shadows").getMethod("shadowOf", android.app.Application.class)
                .invoke(null, RuntimeEnvironment.getApplication());
        shadow.getClass().getMethod("grantPermissions", String[].class)
                .invoke(shadow, (Object) new String[] {Manifest.permission.READ_MEDIA_AUDIO});
    }

    private static void mediaStoreHasOneSong() {
        Robolectric.buildContentProvider(OneSongProvider.class).create("media");
    }

    /** Observes the library like Home/Library do and waits for the io thread to deliver the scanned track. */
    private static List<Track> awaitTracks() throws Exception {
        CountDownLatch seen = new CountDownLatch(1);
        List<Track>[] out = new List[1];
        Observer<List<Track>> o = tracks -> {
            if (!tracks.isEmpty()) { out[0] = tracks; seen.countDown(); }
        };
        app().getContainer().library.getAllTracks().observeForever(o);
        for (int i = 0; i < 100 && seen.getCount() > 0; i++) {
            ShadowLooper.idleMainLooper(); // delivers postValue
            seen.await(50, TimeUnit.MILLISECONDS);
        }
        app().getContainer().library.getAllTracks().removeObserver(o);
        return out[0];
    }

    @Test public void grantingInThePermissionScreenRunsTheFirstScan() throws Exception {
        mediaStoreHasOneSong();
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        assertFalse(app().getContainer().library == null);
        // The permission screen is showing; the user taps Allow and the system grants it.
        grantAudio();
        c.get().onPermissionFlowDone();

        List<Track> tracks = awaitTracks();
        assertTrue("library stayed empty after the grant", tracks != null);
        assertEquals("42", tracks.get(0).id());
    }

    @Test public void alreadyGrantedAtLaunchScansAFreshDatabase() throws Exception {
        mediaStoreHasOneSong();
        grantAudio();
        Robolectric.buildActivity(MainActivity.class).setup();

        List<Track> tracks = awaitTracks();
        assertTrue("never-scanned library must be scanned at start", tracks != null);
        assertEquals("Song", tracks.get(0).title());
    }
}
