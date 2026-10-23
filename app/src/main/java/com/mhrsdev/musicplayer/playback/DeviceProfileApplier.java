package com.mhrsdev.musicplayer.playback;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.OutputDevice;
import com.mhrsdev.musicplayer.domain.model.SoundProfile;
import com.mhrsdev.musicplayer.domain.repository.AudioEffectsController;
import com.mhrsdev.musicplayer.domain.repository.OutputDevices;
import com.mhrsdev.musicplayer.domain.repository.PlayerController;
import java.util.List;

/**
 * Applies a device's saved {@link SoundProfile} when that device becomes the active output. It listens to the
 * output list only while at least one profile exists (the list's system callback is otherwise never registered),
 * and only reacts to a change of the active device, so a profile never overrides the user's tweaks mid-session.
 * Main thread only.
 */
public class DeviceProfileApplier {
    private final OutputDevices outputs;
    private final DeviceProfiles profiles;
    private final AudioEffectsController effects;
    private final PlayerController player;
    private boolean listening;
    @Nullable private String lastActiveKey;

    public DeviceProfileApplier(OutputDevices outputs, DeviceProfiles profiles, AudioEffectsController effects,
            PlayerController player) {
        this.outputs = outputs;
        this.profiles = profiles;
        this.effects = effects;
        this.player = player;
    }

    /** Call at start-up and after saving a profile: begins listening if there is anything to apply. */
    public void startIfNeeded() {
        if (listening || profiles.count() == 0) return;
        listening = true;
        outputs.getDevices().observeForever(this::onDevices);
    }

    private void onDevices(List<OutputDevice> devices) {
        String active = null;
        for (OutputDevice d : devices) if (d.active()) active = d.key();
        if (active == null || active.equals(lastActiveKey)) return;
        lastActiveKey = active;
        SoundProfile p = profiles.get(active);
        if (p != null) apply(p);
    }

    void apply(SoundProfile p) {
        effects.setEqualizerEnabled(p.eqEnabled());
        if (p.preset() >= 0) {
            effects.selectPreset(p.preset());
        } else {
            for (int i = 0; i < p.bandLevelsMb().size(); i++) effects.setBandLevel(i, p.bandLevelsMb().get(i));
        }
        effects.setBassBoost(p.bass());
        effects.setVirtualizer(p.virtualizer());
        effects.setLoudnessGain(p.loudnessMb());
        player.setPlaybackSpeed(p.speed());
    }
}
