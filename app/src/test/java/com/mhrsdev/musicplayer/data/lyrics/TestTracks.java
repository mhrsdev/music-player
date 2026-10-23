package com.mhrsdev.musicplayer.data.lyrics;

import com.mhrsdev.musicplayer.domain.model.Track;

final class TestTracks {
    private TestTracks() { }

    static Track track(String id, String title, String artist, String album, long durationMs, String mediaUri) {
        return new Track(id, title, "ar-" + artist, artist, "al-" + album, album, durationMs, null, 0, mediaUri,
                false, false, 0, 0, 0);
    }

    static Track queen() {
        return track("t1", "I Want To Break Free", "Queen", "The Works", 261_000, null);
    }
}
