package com.mhrsdev.musicplayer.domain.model;

/** How a backup meets the data already on the device. */
public enum RestoreMode {
    /** Add what is missing, keep what exists (playlists by name, higher play counts win). */
    MERGE,
    /** Erase likes, playlists and play history first, then load the file. */
    REPLACE
}
