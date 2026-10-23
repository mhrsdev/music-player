package com.mhrsdev.musicplayer.domain.model;

import androidx.annotation.Nullable;

/**
 * Read-only facts about one track for the "Track details" sheet: the library row plus listening numbers and file
 * facts (0 / null = unknown, e.g. for fakes or when the file is gone).
 *
 * @param lastPlayedMs epoch millis of the last play, 0 = never
 * @param bitrateKbps 0 = unknown
 */
public record TrackDetails(
        Track track,
        int playCount,
        long lastPlayedMs,
        @Nullable String path,
        long sizeBytes,
        @Nullable String mimeType,
        int bitrateKbps) { }
