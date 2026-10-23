package com.mhrsdev.musicplayer.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.mhrsdev.musicplayer.domain.model.Track;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class PlaybackStoreTest {
    private static Track track(String id, String title, String artwork) {
        return new Track(id, title, "ar1", "Artist \"Q\"", "al1", "Album", 215_000L, artwork, 7,
                "content://media/external/audio/media/" + id, true, true, 2021, 3, 1_700_000_000_000L);
    }

    @Test public void queueSurvivesJsonRoundTripIncludingNastyText() {
        List<Track> tracks = Arrays.asList(track("1", "سهراب \"نوری\" \n\\ ü", "content://art/1"), track("2", "Plain", null));
        assertEquals(tracks, PlaybackStore.parseQueue(PlaybackStore.toJson(tracks)));
    }

    @Test public void corruptOrMissingDataGivesEmptyQueue() {
        assertTrue(PlaybackStore.parseQueue(null).isEmpty());
        assertTrue(PlaybackStore.parseQueue("{not json").isEmpty());
        assertTrue(PlaybackStore.parseQueue("[{\"title\":\"no id\"}]").isEmpty());
    }

    @Test public void tracksWithoutPlayableUriAreDropped() {
        Track noUri = new Track("x", "t", "a", "a", "b", "b", 1L, null, 0, null, false, false, 0, 0, 0L);
        assertTrue(PlaybackStore.parseQueue(PlaybackStore.toJson(Arrays.asList(noUri))).isEmpty());
    }
}
