package com.mhrsdev.musicplayer.data.lyrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Non-persistent {@link LyricsCacheStore} for tests and previews. */
public final class InMemoryLyricsCacheStore implements LyricsCacheStore {
    private final Map<String, Entry> map = new ConcurrentHashMap<>();

    @Override public Entry get(String trackId) {
        return map.get(trackId);
    }

    @Override public void put(String trackId, Entry entry) {
        map.put(trackId, entry);
    }
}
