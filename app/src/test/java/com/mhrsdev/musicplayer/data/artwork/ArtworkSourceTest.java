package com.mhrsdev.musicplayer.data.artwork;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.graphics.Bitmap;
import android.net.Uri;
import androidx.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The cover lookup chain, driven by a fake provider that serves picture bytes. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ArtworkSourceTest {
    private static final String ALBUM = "content://media/external/audio/albumart/9";
    private static final String AUDIO = "content://media/external/audio/media/5";

    private static byte[] png(int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, out);
        return out.toByteArray();
    }

    private static final class FakeBackend implements ArtworkSource.Backend {
        @Nullable byte[] legacy, embedded, folder;
        @Nullable Bitmap thumb;
        /** Only these audio URIs have the above art; null = all. */
        @Nullable String artOwner;
        List<ArtworkSource.TrackRef> tracks = new ArrayList<>();
        final List<String> calls = new ArrayList<>();

        boolean owns(Uri audio) { return artOwner == null || artOwner.equals(audio.toString()); }

        @Override public InputStream open(Uri uri) {
            calls.add("open");
            return legacy == null ? null : new ByteArrayInputStream(legacy);
        }
        @Override public Bitmap thumbnail(Uri audio, int sizePx) {
            calls.add("thumb " + audio.getLastPathSegment());
            return owns(audio) ? thumb : null;
        }
        @Override public byte[] embeddedPicture(Uri audio) {
            calls.add("embedded " + audio.getLastPathSegment());
            return owns(audio) ? embedded : null;
        }
        @Override public List<ArtworkSource.TrackRef> albumTracks(long albumId, int limit) {
            calls.add("album " + albumId);
            return tracks;
        }
        @Override public List<ArtworkSource.TrackRef> track(long audioId) {
            calls.add("track " + audioId);
            return tracks;
        }
        @Override public InputStream folderImage(String path) {
            calls.add("folder " + path);
            return folder == null ? null : new ByteArrayInputStream(folder);
        }
    }

    private static ArtworkSource.TrackRef ref(int id) {
        return new ArtworkSource.TrackRef(Uri.parse("content://media/external/audio/media/" + id), "/music/a/" + id + ".mp3");
    }

    private static Bitmap load(ArtworkSource s, String uri) {
        return s.load(uri, 50, Bitmap.Config.RGB_565);
    }

    @Test public void legacyAlbumArtUriIsUsedWhenItStillOpens() {
        FakeBackend f = new FakeBackend();
        f.legacy = png(100);
        assertNotNull(load(new ArtworkSource(f), ALBUM));
        assertEquals(Collections.singletonList("open"), f.calls.subList(0, 1));
    }

    @Test public void thumbnailThenEmbeddedThenFolderImage() {
        FakeBackend f = new FakeBackend();
        f.tracks = Arrays.asList(ref(1));
        f.thumb = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        Bitmap viaThumb = load(new ArtworkSource(f), ALBUM);
        assertEquals(Bitmap.Config.RGB_565, viaThumb.getConfig()); // converted: no alpha needed

        f = new FakeBackend();
        f.tracks = Arrays.asList(ref(1));
        f.embedded = png(200);
        Bitmap viaEmbedded = load(new ArtworkSource(f), ALBUM);
        assertEquals(50, viaEmbedded.getWidth()); // 200 px picture sampled down by 4
        assertEquals("embedded 1", f.calls.get(f.calls.indexOf("thumb 1") + 1));

        f = new FakeBackend();
        f.tracks = Arrays.asList(ref(1));
        f.folder = png(100);
        assertNotNull(load(new ArtworkSource(f), ALBUM));
        assertEquals(true, f.calls.contains("folder /music/a/1.mp3"));
    }

    @Test public void albumUsesFirstTrackThatHasArtAndRemembersIt() {
        FakeBackend f = new FakeBackend();
        f.tracks = Arrays.asList(ref(1), ref(2), ref(3));
        f.embedded = png(100);
        f.artOwner = "content://media/external/audio/media/2";
        ArtworkSource s = new ArtworkSource(f);
        assertNotNull(load(s, ALBUM));
        f.calls.clear();
        assertNotNull(s.load(ALBUM, 100, Bitmap.Config.RGB_565)); // another size, e.g. the player cover
        assertEquals(false, f.calls.contains("album 9")); // winner reused, MediaStore not queried again
        assertEquals(false, f.calls.contains("thumb 1"));
    }

    @Test public void untaggedAlbumUsesTheAudioUriItself() {
        FakeBackend f = new FakeBackend();
        f.tracks = Arrays.asList(ref(5));
        f.embedded = png(100);
        assertNotNull(load(new ArtworkSource(f), AUDIO));
        assertEquals(true, f.calls.contains("track 5"));
    }

    @Test public void missesAreRememberedUntilForgotten() {
        FakeBackend f = new FakeBackend();
        f.tracks = Arrays.asList(ref(1));
        ArtworkSource s = new ArtworkSource(f);
        assertNull(load(s, ALBUM));
        int calls = f.calls.size();
        assertNull(load(s, ALBUM));
        assertEquals("a known miss costs nothing", calls, f.calls.size());
        f.embedded = png(100);
        s.forgetMisses();
        assertNotNull(load(s, ALBUM));
    }

    @Test public void corruptPictureFallsThroughToNextSource() {
        FakeBackend f = new FakeBackend();
        f.tracks = Arrays.asList(ref(1));
        f.embedded = new byte[] {1, 2, 3};
        f.folder = png(100);
        assertNotNull(load(new ArtworkSource(f), ALBUM));
    }
}
