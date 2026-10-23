package com.mhrsdev.musicplayer.playback;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.repository.LibraryBrowser;

/** Hands the library reader to {@link PlaybackService}, which may be created by the system before any UI exists. */
public final class BrowseHolder {
    @Nullable private static volatile LibraryBrowser browser;

    private BrowseHolder() {}

    public static void set(@Nullable LibraryBrowser value) {
        browser = value;
    }

    @Nullable static LibraryBrowser get() {
        return browser;
    }
}
