package com.mhrsdev.musicplayer.ui.player;

import static org.junit.Assert.assertEquals;

import android.media.AudioDeviceInfo;

import org.junit.Test;

public class OutputRouteTest {
    @Test public void bluetoothBeatsWiredAndSpeaker() {
        int[] t = {AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP};
        assertEquals(2, OutputRoute.pick(t));
    }

    @Test public void wiredBeatsSpeakerAndEarpieceIsIgnored() {
        int[] t = {AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                AudioDeviceInfo.TYPE_WIRED_HEADSET};
        assertEquals(2, OutputRoute.pick(t));
        assertEquals(1, OutputRoute.pick(new int[]{AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER}));
        assertEquals(-1, OutputRoute.pick(new int[]{AudioDeviceInfo.TYPE_TELEPHONY}));
    }
}
