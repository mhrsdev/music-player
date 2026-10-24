package com.mhrsdev.musicplayer.ui.sound;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.mhrsdev.musicplayer.domain.model.PlaybackOptions;
import com.mhrsdev.musicplayer.domain.model.ReplayGainMode;
import com.mhrsdev.musicplayer.domain.model.SoundState;
import com.mhrsdev.musicplayer.domain.repository.AudioEffectsController;
import com.mhrsdev.musicplayer.domain.repository.PlayerController;

/** Sound screen: forwards user intents to the effects and the player. Editing the equalizer switches it on. */
public class SoundViewModel extends ViewModel {
    /** Quick speed chips. */
    public static final float[] SPEED_CHIPS = {0.75f, 1f, 1.25f, 1.5f, 2f};

    private final AudioEffectsController effects;
    private final PlayerController player;

    SoundViewModel(AudioEffectsController effects, PlayerController player) {
        this.effects = effects;
        this.player = player;
    }

    public LiveData<SoundState> getSound() { return effects.getState(); }

    public LiveData<PlaybackOptions> getOptions() { return player.getOptions(); }

    public void setEqualizerEnabled(boolean on) { effects.setEqualizerEnabled(on); }

    public void setBand(int band, int levelMb) {
        enableEqualizer();
        effects.setBandLevel(band, levelMb);
    }

    /** {@code index} into {@link SoundState#presets()} or {@link SoundState#PRESET_CUSTOM}. */
    public void selectPreset(int index) {
        enableEqualizer();
        effects.selectPreset(index);
    }

    public void setBassBoost(int strength) { effects.setBassBoost(strength); }

    public void setVirtualizer(int strength) { effects.setVirtualizer(strength); }

    public void setLoudnessGain(int gainMb) { effects.setLoudnessGain(gainMb); }

    public void setSpeed(float speed) { player.setPlaybackSpeed(speed); }

    public void setSkipSilence(boolean on) { player.setSkipSilence(on); }

    public void setReplayGain(ReplayGainMode mode) { player.setReplayGainMode(mode); }

    /** Everything on this screen back to its default. */
    public void reset() {
        effects.reset();
        player.setPlaybackSpeed(1f);
        player.setPlaybackPitch(1f);
        player.setSkipSilence(false);
        player.setReplayGainMode(ReplayGainMode.OFF);
    }

    private void enableEqualizer() {
        SoundState s = effects.getState().getValue();
        if (s != null && !s.equalizerEnabled()) effects.setEqualizerEnabled(true);
    }

    public static ViewModelProvider.Factory factory(AudioEffectsController effects, PlayerController player) {
        return new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> c) {
                return c.cast(new SoundViewModel(effects, player));
            }
        };
    }
}
