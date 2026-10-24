package com.mhrsdev.musicplayer.data.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Settings > Performance persists, survives a restart, carries the old reduce-motion choice, and is in backups. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SettingsStorePerformanceTest {
    @Rule public InstantTaskExecutorRule instantTaskExecutor = new InstantTaskExecutorRule();
    private Context context;

    @Before public void clear() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void defaultIsFull() {
        assertEquals(PerformanceProfile.FULL, new SettingsStore(context).performance().getValue());
    }

    @Test public void choiceSurvivesANewStore() {
        SettingsStore s = new SettingsStore(context);
        PerformanceProfile custom = PerformanceProfile.LIGHT.withLyricsWords(true).withMaxFps(60);
        s.setPerformance(custom);
        assertEquals(custom, s.performance().getValue());
        assertEquals(custom, new SettingsStore(context).performance().getValue()); // process restart
    }

    @Test public void theOlderReduceMotionSwitchCarriesOver() {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("reduce_motion", true).commit();
        PerformanceProfile p = new SettingsStore(context).performance().getValue();
        assertTrue(p.reduceMotion());
        assertEquals(PerformanceProfile.FULL.withReduceMotion(true), p);
    }

    @Test public void backupRoundTripsEveryPerformanceKey() {
        SettingsStore s = new SettingsStore(context);
        PerformanceProfile mine = PerformanceProfile.BALANCED.withReduceMotion(true).withMaxFps(30);
        s.setPerformance(mine);
        Map<String, Object> backup = s.exportBackup();
        assertEquals(Boolean.TRUE, backup.get("reduce_motion"));
        assertEquals(30, backup.get("perf_max_fps"));
        assertEquals(Boolean.FALSE, backup.get("perf_moving_backdrop"));

        s.setPerformance(PerformanceProfile.FULL);
        int applied = s.importBackup(new HashMap<>(backup));
        assertTrue("at least the 8 performance keys, got " + applied, applied >= 8);
        assertEquals(mine, s.performance().getValue());
    }

    @Test public void importIgnoresWrongTypesAndUnknownRates() {
        SettingsStore s = new SettingsStore(context);
        Map<String, Object> bad = new HashMap<>();
        bad.put("perf_lyrics_blur", "yes"); // wrong type: skipped
        bad.put("perf_max_fps", 45);        // stored, but not a choice: reads back as screen rate
        s.importBackup(bad);
        PerformanceProfile p = s.performance().getValue();
        assertTrue(p.lyricsBlur());
        assertEquals(PerformanceProfile.FPS_SCREEN, p.maxFps());
        assertFalse(p.reduceMotion());
    }
}
