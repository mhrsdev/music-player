package com.mhrsdev.musicplayer.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.media.AudioDeviceInfo;
import com.mhrsdev.musicplayer.domain.model.OutputDevice;
import com.mhrsdev.musicplayer.domain.model.OutputDevice.Kind;
import com.mhrsdev.musicplayer.domain.model.SoundProfile;
import com.mhrsdev.musicplayer.playback.OutputDevicesImpl.Raw;
import java.util.List;
import org.junit.Test;

/** Pure parts of the output list and the per-device sound profile codec. */
public class OutputDevicesTest {
    private static final Raw SPEAKER = new Raw(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "", "Speaker");
    private static final Raw EARPIECE = new Raw(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, "", "Earpiece");
    private static final Raw BUDS = new Raw(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "AA:BB", "CMF Buds 2a");
    private static final Raw SCO = new Raw(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "AA:BB", "CMF Buds 2a");
    private static final Raw WIRED = new Raw(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, "", "Wired");

    @Test public void nonMusicOutputsAreDropped() {
        List<OutputDevice> l = OutputDevicesImpl.build(List.of(SPEAKER, EARPIECE, SCO), true, -1, null);
        assertEquals(1, l.size());
        assertEquals(Kind.SPEAKER, l.get(0).kind());
        assertTrue(l.get(0).active());
    }

    @Test public void withoutRoutingInfoBluetoothBeatsWiredBeatsSpeakerAndComesFirst() {
        List<OutputDevice> l = OutputDevicesImpl.build(List.of(SPEAKER, WIRED, BUDS), true, -1, null);
        assertEquals(List.of(Kind.BLUETOOTH, Kind.WIRED, Kind.SPEAKER), l.stream().map(OutputDevice::kind).toList());
        assertTrue(l.get(0).active());
        assertFalse(l.get(1).active());
        assertEquals("CMF Buds 2a", l.get(0).name());
        assertEquals("BLUETOOTH:AA:BB", l.get(0).key());
    }

    @Test public void systemRoutingWinsOverTheGuess() {
        List<OutputDevice> l = OutputDevicesImpl.build(List.of(SPEAKER, BUDS), true,
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "");
        assertEquals(Kind.SPEAKER, l.get(0).kind());
        assertTrue(l.get(0).active());
        assertFalse(l.get(1).active());
    }

    @Test public void bluetoothNameIsHiddenWithoutPermissionButKeyStaysStable() {
        OutputDevice d = OutputDevicesImpl.build(List.of(BUDS), false, -1, null).get(0);
        assertNull(d.name());
        assertTrue(d.nameNeedsPermission());
        assertEquals("BLUETOOTH:AA:BB", d.key());
    }

    @Test public void keyFallsBackToNameThenKind() {
        assertEquals("USB:Dock", OutputDevicesImpl.keyOf(Kind.USB, new Raw(0, "", "Dock"), "Dock"));
        assertEquals("WIRED", OutputDevicesImpl.keyOf(Kind.WIRED, new Raw(0, null, null), null));
    }

    @Test public void soundProfileSurvivesTheCodec() {
        SoundProfile p = new SoundProfile(true, -1, List.of(300, -100, 0, 150, 600), 400, 250, 120, 1.25f);
        assertEquals(p, DeviceProfiles.decode(DeviceProfiles.encode(p)));
        SoundProfile noBands = new SoundProfile(false, 2, List.of(), 0, 0, 0, 1f);
        assertEquals(noBands, DeviceProfiles.decode(DeviceProfiles.encode(noBands)));
    }

    @Test public void corruptProfileReadsAsNone() {
        assertNull(DeviceProfiles.decode(null));
        assertNull(DeviceProfiles.decode("1;x;;0;0;0;1"));
        assertNull(DeviceProfiles.decode("garbage"));
        assertNotNull(DeviceProfiles.decode("0;-1;;0;0;0;1.0"));
    }
}
