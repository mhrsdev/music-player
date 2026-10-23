package com.mhrsdev.musicplayer.data.waveform;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.util.function.BooleanSupplier;

/** Produces the master envelope of a track. Blocking; called on the extraction thread only. */
public interface EnvelopeSource {
    /**
     * @param cancelled polled often; return null promptly once it turns true
     * @return {@link WaveformMath#MASTER_BUCKETS} values in [0, 1], or null when the track cannot be decoded or the
     *         work was cancelled
     */
    @Nullable float[] extract(Track track, BooleanSupplier cancelled);
}
