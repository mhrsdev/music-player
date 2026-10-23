package com.mhrsdev.musicplayer.domain.repository;

import com.mhrsdev.musicplayer.domain.model.Track;
import java.util.List;

/** Pluggable catalog provider (the local library now; a legal online provider later). Callbacks run on main. */
public interface MusicSource {

    String getId();

    String getDisplayName();

    /** Failures are reported as an empty list. */
    void search(String query, int limit, Callback<List<Track>> callback);

    /** Resolves a playable URI for {@code track}; the callback gets null if it can't be played from this source. */
    void resolvePlayableUri(Track track, Callback<String> callback);
}
