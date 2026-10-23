package com.mhrsdev.musicplayer.domain.model;

import java.util.List;

/**
 * The sound of one output device: equalizer (on/off, device preset or the custom band levels in mB), bass boost,
 * virtualizer, loudness gain and playback speed. Immutable.
 */
public record SoundProfile(boolean eqEnabled, int preset, List<Integer> bandLevelsMb, int bass, int virtualizer,
        int loudnessMb, float speed) {

    /** Snapshot of what is playing now. */
    public static SoundProfile of(SoundState s, PlaybackOptions o) {
        List<Integer> levels = s.bands().stream().map(SoundState.Band::levelMb).toList();
        return new SoundProfile(s.equalizerEnabled(), s.preset(), levels, s.bassBoost(), s.virtualizer(),
                s.loudnessGainMb(), o.speed());
    }
}
