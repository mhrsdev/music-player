package com.mhrsdev.musicplayer.ui.player;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.Lyrics;

/** What the lyrics area shows when there are no lines to draw (pure, JVM-tested). */
public enum LyricsEmptyState {
    /** Lines exist: no message. */
    CONTENT,
    /** The lookup is still running ("Finding lyrics…"). */
    LOADING,
    /** Looked everywhere, nothing valid: "No lyrics found" + Search again / Add file / Search manually. */
    NOT_FOUND,
    /** The online step failed (HTTP error, timeout): Retry + Add file + Search manually. */
    FAILED,
    /** Nothing found and the device is offline: Retry + Add file. */
    OFFLINE;

    public static LyricsEmptyState of(@Nullable Lyrics l, boolean online) {
        if (l == null) return LOADING;
        if (!l.isEmpty()) return CONTENT;
        if (!online) return OFFLINE;
        return l.failed() ? FAILED : NOT_FOUND;
    }
}
