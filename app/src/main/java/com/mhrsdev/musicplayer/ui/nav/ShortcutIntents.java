package com.mhrsdev.musicplayer.ui.nav;

import android.content.Context;
import android.content.Intent;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The launcher's static shortcuts (res/xml/shortcuts.xml): the intent actions and what each one does. */
public final class ShortcutIntents {
    public static final String ACTION_SHUFFLE_ALL = "com.mhrsdev.musicplayer.action.SHUFFLE_ALL";
    public static final String ACTION_LIKED = "com.mhrsdev.musicplayer.action.LIKED";
    /** Voice search ("play ...") from Assistant or a head unit; opens search. */
    static final String ACTION_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    public static final String ACTION_SEARCH = "com.mhrsdev.musicplayer.action.SEARCH";

    private ShortcutIntents() { }

    /** Runs the shortcut in {@code intent}, if it is one; returns whether it was. The action is cleared so it runs once. */
    public static boolean handle(Context context, Intent intent, AppContainer container, ScreenNavigator nav) {
        String action = intent == null ? null : intent.getAction();
        if (action == null) return false;
        switch (action) {
            case ACTION_SEARCH, ACTION_PLAY_FROM_SEARCH -> nav.openTab(ScreenNavigator.Tab.SEARCH);
            case ACTION_LIKED -> nav.openTrackList(ScreenNavigator.TrackList.LIKED, null);
            case ACTION_SHUFFLE_ALL -> shuffleAll(context, container);
            default -> { return false; }
        }
        intent.setAction(null);
        return true;
    }

    /** Waits for the first library load, then plays every track in random order. Nothing happens for an empty library. */
    private static void shuffleAll(Context context, AppContainer container) {
        String label = context.getString(R.string.shortcut_shuffle_playing_from);
        LiveData<List<Track>> all = container.library.getAllTracks();
        all.observeForever(new Observer<>() {
            @Override public void onChanged(List<Track> tracks) {
                if (tracks == null) return;
                all.removeObserver(this);
                if (tracks.isEmpty()) return;
                List<Track> shuffled = new ArrayList<>(tracks);
                Collections.shuffle(shuffled);
                container.player.play(shuffled, 0, label);
            }
        });
    }
}
