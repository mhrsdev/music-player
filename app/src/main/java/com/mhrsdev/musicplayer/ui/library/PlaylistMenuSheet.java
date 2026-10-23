package com.mhrsdev.musicplayer.ui.library;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import com.mhrsdev.musicplayer.R;
import java.util.List;

/** Playlist options: rename, add songs, delete. */
public class PlaylistMenuSheet extends ActionSheet {
    private static final String TAG = "playlist_menu";
    @Nullable private TrackListViewModel.Header header;
    @Nullable private Runnable rename, delete, addSongs;

    public static void show(FragmentActivity a, TrackListViewModel.Header header, Runnable rename, Runnable delete,
            Runnable addSongs) {
        if (a.getSupportFragmentManager().findFragmentByTag(TAG) != null) return;
        PlaylistMenuSheet s = new PlaylistMenuSheet();
        s.header = header;
        s.rename = rename;
        s.delete = delete;
        s.addSongs = addSongs;
        s.show(a.getSupportFragmentManager(), TAG);
    }

    @Override protected boolean hasState() {
        return header != null;
    }

    @Nullable @Override protected Header build(List<Row> rows) {
        rows.add(Row.of(R.drawable.u2_ic_edit, getString(R.string.u2_rename_playlist), rename));
        rows.add(Row.of(R.drawable.u2_ic_add, getString(R.string.u2_add_songs), addSongs));
        rows.add(new Row(R.drawable.u2_ic_delete, getString(R.string.u2_delete_playlist), true, false, delete));
        TrackListViewModel.Header h = header;
        return new Header(h.artworkUris(), h.seed(), h.title() == null ? "" : h.title(), null);
    }
}
