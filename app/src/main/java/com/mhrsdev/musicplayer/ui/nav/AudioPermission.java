package com.mhrsdev.musicplayer.ui.nav;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.content.ContextCompat;

/** READ_MEDIA_AUDIO on API 33+, READ_EXTERNAL_STORAGE below. */
public final class AudioPermission {
    private AudioPermission() { }

    public static String name() {
        return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    public static boolean granted(Context context) {
        return ContextCompat.checkSelfPermission(context, name()) == PackageManager.PERMISSION_GRANTED;
    }
}
