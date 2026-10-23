package com.mhrsdev.musicplayer.data.waveform;

import androidx.annotation.Nullable;

/**
 * Persistent cache of master envelopes: one compact blob per track ({@link WaveformMath#MASTER_BUCKETS} bytes, one per
 * bar). Implementations may block (disk / SQLite); the repository only calls them off the main thread.
 *
 * <p>The {@code fingerprint} (file size + modified time + duration) lets an implementation drop a stale blob after the
 * file changed: {@link #put} replaces whatever is stored for the track id (so a database can use track id as primary
 * key), {@link #get} returns null when nothing is stored OR the stored fingerprint differs.
 *
 * <p>An empty array means "this track cannot be decoded, do not try again" (negative cache).
 */
public interface WaveformStore {
    @Nullable byte[] get(String trackId, String fingerprint);

    void put(String trackId, String fingerprint, byte[] blob);
}
