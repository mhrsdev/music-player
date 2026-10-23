package com.mhrsdev.musicplayer.ui.sound;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.mhrsdev.musicplayer.domain.model.PlaybackOptions;
import com.mhrsdev.musicplayer.domain.model.ReplayGainMode;
import com.mhrsdev.musicplayer.fake.FakeAudioEffects;
import com.mhrsdev.musicplayer.fake.FakePlayerController;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class SoundViewModelTest {
    @Rule public InstantTaskExecutorRule rule = new InstantTaskExecutorRule();

    private SoundViewModel vm;

    @Before public void setUp() {
        vm = new SoundViewModel(new FakeAudioEffects(), new FakePlayerController());
    }

    @Test public void editingTheEqualizerSwitchesItOn() {
        assertFalse(vm.getSound().getValue().equalizerEnabled());
        vm.setBand(2, 300);
        assertTrue(vm.getSound().getValue().equalizerEnabled());
        assertEquals(300, vm.getSound().getValue().bands().get(2).levelMb());
    }

    @Test public void presetSwitchesItOnToo() {
        vm.selectPreset(1);
        assertTrue(vm.getSound().getValue().equalizerEnabled());
        assertEquals(1, vm.getSound().getValue().preset());
    }

    @Test public void resetRestoresEverything() {
        vm.setBand(0, 500);
        vm.setBassBoost(800);
        vm.setSpeed(1.5f);
        vm.setSkipSilence(true);
        vm.setReplayGain(ReplayGainMode.TRACK);
        vm.reset();
        assertFalse(vm.getSound().getValue().equalizerEnabled());
        assertEquals(0, vm.getSound().getValue().bassBoost());
        assertEquals(PlaybackOptions.DEFAULT, vm.getOptions().getValue());
    }
}
