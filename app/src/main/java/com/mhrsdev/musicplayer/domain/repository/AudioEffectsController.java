package com.mhrsdev.musicplayer.domain.repository;

import androidx.lifecycle.LiveData;
import com.mhrsdev.musicplayer.domain.model.SoundState;

/**
 * Equalizer, bass boost, virtualizer and loudness gain on the playback output. Main thread only; settings are
 * persisted and re-applied by the implementation. Speed / skip-silence / ReplayGain live on {@link PlayerController}.
 */
public interface AudioEffectsController {

    /** Current snapshot; observing it makes the real implementation probe the device's effects. */
    LiveData<SoundState> getState();

    void setEqualizerEnabled(boolean enabled);

    /** Sets one band (clamped to the device's range); the preset becomes Custom. */
    void setBandLevel(int band, int levelMb);

    /** Selects a device preset by index into {@link SoundState#presets()}, or {@link SoundState#PRESET_CUSTOM}. */
    void selectPreset(int index);

    /** 0..{@link SoundState#MAX_STRENGTH}. */
    void setBassBoost(int strength);

    /** 0..{@link SoundState#MAX_STRENGTH}. */
    void setVirtualizer(int strength);

    /** 0..{@link SoundState#MAX_LOUDNESS_MB} mB of extra gain. */
    void setLoudnessGain(int gainMb);

    /** Equalizer flat and off, bass, virtualizer and loudness at 0. */
    void reset();
}
