package com.mhrsdev.musicplayer.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.MediaItem;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class PlaybackStoreTest {
    private static Track track(String id, String title, String artwork) {
        return new Track(id, title, "ar1", "Artist \"Q\"", "al1", "Album", 215_000L, artwork, 7,
                "content://media/external/audio/media/" + id, true, true, 2021, 3, 1_700_000_000_000L);
    }

    private static byte[] encode(List<Track> tracks) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PlaybackStore.encode(tracks, out);
        return out.toByteArray();
    }

    private static List<Track> decode(byte[] bytes) {
        return PlaybackStore.decode(new ByteArrayInputStream(bytes));
    }

    @Test public void queueSurvivesRoundTripIncludingNastyText() throws IOException {
        List<Track> tracks = Arrays.asList(track("1", "سهراب \"نوری\" \n\\ ü", "content://art/1"), track("2", "Plain", null));
        assertEquals(tracks, decode(encode(tracks)));
    }

    @Test public void corruptOrEmptyDataGivesEmptyQueue() throws IOException {
        assertTrue(decode(new byte[0]).isEmpty());
        assertTrue(decode("{not binary".getBytes()).isEmpty());
        byte[] good = encode(Arrays.asList(track("1", "t", null), track("2", "t", null)));
        assertTrue(decode(Arrays.copyOf(good, good.length - 5)).isEmpty()); // truncated write
    }

    @Test public void tracksWithoutPlayableUriAreDropped() throws IOException {
        Track noUri = new Track("x", "t", "a", "a", "b", "b", 1L, null, 0, null, false, false, 0, 0, 0L);
        assertTrue(decode(encode(Arrays.asList(noUri))).isEmpty());
    }

    /** F1 budget: a 10k-track queue must encode/decode in well under a frame budget per 1k tracks; prints the numbers. */
    @Test public void tenThousandTracksAreFastAndCompact() throws IOException {
        List<Track> tracks = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            tracks.add(new Track("id" + i, "Track number " + i, "ar" + i % 800, "Artist " + i % 800, "al" + i % 1000,
                    "Album " + i % 1000, 200_000L + i, "content://media/external/audio/albumart/" + i % 1000, i % 9,
                    "content://media/external/audio/media/" + i, false, i % 7 == 0, 2000 + i % 25, i % 15, 1_700_000_000_000L + i));
        }
        encode(tracks); decode(encode(tracks)); // warm up the JIT
        long t0 = System.nanoTime();
        byte[] bytes = encode(tracks);
        long t1 = System.nanoTime();
        List<Track> back = decode(bytes);
        long t2 = System.nanoTime();
        List<MediaItem> items = new ArrayList<>();
        for (Track t : tracks) items.add(TrackMapper.toMediaItem(t));
        long t3 = System.nanoTime();
        for (MediaItem item : items) TrackMapper.fromMediaItem(item);
        long t4 = System.nanoTime();
        System.out.printf("PERF queue 10k: encode %d ms, decode %d ms, size %d KB, toMediaItem %d ms, fromMediaItem %d ms%n",
                (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, bytes.length / 1024, (t3 - t2) / 1_000_000, (t4 - t3) / 1_000_000);
        assertEquals(tracks, back);
        assertTrue("encode " + (t1 - t0) / 1_000_000 + " ms", t1 - t0 < 500_000_000L);
        assertTrue("decode " + (t2 - t1) / 1_000_000 + " ms", t2 - t1 < 500_000_000L);
    }
}
