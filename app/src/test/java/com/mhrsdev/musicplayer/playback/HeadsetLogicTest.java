package com.mhrsdev.musicplayer.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.media.AudioDeviceInfo;
import com.mhrsdev.musicplayer.domain.model.HeadsetAction;
import com.mhrsdev.musicplayer.domain.model.HeadsetConfig;
import org.junit.Test;

/** Pure decisions behind the headset buttons: gesture mapping and the connect / interruption rules. */
public class HeadsetLogicTest {
    private static final HeadsetConfig D = HeadsetConfig.DEFAULT;

    @Test public void defaultsMapSingleDoubleTriple() {
        assertEquals(HeadsetAction.PLAY_PAUSE, D.forClicks(1));
        assertEquals(HeadsetAction.NEXT, D.forClicks(2));
        assertEquals(HeadsetAction.PREVIOUS, D.forClicks(3));
        assertEquals(HeadsetAction.PREVIOUS, D.forClicks(7)); // more than three counts as three
        assertEquals(HeadsetAction.NONE, D.forClicks(0));
        assertEquals(HeadsetAction.NONE, D.longPress());
        assertFalse(D.resumeOnConnect());
        assertTrue(D.pauseOnDisconnect());
        assertTrue(D.resumeAfterCall());
    }

    @Test public void detectorFiresAtOnceWhenNoLongerGestureIsMapped() {
        assertEquals(3, D.maxClicks());
        HeadsetConfig noTriple = D.withGesture(2, HeadsetAction.NONE);
        assertEquals(2, noTriple.maxClicks());
        assertEquals(1, noTriple.withGesture(1, HeadsetAction.NONE).maxClicks());
    }

    @Test public void withersChangeOnlyTheirField() {
        HeadsetConfig c = D.withGesture(3, HeadsetAction.SEEK_FORWARD).withResumeOnConnect(true)
                .withPauseOnDisconnect(false).withResumeAfterCall(false);
        assertEquals(HeadsetAction.SEEK_FORWARD, c.longPress());
        assertEquals(HeadsetAction.PLAY_PAUSE, c.single());
        assertTrue(c.resumeOnConnect());
        assertFalse(c.pauseOnDisconnect());
        assertFalse(c.resumeAfterCall());
    }

    @Test public void resumeOnConnectNeedsSettingQueuePauseByUnplugAndRecentness() {
        HeadsetConfig on = D.withResumeOnConnect(true);
        long t = 1_000_000L;
        assertTrue(HeadsetControls.shouldResumeOnConnect(on, t, t + 5_000, true, false));
        assertFalse("setting off", HeadsetControls.shouldResumeOnConnect(D, t, t + 5_000, true, false));
        assertFalse("user paused (no unplug)", HeadsetControls.shouldResumeOnConnect(on, -1L, t, true, false));
        assertFalse("already playing", HeadsetControls.shouldResumeOnConnect(on, t, t + 5_000, true, true));
        assertFalse("empty queue", HeadsetControls.shouldResumeOnConnect(on, t, t + 5_000, false, false));
        assertFalse("too long ago", HeadsetControls.shouldResumeOnConnect(on, t, t + HeadsetControls.RESUME_WINDOW_MS + 1, true, false));
    }

    @Test public void repauseOnlyWhenResumeAfterCallIsOff() {
        assertFalse(HeadsetControls.shouldRepause(D, true));
        assertTrue(HeadsetControls.shouldRepause(D.withResumeAfterCall(false), true));
        assertFalse(HeadsetControls.shouldRepause(D.withResumeAfterCall(false), false));
    }

    @Test public void onlyPluggedAndPairedOutputsCountAsHeadsets() {
        assertTrue(HeadsetControls.isHeadsetType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP));
        assertTrue(HeadsetControls.isHeadsetType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES));
        assertTrue(HeadsetControls.isHeadsetType(AudioDeviceInfo.TYPE_USB_HEADSET));
        assertFalse(HeadsetControls.isHeadsetType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER));
        assertFalse(HeadsetControls.isHeadsetType(AudioDeviceInfo.TYPE_HDMI));
    }
}
