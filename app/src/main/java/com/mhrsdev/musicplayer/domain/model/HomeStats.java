package com.mhrsdev.musicplayer.domain.model;

import androidx.annotation.Nullable;

/**
 * Numbers for the Home "Your listening" card.
 *
 * @param listenedMsMonth approximate listening time this calendar month (sum of played tracks' durations)
 * @param topArtistMonth most played artist this month, null when nothing was played
 */
public record HomeStats(
        int tracks,
        int artists,
        int albums,
        long libraryMs,
        int playsMonth,
        long listenedMsMonth,
        @Nullable String topArtistMonth,
        @Nullable String topArtistMonthId) {
}
