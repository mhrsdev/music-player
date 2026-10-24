package com.mhrsdev.musicplayer.ui.settings;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.mhrsdev.musicplayer.data.settings.PerformanceProfile;
import com.mhrsdev.musicplayer.data.settings.PerformanceProfile.Preset;
import com.mhrsdev.musicplayer.data.settings.SettingsStore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** A preset rewrites every switch; a hand-made change shows as Custom; Custom itself is not a choice. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class PerformanceViewModelTest {
    @Rule public InstantTaskExecutorRule instantTaskExecutor = new InstantTaskExecutorRule();

    private PerformanceViewModel vm() {
        Context c = RuntimeEnvironment.getApplication();
        c.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit();
        return new PerformanceViewModel(new SettingsStore(c));
    }

    @Test public void presetSetsAllSwitchesAndManualChangeMakesCustom() {
        PerformanceViewModel vm = vm();
        vm.choosePreset(Preset.LIGHT);
        assertEquals(PerformanceProfile.LIGHT, vm.getProfile().getValue());
        vm.change(p -> p.withSeekMotion(true));
        assertEquals(Preset.CUSTOM, vm.getProfile().getValue().preset());
        vm.choosePreset(Preset.CUSTOM); // no-op
        assertEquals(Preset.CUSTOM, vm.getProfile().getValue().preset());
        vm.choosePreset(Preset.BALANCED);
        assertEquals(PerformanceProfile.BALANCED, vm.getProfile().getValue());
        vm.change(p -> p.withReduceMotion(true));
        vm.choosePreset(Preset.FULL); // a preset also lifts reduce motion: "Full" means everything on
        assertEquals(PerformanceProfile.FULL, vm.getProfile().getValue());
    }
}
