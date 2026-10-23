package com.mhrsdev.musicplayer.data.waveform;

import androidx.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/** Non-persistent {@link WaveformStore} for tests and previews. */
public final class InMemoryWaveformStore implements WaveformStore {
    private record Entry(String fingerprint, byte[] blob) { }

    private final Map<String, Entry> entries = new HashMap<>();

    @Override public synchronized @Nullable byte[] get(String trackId, String fingerprint) {
        Entry e = entries.get(trackId);
        return e != null && e.fingerprint().equals(fingerprint) ? e.blob() : null;
    }

    @Override public synchronized void put(String trackId, String fingerprint, byte[] blob) {
        entries.put(trackId, new Entry(fingerprint, blob));
    }
}
