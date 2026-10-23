package com.mhrsdev.musicplayer.domain.model;

import java.util.Collections;
import java.util.List;

/**
 * Audio-effects snapshot. Hardware-dependent parts are flagged {@code *Supported}; {@code bands} stays empty until the
 * device's equalizer has been probed (it is probed while the sound screen is observed or effects are playing).
 * {@code presets} are the device's own; the user's "Custom" is not in the list but {@code preset == PRESET_CUSTOM}.
 * Levels are in millibel (mB), strengths 0..{@link #MAX_STRENGTH}. Immutable.
 */
public record SoundState(
        boolean equalizerSupported, boolean equalizerEnabled,
        List<Band> bands, int minLevelMb, int maxLevelMb,
        List<String> presets, int preset,
        boolean bassBoostSupported, int bassBoost,
        boolean virtualizerSupported, int virtualizer,
        boolean loudnessSupported, int loudnessGainMb) {

    /** One equalizer band: centre frequency in Hz and its current gain in mB. */
    public record Band(int centerHz, int levelMb) { }

    public static final int PRESET_CUSTOM = -1;
    public static final int MAX_STRENGTH = 1000;
    public static final int MAX_LOUDNESS_MB = 1000;

    /** Nothing probed yet: everything assumed supported, all off. */
    public static SoundState initial() {
        return new SoundState(true, false, Collections.emptyList(), 0, 0, Collections.emptyList(), PRESET_CUSTOM,
                true, 0, true, 0, true, 0);
    }

    public boolean anySupported() {
        return equalizerSupported || bassBoostSupported || virtualizerSupported || loudnessSupported;
    }
}
