package com.mhrsdev.musicplayer.playback;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.mhrsdev.musicplayer.domain.model.OutputDevice;
import com.mhrsdev.musicplayer.domain.model.OutputDevice.Kind;
import com.mhrsdev.musicplayer.domain.repository.OutputDevices;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * {@link OutputDevices} on {@code AudioManager.getDevices(GET_DEVICES_OUTPUTS)}. The system {@link AudioDeviceCallback}
 * is registered only while the list has an observer and unregistered when the last one leaves; nothing polls.
 * Bluetooth names need BLUETOOTH_CONNECT on API 31+ (already requested by the player's output chip): without it the
 * device is listed with {@code name == null}.
 */
public class OutputDevicesImpl implements OutputDevices {
    /** The raw facts about one {@link AudioDeviceInfo}; keeps the list building pure. */
    record Raw(int type, @Nullable String address, @Nullable String productName) { }

    private final Context context;
    private final AudioManager am;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final DevicesData data = new DevicesData();
    private final AudioDeviceCallback callback = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) { refresh(); }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) { refresh(); }
    };

    public OutputDevicesImpl(Context context) {
        this.context = context.getApplicationContext();
        am = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
    }

    @Override public LiveData<List<OutputDevice>> getDevices() {
        return data;
    }

    @Override public void refresh() {
        data.setValue(read());
    }

    @Override public int getVolume() {
        return am == null ? 0 : am.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    @Override public int getMaxVolume() {
        return am == null ? 0 : am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    }

    @Override public void setVolume(int step) {
        if (am != null) am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(0, Math.min(step, getMaxVolume())), 0);
    }

    private List<OutputDevice> read() {
        if (am == null) return new ArrayList<>();
        AudioDeviceInfo[] infos = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        List<Raw> raws = new ArrayList<>(infos.length);
        for (AudioDeviceInfo d : infos) {
            CharSequence name = d.getProductName(); // address needs API 28; before it the key falls back to the name
            raws.add(new Raw(d.getType(), Build.VERSION.SDK_INT >= 28 ? d.getAddress() : null, name == null ? null : name.toString()));
        }
        int activeType = -1;
        String activeAddress = null;
        if (Build.VERSION.SDK_INT >= 33) {
            List<AudioDeviceInfo> routed = am.getAudioDevicesForAttributes(
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build());
            if (!routed.isEmpty()) {
                activeType = routed.get(0).getType();
                activeAddress = routed.get(0).getAddress();
            }
        }
        return build(raws, canReadBluetoothName(), activeType, activeAddress);
    }

    private boolean canReadBluetoothName() {
        return Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(context,
                Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    // --- pure list building (unit-tested) -----------------------------------------------------------

    /**
     * Keeps the outputs that carry music, finds the active route (the one the system routes media to, else the most
     * "plugged-in" one: Bluetooth, USB, wired, then the speaker) and orders active first.
     *
     * @param activeType the routed device's type, or -1 when the system does not say (before API 33)
     */
    static List<OutputDevice> build(List<Raw> raws, boolean canReadBluetoothName, int activeType,
            @Nullable String activeAddress) {
        List<Raw> kept = new ArrayList<>();
        for (Raw r : raws) if (kindOf(r.type()) != null) kept.add(r);
        int active = -1;
        for (int i = 0; i < kept.size() && active < 0 && activeType >= 0; i++) {
            Raw r = kept.get(i);
            if (r.type() == activeType && (activeAddress == null || activeAddress.isEmpty()
                    || activeAddress.equals(r.address()))) active = i;
        }
        if (active < 0) {
            int bestRank = 0;
            for (int i = 0; i < kept.size(); i++) {
                int rank = rank(kindOf(kept.get(i).type()));
                if (rank > bestRank) { bestRank = rank; active = i; }
            }
        }
        List<OutputDevice> out = new ArrayList<>();
        for (int i = 0; i < kept.size(); i++) {
            Raw r = kept.get(i);
            Kind kind = kindOf(r.type());
            boolean needsPermission = kind == Kind.BLUETOOTH && !canReadBluetoothName;
            String name = needsPermission || r.productName() == null || r.productName().isEmpty() ? null : r.productName();
            out.add(new OutputDevice(keyOf(kind, r, name), kind, name, needsPermission, i == active));
        }
        out.sort(Comparator.comparing((OutputDevice d) -> !d.active()).thenComparingInt(d -> -rank(d.kind())));
        return out;
    }

    /** Stable across reconnects: kind plus address, else kind plus name, else just the kind. */
    static String keyOf(Kind kind, Raw r, @Nullable String name) {
        if (r.address() != null && !r.address().isEmpty()) return kind + ":" + r.address();
        return name != null ? kind + ":" + name : kind.name();
    }

    @Nullable static Kind kindOf(int type) {
        return switch (type) {
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER,
                    AudioDeviceInfo.TYPE_BLE_BROADCAST, AudioDeviceInfo.TYPE_HEARING_AID -> Kind.BLUETOOTH;
            case AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY -> Kind.USB;
            case AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_LINE_ANALOG,
                    AudioDeviceInfo.TYPE_LINE_DIGITAL -> Kind.WIRED;
            case AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC -> Kind.HDMI;
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> Kind.SPEAKER;
            default -> null; // earpiece, telephony, SCO (calls), buses, remote submix: not somewhere music is sent
        };
    }

    private static int rank(Kind kind) {
        return switch (kind) {
            case BLUETOOTH -> 5;
            case USB -> 4;
            case WIRED -> 3;
            case HDMI -> 2;
            default -> 1;
        };
    }

    /** Registers the system callback only while observed. */
    private class DevicesData extends MutableLiveData<List<OutputDevice>> {
        @Override protected void onActive() {
            if (am != null) am.registerAudioDeviceCallback(callback, main); // delivers the current devices once
            refresh();
        }

        @Override protected void onInactive() {
            if (am != null) am.unregisterAudioDeviceCallback(callback);
        }
    }
}
