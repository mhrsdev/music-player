package com.mhrsdev.musicplayer.data.waveform;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.FileOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class WaveformStoreTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static void roundTrip(WaveformStore store) {
        assertNull(store.get("a", "fp1"));
        store.put("a", "fp1", new byte[] {1, 2, 3});
        assertArrayEquals(new byte[] {1, 2, 3}, store.get("a", "fp1"));
        assertNull("changed fingerprint = stale", store.get("a", "fp2"));
        store.put("a", "fp2", new byte[] {9});
        assertArrayEquals(new byte[] {9}, store.get("a", "fp2"));
        assertNull(store.get("a", "fp1"));
        store.put("neg", "fp", new byte[0]);
        assertEquals(0, store.get("neg", "fp").length);
        assertNull(store.get("b", "fp2"));
    }

    @Test public void inMemory() {
        roundTrip(new InMemoryWaveformStore());
    }

    @Test public void fileBackedSurvivesANewInstance() throws Exception {
        File dir = new File(tmp.getRoot(), "wf");
        roundTrip(new FileWaveformStore(dir));
        assertArrayEquals(new byte[] {9}, new FileWaveformStore(dir).get("a", "fp2"));
    }

    @Test public void corruptFileIsAMissAndGetsOverwritten() throws Exception {
        File dir = tmp.newFolder("wf2");
        FileWaveformStore store = new FileWaveformStore(dir);
        store.put("a", "fp", new byte[] {1, 2, 3});
        File f = dir.listFiles()[0];
        try (FileOutputStream out = new FileOutputStream(f)) { out.write(new byte[] {0, 5, 1}); }
        assertNull(store.get("a", "fp"));
        store.put("a", "fp", new byte[] {4});
        assertArrayEquals(new byte[] {4}, store.get("a", "fp"));
    }
}
