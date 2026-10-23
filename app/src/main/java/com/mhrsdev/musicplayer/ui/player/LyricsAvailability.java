package com.mhrsdev.musicplayer.ui.player;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.Lyrics;
import com.mhrsdev.musicplayer.domain.model.Track;

/**
 * Whether the current track has lyrics, which decides if the player shows its lyrics button. Derived from the
 * repository's single lookup for the current track (local tag/sidecar, then the cached online result, then at most
 * one LRCLIB request when the online setting is on); that lookup only runs while the full player is visible.
 */
public enum LyricsAvailability {
    /** No current track. */
    UNKNOWN,
    /** Lookup for the current track still running: show nothing (no spinner). */
    CHECKING,
    AVAILABLE,
    NONE;

    /** Pure mapping from the latest lyrics value (maybe still the previous track's) and the current track. */
    public static LyricsAvailability of(@Nullable Lyrics lyrics, @Nullable Track current) {
        if (current == null) return UNKNOWN;
        if (lyrics == null || !lyrics.trackId().equals(current.id())) return CHECKING;
        return lyrics.isEmpty() ? NONE : AVAILABLE;
    }
}
