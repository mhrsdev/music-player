package com.mhrsdev.musicplayer.ui.library;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Observer;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.domain.model.Playlist;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.repository.LibraryRepository;
import com.mhrsdev.musicplayer.domain.repository.PlayerController;
import com.mhrsdev.musicplayer.ui.nav.Screens;
import java.util.List;

/** Overflow menu for a track row: play next, add to queue, like, go to album / artist. */
public final class TrackMenu {
    private TrackMenu() { }

    private static final int PLAY_NEXT = 1, ADD_QUEUE = 2, LIKE = 3, ADD_PLAYLIST = 4;

    public static void show(View anchor, Track track) {
        PlayerController player = Screens.container(anchor.getContext()).player;
        LibraryRepository library = Screens.container(anchor.getContext()).library;
        PopupMenu menu = new PopupMenu(anchor.getContext(), anchor);
        menu.getMenu().add(0, PLAY_NEXT, 0, com.mhrsdev.musicplayer.R.string.library_menu_play_next);
        menu.getMenu().add(0, ADD_QUEUE, 1, com.mhrsdev.musicplayer.R.string.library_menu_add_queue);
        menu.getMenu().add(0, LIKE, 2, track.liked()
                ? com.mhrsdev.musicplayer.R.string.library_menu_unlike : com.mhrsdev.musicplayer.R.string.library_menu_like);
        menu.getMenu().add(0, ADD_PLAYLIST, 3, R.string.library_menu_add_playlist);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case PLAY_NEXT -> player.playNext(track);
                case ADD_QUEUE -> player.addToQueue(track);
                case LIKE -> library.toggleLike(track.id());
                case ADD_PLAYLIST -> pickPlaylist(anchor.getContext(), library, track);
                default -> { return false; }
            }
            return true;
        });
        menu.show();
    }

    /** Lists the playlists (last row: a new one) and adds {@code track} to the chosen one. */
    private static void pickPlaylist(Context context, LibraryRepository library, Track track) {
        library.getPlaylists().observeForever(new Observer<List<Playlist>>() {
            @Override public void onChanged(List<Playlist> playlists) {
                library.getPlaylists().removeObserver(this); // one snapshot is enough for the picker
                String[] names = new String[playlists.size() + 1];
                for (int i = 0; i < playlists.size(); i++) names[i] = playlists.get(i).name();
                names[playlists.size()] = context.getString(R.string.library_new_playlist);
                new AlertDialog.Builder(context).setTitle(R.string.library_menu_add_playlist)
                        .setItems(names, (d, which) -> {
                            if (which < playlists.size()) library.addToPlaylist(playlists.get(which).id(), track.id());
                            else createAndAdd(context, library, track);
                        }).show();
            }
        });
    }

    private static void createAndAdd(Context context, LibraryRepository library, Track track) {
        EditText name = new EditText(context);
        name.setSingleLine();
        name.setText(R.string.library_new_playlist);
        new AlertDialog.Builder(context).setTitle(R.string.library_new_playlist).setView(name)
                .setPositiveButton(android.R.string.ok, (d, w) -> library.createPlaylist(name.getText().toString(),
                        p -> library.addToPlaylist(p.id(), track.id())))
                .setNegativeButton(android.R.string.cancel, null).show();
    }
}
