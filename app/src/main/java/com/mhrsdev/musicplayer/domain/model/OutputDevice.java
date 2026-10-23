package com.mhrsdev.musicplayer.domain.model;

import androidx.annotation.Nullable;

/**
 * One connected audio output. {@code key} is stable across reconnects (type + address, or type + name) and keys the
 * per-device sound profile. {@code name} is null when the system hides it (Bluetooth without BLUETOOTH_CONNECT).
 * {@code active}: the route media currently plays to. Immutable.
 */
public record OutputDevice(String key, Kind kind, @Nullable String name, boolean nameNeedsPermission, boolean active) {

    public enum Kind { SPEAKER, EARPIECE, WIRED, USB, BLUETOOTH, HDMI, OTHER }
}
