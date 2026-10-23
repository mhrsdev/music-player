package com.mhrsdev.musicplayer.ui.headset;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.mhrsdev.musicplayer.data.settings.SettingsStore;
import com.mhrsdev.musicplayer.domain.model.HeadsetAction;
import com.mhrsdev.musicplayer.domain.model.HeadsetConfig;
import com.mhrsdev.musicplayer.domain.model.OutputDevice;
import com.mhrsdev.musicplayer.domain.model.PlaybackOptions;
import com.mhrsdev.musicplayer.domain.model.SoundProfile;
import com.mhrsdev.musicplayer.domain.model.SoundState;
import com.mhrsdev.musicplayer.domain.repository.AudioEffectsController;
import com.mhrsdev.musicplayer.domain.repository.OutputDevices;
import com.mhrsdev.musicplayer.domain.repository.PlayerController;
import com.mhrsdev.musicplayer.playback.DeviceProfiles;
import java.util.List;

/** Headset screen: button mapping, connection behaviour, connected outputs, volume and the per-device sound. */
public class HeadsetViewModel extends ViewModel {
    private final SettingsStore settings;
    private final OutputDevices outputs;
    private final DeviceProfiles profiles;
    private final AudioEffectsController effects;
    private final PlayerController player;
    private final Runnable profilesChanged;
    private final MutableLiveData<Integer> profileRevision = new MutableLiveData<>(0);

    HeadsetViewModel(SettingsStore settings, OutputDevices outputs, DeviceProfiles profiles,
            AudioEffectsController effects, PlayerController player, Runnable profilesChanged) {
        this.settings = settings;
        this.outputs = outputs;
        this.profiles = profiles;
        this.effects = effects;
        this.player = player;
        this.profilesChanged = profilesChanged;
    }

    public LiveData<HeadsetConfig> getConfig() { return settings.headset(); }

    public LiveData<List<OutputDevice>> getDevices() { return outputs.getDevices(); }

    /** Observing it makes the equalizer probe the device, so a saved profile has real band levels. */
    public LiveData<SoundState> getSound() { return effects.getState(); }

    /** Bumps when a profile is saved or removed, so rows redraw their "saved" state. */
    public LiveData<Integer> getProfileRevision() { return profileRevision; }

    public void setGesture(int index, HeadsetAction action) {
        settings.setHeadset(current().withGesture(index, action));
    }

    public void setResumeOnConnect(boolean on) { settings.setHeadset(current().withResumeOnConnect(on)); }

    public void setPauseOnDisconnect(boolean on) { settings.setHeadset(current().withPauseOnDisconnect(on)); }

    public void setResumeAfterCall(boolean on) { settings.setHeadset(current().withResumeAfterCall(on)); }

    private HeadsetConfig current() {
        HeadsetConfig c = settings.headset().getValue();
        return c == null ? HeadsetConfig.DEFAULT : c;
    }

    public int getVolume() { return outputs.getVolume(); }

    public int getMaxVolume() { return outputs.getMaxVolume(); }

    public void setVolume(int step) { outputs.setVolume(step); }

    public void refreshDevices() { outputs.refresh(); }

    public boolean hasProfile(OutputDevice d) { return profiles.has(d.key()); }

    /** Stores the sound that is playing now for {@code device}; it is applied whenever that device becomes the output. */
    public void saveSound(OutputDevice device, @Nullable String displayName) {
        SoundState sound = effects.getState().getValue();
        PlaybackOptions options = player.getOptions().getValue();
        if (sound == null || options == null) return;
        profiles.save(device.key(), displayName, SoundProfile.of(sound, options));
        profilesChanged.run();
        profileRevision.setValue(profileRevision.getValue() + 1);
    }

    public void removeSound(OutputDevice device) {
        profiles.remove(device.key());
        profileRevision.setValue(profileRevision.getValue() + 1);
    }

    public static ViewModelProvider.Factory factory(SettingsStore settings, OutputDevices outputs, DeviceProfiles profiles,
            AudioEffectsController effects, PlayerController player, Runnable profilesChanged) {
        return new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                return modelClass.cast(new HeadsetViewModel(settings, outputs, profiles, effects, player, profilesChanged));
            }
        };
    }
}
