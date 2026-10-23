package com.mhrsdev.musicplayer.data.lyrics;

import androidx.annotation.Nullable;

/**
 * Persistent cache of network lyric lookups, keyed by track id. The data layer binds this to the {@code lyrics_cache}
 * SQLite table (via {@code LyricsCacheQueries}); {@link InMemoryLyricsCacheStore} serves tests. Called on a
 * background thread only. Expiry is judged by the caller from {@link Entry#expiresAtMs()}.
 */
public interface LyricsCacheStore {

    /** @param text raw LRC/plain text; null is a cached "no lyrics" (negative result) */
    record Entry(@Nullable String text, @Nullable String source, long expiresAtMs) { }

    @Nullable
    Entry get(String trackId);

    void put(String trackId, Entry entry);
}
