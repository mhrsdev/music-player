package com.mhrsdev.musicplayer.ui.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Looper;

import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Artwork fallback chain (Android 10+ albumart bug) against a fake provider. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
public class ArtworkResolverTest {

    private static byte[] png(int size, int color) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        b.eraseColor(color);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, out);
        return out.toByteArray();
    }

    /** Fake provider: nothing opens unless configured; counts calls. */
    static class FakeBackend implements ArtworkResolver.Backend {
        final Map<String, byte[]> embedded = new HashMap<>();
        final Map<String, byte[]> streams = new HashMap<>();
        final Map<String, List<ArtworkResolver.AudioRef>> albums = new HashMap<>();
        final Map<String, String> paths = new HashMap<>();
        final Set<String> files = new HashSet<>();
        final AtomicInteger embeddedCalls = new AtomicInteger();

        @Nullable @Override public Bitmap thumbnail(Uri uri, int sizePx) throws Exception {
            throw new FileNotFoundException("no thumbnail: " + uri); // like loadThumbnail without art
        }

        @Nullable @Override public InputStream open(Uri uri) throws Exception {
            byte[] b = streams.get(uri.toString());
            if (b == null) throw new FileNotFoundException(uri.toString());
            return new ByteArrayInputStream(b);
        }

        @Nullable @Override public byte[] embeddedPicture(Uri audioUri) {
            embeddedCalls.incrementAndGet();
            return embedded.get(audioUri.toString());
        }

        @Override public List<ArtworkResolver.AudioRef> albumTracks(String albumId, int limit) {
            List<ArtworkResolver.AudioRef> l = albums.get(albumId);
            return l == null ? new ArrayList<>() : l;
        }

        @Nullable @Override public ArtworkResolver.AudioRef audio(String audioId) {
            String uri = "content://media/external/audio/media/" + audioId;
            return new ArtworkResolver.AudioRef(Uri.parse(uri), paths.get(audioId));
        }

        @Override public boolean isFile(File f) { return files.contains(f.getPath()); }
    }

    private static ArtworkResolver.AudioRef ref(String id) {
        return new ArtworkResolver.AudioRef(Uri.parse("content://media/external/audio/media/" + id), null);
    }

    @Test public void albumArtFallsBackToFirstTrackWithEmbeddedPicture() {
        FakeBackend fb = new FakeBackend();
        fb.albums.put("7", List.of(ref("1"), ref("2"), ref("3")));
        fb.embedded.put("content://media/external/audio/media/2", png(800, Color.RED));
        Bitmap b = new ArtworkResolver(fb).resolve("content://media/external/audio/albumart/7", 200);
        assertNotNull(b);
        assertEquals(200, b.getWidth()); // 800 sampled by 4: shorter side stays >= 200
        assertEquals(Color.RED, b.getPixel(10, 10) | 0xFF000000);
        assertEquals(2, fb.embeddedCalls.get()); // stopped at the first track with art
    }

    /** Perf: the same cover asked at another size goes straight to the track that had the art (no probing of 1 and 2). */
    @Test public void secondSizeOfAnAlbumSkipsTheFailedProbes() {
        FakeBackend fb = new FakeBackend();
        fb.albums.put("7", List.of(ref("1"), ref("2"), ref("3")));
        fb.embedded.put("content://media/external/audio/media/3", png(800, Color.RED));
        ArtworkResolver r = new ArtworkResolver(fb);
        assertNotNull(r.resolve("content://media/external/audio/albumart/7", 200));
        assertEquals(3, fb.embeddedCalls.get());
        assertNotNull(r.resolve("content://media/external/audio/albumart/7", 100));
        assertEquals("only the winner was read again", 4, fb.embeddedCalls.get());
    }

    @Test public void legacyAlbumArtUriStillUsedWhenItOpens() {
        FakeBackend fb = new FakeBackend();
        fb.streams.put("content://media/external/audio/albumart/9", png(64, Color.BLUE));
        assertNotNull(new ArtworkResolver(fb).resolve("content://media/external/audio/albumart/9", 64));
        assertEquals(0, fb.embeddedCalls.get());
    }

    @Test public void trackFallsBackToFolderArt() {
        FakeBackend fb = new FakeBackend();
        fb.paths.put("5", "/music/album/05 song.mp3");
        fb.files.add("/music/album/folder.jpg");
        fb.streams.put(Uri.fromFile(new File("/music/album/folder.jpg")).toString(), png(300, Color.GREEN));
        Bitmap b = new ArtworkResolver(fb).resolve("content://media/external/audio/media/5", 300);
        assertNotNull(b);
        assertEquals(1, fb.embeddedCalls.get());
    }

    @Test public void nothingFoundIsNull() {
        FakeBackend fb = new FakeBackend();
        fb.albums.put("3", List.of(ref("8")));
        assertNull(new ArtworkResolver(fb).resolve("content://media/external/audio/albumart/3", 100));
        assertNull(new ArtworkResolver(fb).resolve("content://media/external/audio/media/8", 100));
        assertNull(new ArtworkResolver(fb).resolve("file:///nope.jpg", 100));
    }

    @Test public void mediaIdParsing() {
        assertEquals("12", ArtworkResolver.mediaId(Uri.parse("content://media/external/audio/albumart/12"), "albumart"));
        assertEquals("4", ArtworkResolver.mediaId(Uri.parse("content://media/external/audio/media/4"), "media"));
        assertNull(ArtworkResolver.mediaId(Uri.parse("content://media/external/audio/media/4"), "albumart"));
        assertNull(ArtworkResolver.mediaId(Uri.parse("content://other/external/audio/media/4"), "media"));
        assertNull(ArtworkResolver.mediaId(Uri.parse("content://media/external/audio/media/x1"), "media"));
    }

    @Test public void loaderCachesMissesAndDoesNotRetry() throws Exception {
        FakeBackend fb = new FakeBackend();
        ArtworkLoader loader = new ArtworkLoader(new ArtworkResolver(fb));
        String uri = "content://media/external/audio/media/42";
        Bitmap[] got = {Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)};
        loader.load(uri, 96, (k, b) -> got[0] = b);
        for (int i = 0; i < 200 && !loader.isKnownMiss(uri); i++) {
            Thread.sleep(10);
            ShadowLooper.shadowMainLooper().idle();
        }
        assertTrue(loader.isKnownMiss(uri));
        assertNull(got[0]);
        int calls = fb.embeddedCalls.get();
        boolean[] again = {false};
        loader.load(uri, 160, (k, b) -> again[0] = b == null); // other size, same URI: answered from the miss cache
        assertTrue(again[0]);
        assertEquals(calls, fb.embeddedCalls.get());
        assertEquals(Looper.getMainLooper(), Looper.myLooper());
    }
}
