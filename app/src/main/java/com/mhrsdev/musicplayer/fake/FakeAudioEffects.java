package com.mhrsdev.musicplayer.fake;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.mhrsdev.musicplayer.domain.model.SoundState;
import com.mhrsdev.musicplayer.domain.model.SoundState.Band;
import com.mhrsdev.musicplayer.domain.repository.AudioEffectsController;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** In-memory {@link AudioEffectsController}: a 5-band equalizer with the usual presets and no audio. Main thread only. */
public final class FakeAudioEffects implements AudioEffectsController {
    private static final int[] CENTERS_HZ = {60, 230, 910, 3600, 14000};
    private static final List<String> PRESETS = Arrays.asList("Normal", "Classical", "Dance", "Flat", "Folk",
            "Heavy Metal", "Hip Hop", "Jazz", "Pop", "Rock");
    private static final int[][] PRESET_LEVELS = {
            {300, 0, 0, 0, 300}, {500, 300, -200, 400, 400}, {600, 0, 200, 400, 100}, {0, 0, 0, 0, 0},
            {300, 0, 0, 200, -100}, {400, 100, 900, 300, 0}, {500, 300, 0, 100, 300}, {400, 200, -200, 200, 500},
            {-100, 200, 500, 100, -200}, {500, 300, -100, 300, 500}};

    private final MutableLiveData<SoundState> state = new MutableLiveData<>();

    public FakeAudioEffects() {
        state.setValue(build(false, new int[5], SoundState.PRESET_CUSTOM, 0, 0, 0));
    }

    private static SoundState build(boolean enabled, int[] levels, int preset, int bass, int virt, int loud) {
        List<Band> bands = new ArrayList<>();
        for (int i = 0; i < levels.length; i++) bands.add(new Band(CENTERS_HZ[i], levels[i]));
        return new SoundState(true, enabled, bands, -1500, 1500, PRESETS, preset, true, bass, true, virt, true, loud);
    }

    private int[] levels() {
        SoundState s = state.getValue();
        int[] out = new int[s.bands().size()];
        for (int i = 0; i < out.length; i++) out[i] = s.bands().get(i).levelMb();
        return out;
    }

    @Override public LiveData<SoundState> getState() {
        return state;
    }

    @Override public void setEqualizerEnabled(boolean enabled) {
        SoundState s = state.getValue();
        state.setValue(build(enabled, levels(), s.preset(), s.bassBoost(), s.virtualizer(), s.loudnessGainMb()));
    }

    @Override public void setBandLevel(int band, int levelMb) {
        SoundState s = state.getValue();
        int[] levels = levels();
        if (band < 0 || band >= levels.length) return;
        levels[band] = Math.max(s.minLevelMb(), Math.min(s.maxLevelMb(), levelMb));
        state.setValue(build(s.equalizerEnabled(), levels, SoundState.PRESET_CUSTOM, s.bassBoost(), s.virtualizer(),
                s.loudnessGainMb()));
    }

    @Override public void selectPreset(int index) {
        SoundState s = state.getValue();
        int[] levels = index >= 0 && index < PRESET_LEVELS.length ? PRESET_LEVELS[index].clone() : levels();
        state.setValue(build(s.equalizerEnabled(), levels, index < PRESET_LEVELS.length ? index : s.preset(),
                s.bassBoost(), s.virtualizer(), s.loudnessGainMb()));
    }

    @Override public void setBassBoost(int strength) {
        SoundState s = state.getValue();
        state.setValue(build(s.equalizerEnabled(), levels(), s.preset(), clamp(strength, SoundState.MAX_STRENGTH),
                s.virtualizer(), s.loudnessGainMb()));
    }

    @Override public void setVirtualizer(int strength) {
        SoundState s = state.getValue();
        state.setValue(build(s.equalizerEnabled(), levels(), s.preset(), s.bassBoost(),
                clamp(strength, SoundState.MAX_STRENGTH), s.loudnessGainMb()));
    }

    @Override public void setLoudnessGain(int gainMb) {
        SoundState s = state.getValue();
        state.setValue(build(s.equalizerEnabled(), levels(), s.preset(), s.bassBoost(), s.virtualizer(),
                clamp(gainMb, SoundState.MAX_LOUDNESS_MB)));
    }

    @Override public void reset() {
        state.setValue(build(false, new int[5], SoundState.PRESET_CUSTOM, 0, 0, 0));
    }

    private static int clamp(int v, int max) {
        return Math.max(0, Math.min(max, v));
    }
}
