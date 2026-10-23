package com.mhrsdev.musicplayer.ui.headset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowPackageManager;

/** The system-screen fallbacks: the first target that exists wins, the last is always a plain settings screen. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class OutputSwitcherTest {
    private static void register(Intent i) {
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = "com.android.settings";
        info.activityInfo.name = "Stub";
        ((ShadowPackageManager) Shadow.extract(RuntimeEnvironment.getApplication().getPackageManager())).addResolveInfoForIntent(i, info);
    }

    @Test public void audioSharingFallsBackToBluetoothSettings() {
        Application app = RuntimeEnvironment.getApplication();
        ((ShadowApplication) Shadow.extract(app)).checkActivities(true);
        register(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        assertTrue(OutputSwitcher.openAudioSharing(app));
        assertEquals(Settings.ACTION_BLUETOOTH_SETTINGS, ((ShadowApplication) Shadow.extract(app)).getNextStartedActivity().getAction());
    }

    @Test public void audioSharingPrefersTheDedicatedScreen() {
        Application app = RuntimeEnvironment.getApplication();
        ((ShadowApplication) Shadow.extract(app)).checkActivities(true);
        register(new Intent(OutputSwitcher.AUDIO_SHARING_SETTINGS));
        register(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        assertTrue(OutputSwitcher.openAudioSharing(app));
        assertEquals(OutputSwitcher.AUDIO_SHARING_SETTINGS, ((ShadowApplication) Shadow.extract(app)).getNextStartedActivity().getAction());
    }

    @Test public void reportsFailureWhenNothingResolves() {
        Application app = RuntimeEnvironment.getApplication();
        ((ShadowApplication) Shadow.extract(app)).checkActivities(true);
        assertFalse(OutputSwitcher.startFirst(app, OutputSwitcher.outputPickerIntents("x")));
    }
}
