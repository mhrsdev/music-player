package com.mhrsdev.musicplayer.ui.headset;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.MediaRouter2;
import android.os.Build;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.List;

/**
 * Opens the system's own places for changing where audio goes. Nothing here is a stable public contract on every
 * phone, so each target is tried in turn and the first that starts wins; the last one is always a plain system screen.
 */
public class OutputSwitcher {
    static final String MEDIA_OUTPUT_PANEL = "com.android.settings.panel.action.MEDIA_OUTPUT";
    static final String EXTRA_PANEL_PACKAGE = "com.android.settings.panel.extra.PACKAGE_NAME";
    static final String AUDIO_SHARING_SETTINGS = "com.android.settings.BLUETOOTH_AUDIO_SHARING_SETTINGS";

    private OutputSwitcher() { }

    /** The system output switcher: API 34's MediaRouter2 dialog, else Settings' media-output / volume panel. */
    public static boolean openOutputPicker(Context context) {
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                if (MediaRouter2.getInstance(context).showSystemOutputSwitcher()) return true;
            } catch (RuntimeException e) {
                // fall through to the panels
            }
        }
        return startFirst(context, outputPickerIntents(context.getPackageName()));
    }

    /** Dual Audio (Samsung) / Audio Sharing (Android LE Audio) settings, else the Bluetooth settings list. */
    public static boolean openAudioSharing(Context context) {
        return startFirst(context, audioSharingIntents());
    }

    static List<Intent> outputPickerIntents(String packageName) {
        List<Intent> out = new ArrayList<>();
        out.add(new Intent(MEDIA_OUTPUT_PANEL).putExtra(EXTRA_PANEL_PACKAGE, packageName));
        out.add(new Intent(Settings.Panel.ACTION_VOLUME));
        out.add(new Intent(Settings.ACTION_SOUND_SETTINGS));
        return out;
    }

    static List<Intent> audioSharingIntents() {
        List<Intent> out = new ArrayList<>();
        out.add(new Intent().setComponent(new ComponentName("com.android.settings",
                "com.samsung.android.settings.bluetooth.DualAudioSettings")));
        out.add(new Intent(AUDIO_SHARING_SETTINGS));
        out.add(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        return out;
    }

    static boolean startFirst(Context context, List<Intent> intents) {
        for (Intent i : intents) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return true;
            } catch (ActivityNotFoundException | SecurityException notHere) {
                // try the next one
            }
        }
        return false;
    }
}
