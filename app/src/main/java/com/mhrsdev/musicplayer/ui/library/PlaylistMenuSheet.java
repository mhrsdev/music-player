package com.mhrsdev.musicplayer.ui.library;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;
import com.mhrsdev.musicplayer.R;
import java.util.ArrayList;
import java.util.List;

/**
 * Playlist options: rename, add songs, delete. Arguments hold the playlist; the choice is delivered as a fragment
 * result, so the sheet survives recreation and the screen that is alive at that moment handles it.
 */
public class PlaylistMenuSheet extends ActionSheet {
    public enum Action { RENAME, ADD_SONGS, DELETE }

    /** Receives the chosen action for a playlist. */
    public interface Listener {
        void onAction(String playlistId, Action action);
    }

    private static final String TAG = "playlist_menu";
    private static final String RESULT_KEY = "u2_playlist_menu_result";
    private static final String ARG_ID = "id", ARG_NAME = "name", ARG_SEED = "seed", ARG_COVERS = "covers",
            RESULT_ACTION = "action";

    public static void show(FragmentManager fm, String playlistId, TrackListViewModel.Header header) {
        if (fm.findFragmentByTag(TAG) != null) return;
        PlaylistMenuSheet s = new PlaylistMenuSheet();
        Bundle args = new Bundle();
        args.putString(ARG_ID, playlistId);
        args.putString(ARG_NAME, header.title() == null ? "" : header.title());
        args.putInt(ARG_SEED, header.seed());
        args.putStringArrayList(ARG_COVERS, new ArrayList<>(header.artworkUris()));
        s.setArguments(args);
        s.show(fm, TAG);
    }

    public static void listen(FragmentManager fm, LifecycleOwner owner, Listener listener) {
        fm.setFragmentResultListener(RESULT_KEY, owner, (key, result) -> {
            String id = result.getString(ARG_ID);
            String action = result.getString(RESULT_ACTION);
            if (id != null && action != null) listener.onAction(id, Action.valueOf(action));
        });
    }

    @Nullable @Override protected Header build(List<Row> rows) {
        Bundle args = requireArguments();
        rows.add(Row.of(R.drawable.u2_ic_edit, getString(R.string.u2_rename_playlist), () -> send(Action.RENAME)));
        rows.add(Row.of(R.drawable.u2_ic_add, getString(R.string.u2_add_songs), () -> send(Action.ADD_SONGS)));
        rows.add(new Row(R.drawable.u2_ic_delete, getString(R.string.u2_delete_playlist), true, false,
                () -> send(Action.DELETE)));
        List<String> covers = args.getStringArrayList(ARG_COVERS);
        return new Header(covers == null ? List.of() : covers, args.getInt(ARG_SEED), args.getString(ARG_NAME, ""), null);
    }

    private void send(Action action) {
        Bundle result = new Bundle();
        result.putString(ARG_ID, requireArguments().getString(ARG_ID));
        result.putString(RESULT_ACTION, action.name());
        getParentFragmentManager().setFragmentResult(RESULT_KEY, result);
    }
}
