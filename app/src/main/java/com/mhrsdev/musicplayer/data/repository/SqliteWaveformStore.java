package com.mhrsdev.musicplayer.data.repository;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.data.db.DbHelper;
import com.mhrsdev.musicplayer.data.db.query.WaveformQueries;
import com.mhrsdev.musicplayer.data.waveform.WaveformMath;
import com.mhrsdev.musicplayer.data.waveform.WaveformStore;

/**
 * SQLite-backed {@link WaveformStore}: one 256-bucket master envelope per track (row key = track id +
 * {@link WaveformMath#MASTER_BUCKETS}). The blob codec is identical to {@code WaveformMath.encode}, so the bytes pass
 * through untouched; an empty blob is the negative cache. Validity is judged in SQL against the track's current
 * {@code modified_ms} (a rescan that sees an edited file invalidates the row), so the {@code fingerprint} argument is
 * not stored. Blocks on the database: background thread only.
 */
public final class SqliteWaveformStore implements WaveformStore {
    private final DbHelper helper;

    public SqliteWaveformStore(DbHelper helper) {
        this.helper = helper;
    }

    @Nullable @Override public byte[] get(String trackId, String fingerprint) {
        return new WaveformQueries(helper.getWritableDatabase()).get(trackId, WaveformMath.MASTER_BUCKETS);
    }

    /** Ignored if the track is not in the library (the SQL inserts only for known tracks). */
    @Override public void put(String trackId, String fingerprint, byte[] blob) {
        new WaveformQueries(helper.getWritableDatabase()).put(trackId, WaveformMath.MASTER_BUCKETS, blob);
    }
}
