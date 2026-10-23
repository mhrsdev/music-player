package com.mhrsdev.musicplayer.fake;

import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.repository.Callback;
import com.mhrsdev.musicplayer.domain.repository.MusicSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Searches {@link SampleData}; has no playable URIs. Callbacks run inline. */
public final class FakeMusicSource implements MusicSource {
    @Override public String getId() {
        return "fake";
    }

    @Override public String getDisplayName() {
        return "Sample catalog";
    }

    @Override public void search(String query, int limit, Callback<List<Track>> callback) {
        String q = query.toLowerCase(Locale.ROOT);
        List<Track> out = new ArrayList<>();
        for (Track t : SampleData.TRACKS) {
            if (out.size() >= limit) break;
            if (t.title().toLowerCase(Locale.ROOT).contains(q) || t.artistName().toLowerCase(Locale.ROOT).contains(q)) out.add(t);
        }
        callback.onResult(out);
    }

    @Override public void resolvePlayableUri(Track track, Callback<String> callback) {
        callback.onResult(null);
    }
}
