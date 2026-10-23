package com.mhrsdev.musicplayer.ui.settings;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.mhrsdev.musicplayer.data.settings.SeekBarStyle;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** D25: the new seek styles persist, old stored values still load, unknown ones (and odd backups) fall back to Wavy. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SeekStyleSettingTest {
    @Rule public InstantTaskExecutorRule instantTaskExecutor = new InstantTaskExecutorRule();

    private static Context fresh(String stored) {
        Context c = RuntimeEnvironment.getApplication();
        android.content.SharedPreferences.Editor e = c.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear();
        if (stored != null) e.putString("seek_bar_style", stored);
        e.commit();
        return c;
    }

    @Test public void oldValuesStillLoad() {
        for (String old : new String[] {"WAVY", "WAVEFORM", "BARS"}) {
            com.mhrsdev.musicplayer.data.settings.SettingsStore s = new com.mhrsdev.musicplayer.data.settings.SettingsStore(fresh(old));
            assertEquals(old, s.seekBarStyle().getValue().name());
        }
    }

    @Test public void unknownValueFallsBackToWavy() {
        com.mhrsdev.musicplayer.data.settings.SettingsStore s = new com.mhrsdev.musicplayer.data.settings.SettingsStore(fresh("HOLOGRAM"));
        assertEquals(SeekBarStyle.WAVY, s.seekBarStyle().getValue());
        assertEquals(SettingsStore.SeekStyle.WAVY, new PrefsSettingsStore(s).getSeekStyle());
    }

    @Test public void everyNewStyleRoundTripsThroughBothStores() {
        for (SeekBarStyle st : SeekBarStyle.values()) {
            com.mhrsdev.musicplayer.data.settings.SettingsStore s = new com.mhrsdev.musicplayer.data.settings.SettingsStore(fresh(null));
            PrefsSettingsStore ui = new PrefsSettingsStore(s);
            ui.setSeekStyle(SettingsStore.SeekStyle.valueOf(st.name()));
            assertEquals(st, s.seekBarStyle().getValue());
            // a later process reads the persisted name
            assertEquals(st, new com.mhrsdev.musicplayer.data.settings.SettingsStore(RuntimeEnvironment.getApplication())
                    .seekBarStyle().getValue());
        }
    }

    @Test public void backupWithNewAndUnknownStylesImportsSafely() {
        com.mhrsdev.musicplayer.data.settings.SettingsStore s = new com.mhrsdev.musicplayer.data.settings.SettingsStore(fresh(null));
        Map<String, Object> backup = new HashMap<>();
        backup.put("seek_bar_style", "LED_LADDER");
        s.importBackup(backup);
        assertEquals(SeekBarStyle.LED_LADDER, s.seekBarStyle().getValue());
        backup.put("seek_bar_style", "FROM_A_NEWER_VERSION");
        s.importBackup(backup);
        assertEquals(SeekBarStyle.WAVY, s.seekBarStyle().getValue());
        backup.put("seek_bar_style", 7); // wrong type: ignored
        s.importBackup(backup);
        assertEquals(SeekBarStyle.WAVY, s.seekBarStyle().getValue());
    }
}
