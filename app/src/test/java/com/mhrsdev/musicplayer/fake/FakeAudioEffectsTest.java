package com.mhrsdev.musicplayer.fake;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.mhrsdev.musicplayer.domain.model.PlaybackOptions;
import com.mhrsdev.musicplayer.domain.model.ReplayGainMode;
import com.mhrsdev.musicplayer.domain.model.SoundState;
import org.junit.Rule;
import org.junit.Test;

public class FakeAudioEffectsTest {
    @Rule public InstantTaskExecutorRule rule = new InstantTaskExecutorRule();

    @Test public void bandEditSwitchesToCustomAndClamps() {
        FakeAudioEffects fx = new FakeAudioEffects();
        fx.selectPreset(2);
        assertEquals(2, fx.getState().getValue().preset());
        fx.setBandLevel(1, 99_999);
        SoundState s = fx.getState().getValue();
        assertEquals(SoundState.PRESET_CUSTOM, s.preset());
        assertEquals(s.maxLevelMb(), s.bands().get(1).levelMb());
        fx.reset();
        assertFalse(fx.getState().getValue().equalizerEnabled());
        assertEquals(0, fx.getState().getValue().bands().get(1).levelMb());
    }

    @Test public void fakePlayerOptionsSnapAndKeepOthers() {
        FakePlayerController p = new FakePlayerController();
        p.setPlaybackSpeed(1.2999f);
        p.setSkipSilence(true);
        p.setReplayGainMode(ReplayGainMode.ALBUM);
        PlaybackOptions o = p.getOptions().getValue();
        assertEquals(1.3f, o.speed(), 1e-4f);
        assertEquals(1f, o.pitch(), 0f);
        assertTrue(o.skipSilence());
        assertEquals(ReplayGainMode.ALBUM, o.replayGain());
    }

    @Test public void speedSnapsToStepWithinRange() {
        assertEquals(0.5f, PlaybackOptions.snap(0.1f), 1e-6f);
        assertEquals(2f, PlaybackOptions.snap(9f), 1e-6f);
        assertEquals(1.25f, PlaybackOptions.snap(1.26f), 1e-4f);
    }
}
