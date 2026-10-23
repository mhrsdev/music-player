package com.mhrsdev.musicplayer.ui.nav;

import android.content.Context;
import android.content.ContextWrapper;
import androidx.annotation.Nullable;

/** In-app navigation between library screens. Implemented by {@code MainActivity}; every call pushes onto the back stack. */
public interface ScreenNavigator {

    /** What a track-list screen shows. */
    enum TrackList { ALBUM, ARTIST, PLAYLIST, LIKED, HISTORY, RECENT }

    /** What a collection grid shows. */
    enum Collection { ALBUMS, ARTISTS, PLAYLISTS }

    /** {@code id} is the album/artist/playlist id, or null for LIKED / HISTORY / RECENT. */
    void openTrackList(TrackList kind, @Nullable String id);

    void openCollection(Collection kind);

    /** Switches to a top-level tab (clears the back stack). */
    void openTab(Tab tab);

    enum Tab { HOME, SEARCH, LIBRARY, SETTINGS }

    @Nullable
    static ScreenNavigator from(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof ScreenNavigator) return (ScreenNavigator) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
