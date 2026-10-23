package com.mhrsdev.musicplayer.ui.nav;

import android.content.Context;
import androidx.fragment.app.Fragment;
import com.mhrsdev.musicplayer.MusicPlayerApp;
import com.mhrsdev.musicplayer.di.AppContainer;

/** Small helpers shared by the screen fragments. */
public final class Screens {
    private Screens() { }

    public static AppContainer container(Fragment fragment) {
        return ((MusicPlayerApp) fragment.requireContext().getApplicationContext()).getContainer();
    }

    public static AppContainer container(Context context) {
        return ((MusicPlayerApp) context.getApplicationContext()).getContainer();
    }

    public static void openTrackList(Fragment f, ScreenNavigator.TrackList kind, String id) {
        ScreenNavigator nav = ScreenNavigator.from(f.requireContext());
        if (nav != null) nav.openTrackList(kind, id);
    }

    public static void openCollection(Fragment f, ScreenNavigator.Collection kind) {
        ScreenNavigator nav = ScreenNavigator.from(f.requireContext());
        if (nav != null) nav.openCollection(kind);
    }
}
