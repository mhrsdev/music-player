package com.mhrsdev.musicplayer.ui.settings;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.file.Files;
import org.junit.Test;

public class DeviceSettingsViewModelTest {
    @Test public void sizeOfSumsNestedFiles() throws Exception {
        File dir = Files.createTempDirectory("mowj").toFile();
        File sub = new File(dir, "a");
        sub.mkdirs();
        Files.write(new File(dir, "x").toPath(), new byte[10]);
        Files.write(new File(sub, "y").toPath(), new byte[5]);
        assertEquals(15, DeviceSettingsViewModel.sizeOf(dir));
        assertEquals(0, DeviceSettingsViewModel.sizeOf(new File(dir, "missing")));
    }
}
