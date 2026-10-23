package com.mhrsdev.musicplayer.domain.model;

/**
 * One online lyrics result offered by "Search manually".
 *
 * @param text the raw lyrics text (LRC or plain) as the service returned it
 */
public record LyricsCandidate(String title, String artist, String album, long durationMs, boolean synced,
        String text) { }
