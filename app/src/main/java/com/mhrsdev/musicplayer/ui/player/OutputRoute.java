package com.mhrsdev.musicplayer.ui.player;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * Current audio output for the player's output chip, MediaRouter-free and permission-free: AudioManager output
 * devices, refreshed only by an {@link AudioDeviceCallback} that is registered while the player is visible
 * ({@link #start}/{@link #stop}), so it costs nothing in the background. Bluetooth product names are shown only when
 * BLUETOOTH_CONNECT is granted (API 31+); otherwise a generic "Bluetooth".
 */
public final class OutputRoute {
    public enum Kind { SPEAKER, WIRED, USB, BLUETOOTH }

    /** What the chip shows; {@code name} is null for the generic label. */
    public record Route(Kind kind, @Nullable String name) {}

    public interface Listener { void onRoute(Route r); }

    private final AudioManager am;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    @Nullable private Listener listener;
    private final AudioDeviceCallback callback = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) { publish(); }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) { publish(); }
    };

    public OutputRoute(Context c) {
        context = c.getApplicationContext();
        am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public void start(Listener l) {
        listener = l;
        if (am == null) { l.onRoute(new Route(Kind.SPEAKER, null)); return; }
        am.registerAudioDeviceCallback(callback, main); // also delivers the current devices once
        publish();
    }

    public void stop() {
        listener = null;
        if (am != null) am.unregisterAudioDeviceCallback(callback);
    }

    private void publish() {
        Listener l = listener;
        if (l == null) return;
        AudioDeviceInfo[] devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        int[] types = new int[devices.length];
        for (int i = 0; i < devices.length; i++) types[i] = devices[i].getType();
        int best = pick(types);
        Kind kind = best < 0 ? Kind.SPEAKER : kindOf(types[best]);
        String name = null;
        if (kind == Kind.BLUETOOTH && canReadBluetoothName()) {
            CharSequence n = devices[best].getProductName();
            if (n != null && n.length() > 0) name = n.toString();
        }
        l.onRoute(new Route(kind, name));
    }

    private boolean canReadBluetoothName() {
        return Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(context,
                Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    /** Pure: index of the device that most likely carries media (Bluetooth > USB > wired > speaker), -1 for none. */
    static int pick(int[] types) {
        int best = -1, bestRank = 0;
        for (int i = 0; i < types.length; i++) {
            int r = rank(types[i]);
            if (r > bestRank) { bestRank = r; best = i; }
        }
        return best;
    }

    private static int rank(int type) {
        Kind k = kindOf(type);
        if (k == null) return 0;
        switch (k) {
            case BLUETOOTH: return 4;
            case USB: return 3;
            case WIRED: return 2;
            default: return 1;
        }
    }

    @Nullable static Kind kindOf(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
            case AudioDeviceInfo.TYPE_BLE_HEADSET:
            case AudioDeviceInfo.TYPE_BLE_SPEAKER:
            case AudioDeviceInfo.TYPE_HEARING_AID:
                return Kind.BLUETOOTH;
            case AudioDeviceInfo.TYPE_USB_HEADSET:
            case AudioDeviceInfo.TYPE_USB_DEVICE:
                return Kind.USB;
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                return Kind.WIRED;
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                return Kind.SPEAKER;
            default:
                return null;
        }
    }
}
