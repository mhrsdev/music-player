package com.mhrsdev.musicplayer.data.backup;

import androidx.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/** Finds the local track id for a {@link TrackRef}: by {@link TrackRef#key()} first, then by stored path. */
public final class TrackMatcher {
    private final Map<String, String> byKey = new HashMap<>();
    private final Map<String, String> byPath = new HashMap<>();

    /** @param library local track id to its description */
    public TrackMatcher(Map<String, TrackRef> library) {
        for (Map.Entry<String, TrackRef> e : library.entrySet()) {
            byKey.putIfAbsent(e.getValue().key(), e.getKey());
            if (e.getValue().path() != null) byPath.putIfAbsent(e.getValue().path(), e.getKey());
        }
    }

    /** The local track id, or null when the track is not in this library. */
    @Nullable
    public String match(TrackRef ref) {
        String id = byKey.get(ref.key());
        if (id == null && ref.path() != null) id = byPath.get(ref.path());
        return id;
    }
}
