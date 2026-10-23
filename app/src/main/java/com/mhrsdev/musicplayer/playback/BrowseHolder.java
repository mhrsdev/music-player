package com.mhrsdev.musicplayer.playback;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.repository.LibraryReader;

/** Hands the library reader to {@link PlaybackService}, which may be created by the system before any UI exists. */
public class BrowseHolder {
    @Nullable private static volatile LibraryReader browser;

    private BrowseHolder() {}

    public static void set(@Nullable LibraryReader value) {
        browser = value;
    }

    @Nullable static LibraryReader get() {
        return browser;
    }
}
