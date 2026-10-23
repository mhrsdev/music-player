package com.mhrsdev.musicplayer.ui.library;

import android.view.View;
import android.widget.PopupMenu;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.repository.LibraryRepository;
import com.mhrsdev.musicplayer.domain.repository.PlayerController;
import com.mhrsdev.musicplayer.ui.nav.Screens;

/** Overflow menu for a track row: play next, add to queue, like, go to album / artist. */
public final class TrackMenu {
    private TrackMenu() { }

    private static final int PLAY_NEXT = 1, ADD_QUEUE = 2, LIKE = 3;

    public static void show(View anchor, Track track) {
        PlayerController player = Screens.container(anchor.getContext()).player;
        LibraryRepository library = Screens.container(anchor.getContext()).library;
        PopupMenu menu = new PopupMenu(anchor.getContext(), anchor);
        menu.getMenu().add(0, PLAY_NEXT, 0, com.mhrsdev.musicplayer.R.string.library_menu_play_next);
        menu.getMenu().add(0, ADD_QUEUE, 1, com.mhrsdev.musicplayer.R.string.library_menu_add_queue);
        menu.getMenu().add(0, LIKE, 2, track.liked()
                ? com.mhrsdev.musicplayer.R.string.library_menu_unlike : com.mhrsdev.musicplayer.R.string.library_menu_like);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case PLAY_NEXT -> player.playNext(track);
                case ADD_QUEUE -> player.addToQueue(track);
                case LIKE -> library.toggleLike(track.id());
                default -> { return false; }
            }
            return true;
        });
        menu.show();
    }
}
