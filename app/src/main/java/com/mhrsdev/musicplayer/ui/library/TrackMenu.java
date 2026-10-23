package com.mhrsdev.musicplayer.ui.library;

import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import com.mhrsdev.musicplayer.domain.model.Track;

/** Entry point used by every track row in the app (Home, Search, Library): opens the shared {@link TrackMenuSheet}. */
public class TrackMenu {
    private TrackMenu() { }

    public static void show(View anchor, Track track) {
        show(anchor, track, null);
    }

    /** {@code playlistId}: the playlist the row belongs to (adds "Remove from this playlist"), or null. */
    public static void show(View anchor, Track track, @Nullable String playlistId) {
        FragmentActivity activity = activityOf(anchor.getContext());
        if (activity != null) TrackMenuSheet.show(activity, track, playlistId);
    }

    @Nullable static FragmentActivity activityOf(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof FragmentActivity a) return a;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
