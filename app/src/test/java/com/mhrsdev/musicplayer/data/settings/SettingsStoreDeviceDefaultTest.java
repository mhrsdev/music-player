package com.mhrsdev.musicplayer.data.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.mhrsdev.musicplayer.data.settings.PerformanceProfile.Preset;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** C1 (D32): the preset chosen for the phone on first run never replaces a saved choice and is shown until changed. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SettingsStoreDeviceDefaultTest {
    @Rule public InstantTaskExecutorRule instantTaskExecutor = new InstantTaskExecutorRule();
    private Context context;

    @Before public void clear() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void firstRunStoresThePresetAndRemembersItWasChosenForThePhone() {
        SettingsStore s = new SettingsStore(context);
        s.chooseDefaultPerformance(() -> Preset.LIGHT);
        assertEquals(PerformanceProfile.LIGHT, s.performance().getValue());
        assertEquals(Preset.LIGHT, s.performanceChosenForPhone());
        SettingsStore restarted = new SettingsStore(context);
        assertEquals(PerformanceProfile.LIGHT, restarted.performance().getValue());
        assertEquals(Preset.LIGHT, restarted.performanceChosenForPhone());
    }

    @Test public void aSavedChoiceIsNeverOverridden() {
        SettingsStore s = new SettingsStore(context);
        s.setPerformance(PerformanceProfile.FULL); // the user picked Full on purpose
        s.chooseDefaultPerformance(() -> Preset.LIGHT);
        assertEquals(PerformanceProfile.FULL, s.performance().getValue());
        assertNull(s.performanceChosenForPhone());
    }

    @Test public void theOlderReduceMotionSwitchCountsAsASavedChoice() {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("reduce_motion", false).commit();
        SettingsStore s = new SettingsStore(context);
        s.chooseDefaultPerformance(() -> Preset.BALANCED);
        assertEquals(PerformanceProfile.FULL, s.performance().getValue());
    }

    @Test public void changingAnythingByHandEndsTheChosenForThisPhoneNote() {
        SettingsStore s = new SettingsStore(context);
        s.chooseDefaultPerformance(() -> Preset.BALANCED);
        s.setPerformance(PerformanceProfile.BALANCED.withBounce(false));
        assertNull(s.performanceChosenForPhone());
        s.chooseDefaultPerformance(() -> Preset.LIGHT); // and a later start does not choose again
        assertEquals(PerformanceProfile.BALANCED.withBounce(false), s.performance().getValue());
    }

    @Test public void theChoiceIsMadeOnceAndTheRulesAreNotAskedAgain() {
        SettingsStore s = new SettingsStore(context);
        s.chooseDefaultPerformance(() -> Preset.BALANCED);
        s.chooseDefaultPerformance(() -> { throw new AssertionError("asked twice"); });
        assertEquals(Preset.BALANCED, s.performanceChosenForPhone());
    }

    @Test public void aRestoredBackupReplacesThePhoneChoiceAndIsNotAPartOfBackups() {
        SettingsStore s = new SettingsStore(context);
        s.chooseDefaultPerformance(() -> Preset.LIGHT);
        assertEquals("the marker is not exported", false, s.exportBackup().containsKey("perf_auto_preset"));
        s.importBackup(Map.of("perf_max_fps", 60, "perf_bounce", true));
        assertNull(s.performanceChosenForPhone());
    }
}
