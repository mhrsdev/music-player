package com.mhrsdev.musicplayer.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The sound of one output device: equalizer (on/off, device preset or the custom band levels in mB), bass boost,
 * virtualizer, loudness gain and playback speed. Immutable.
 */
public record SoundProfile(boolean eqEnabled, int preset, List<Integer> bandLevelsMb, int bass, int virtualizer,
        int loudnessMb, float speed) {

    /** Snapshot of what is playing now. */
    public static SoundProfile of(SoundState s, PlaybackOptions o) {
        List<Integer> levels = new ArrayList<>(s.bands().size()); // Stream.toList() needs API 34
        for (SoundState.Band band : s.bands()) levels.add(band.levelMb());
        return new SoundProfile(s.equalizerEnabled(), s.preset(), levels, s.bassBoost(), s.virtualizer(),
                s.loudnessGainMb(), o.speed());
    }
}
