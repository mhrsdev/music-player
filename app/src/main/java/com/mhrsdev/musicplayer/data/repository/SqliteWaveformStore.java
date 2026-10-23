package com.mhrsdev.musicplayer.data.repository;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.data.db.DbHelper;
import com.mhrsdev.musicplayer.data.db.query.WaveformQueries;
import com.mhrsdev.musicplayer.domain.model.Track;

/**
 * Storage side of {@code WaveformRepository}: the waveform task's extractor reads the cache here first and stores what
 * it computed. Both methods block on the database: call them from a background thread. A stored envelope is valid only
 * while the audio file's modified time is unchanged, so edited files are re-extracted automatically.
 */
public final class SqliteWaveformStore {
    private final DbHelper helper;

    public SqliteWaveformStore(DbHelper helper) {
        this.helper = helper;
    }

    /** The cached envelope with {@code bars} values in [0, 1], or null (never extracted, file changed, or track unknown). */
    @Nullable public float[] get(Track track, int bars) {
        byte[] data = new WaveformQueries(helper.getWritableDatabase()).get(track.id(), bars);
        return data == null ? null : WaveformCodec.decode(data);
    }

    /** Stores {@code values} (clamped to [0, 1]) for the track. Ignored if the track is not in the library. */
    public void put(Track track, int bars, float[] values) {
        new WaveformQueries(helper.getWritableDatabase()).put(track.id(), bars, WaveformCodec.encode(values));
    }
}
