package com.mhrsdev.musicplayer.ui.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.mhrsdev.musicplayer.data.settings.BatterySaverMode;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The UI settings view and the data store are one source of truth. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class PrefsSettingsStoreTest {
    @Rule public InstantTaskExecutorRule instantTaskExecutor = new InstantTaskExecutorRule();

    @Test public void writesThroughToTheDataStoreAndBack() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit();
        com.mhrsdev.musicplayer.data.settings.SettingsStore data = new com.mhrsdev.musicplayer.data.settings.SettingsStore(context);
        PrefsSettingsStore ui = new PrefsSettingsStore(data);

        ui.setTheme(SettingsStore.Theme.BLACK);
        ui.setSeekStyle(SettingsStore.SeekStyle.BARS);
        ui.setOnlineLyrics(false);
        ui.setSleepDefaultMinutes(0);
        ui.setBatterySaver(true);
        assertEquals(com.mhrsdev.musicplayer.data.settings.ThemeMode.BLACK, data.theme().getValue());
        assertEquals(com.mhrsdev.musicplayer.data.settings.SeekBarStyle.BARS, data.seekBarStyle().getValue());
        assertFalse(data.lyricsOnline().getValue());
        assertEquals(BatterySaverMode.ON, data.batterySaver().getValue());
        assertTrue(ui.isBatterySaver());
        assertEquals(0, ui.getSleepDefaultMinutes());
        ui.setBatterySaver(false);
        assertEquals(BatterySaverMode.AUTO, data.batterySaver().getValue());
    }

    @Test public void batterySaverModeCombinesWithTheSystemState() {
        assertTrue(BatterySaverMode.AUTO.active(true));
        assertFalse(BatterySaverMode.AUTO.active(false));
        assertTrue(BatterySaverMode.ON.active(false));
        assertFalse(BatterySaverMode.OFF.active(true));
    }
}
