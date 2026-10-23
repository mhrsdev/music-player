package com.mhrsdev.musicplayer.domain.model;

/** Update preferences; {@code lastCheckedMs} is 0 until a check has succeeded. */
public record UpdateSettings(boolean autoCheck, boolean includePrereleases, boolean wifiOnly, long lastCheckedMs) { }
