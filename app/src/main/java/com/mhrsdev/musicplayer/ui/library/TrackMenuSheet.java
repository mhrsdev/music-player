package com.mhrsdev.musicplayer.ui.library;

import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator;
import com.mhrsdev.musicplayer.ui.nav.Screens;
import com.mhrsdev.musicplayer.ui.playlist.AddToPlaylistSheet;
import java.util.Collections;
import java.util.List;

/**
 * The one track context menu of the app: play next, add to queue / playlist, like, go to album / artist,
 * remove from this playlist, details, share, delete from device.
 */
public final class TrackMenuSheet extends ActionSheet {
    private static final String TAG = "track_menu";

    @Nullable private Track track;
    @Nullable private String playlistId;
    @Nullable private FragmentActivity host;

    public static void show(FragmentActivity activity, Track track, @Nullable String playlistId) {
        if (activity.getSupportFragmentManager().findFragmentByTag(TAG) != null) return;
        TrackMenuSheet s = new TrackMenuSheet();
        s.track = track;
        s.playlistId = playlistId;
        s.host = activity;
        s.show(activity.getSupportFragmentManager(), TAG);
    }

    @Override protected boolean hasState() {
        return track != null && host != null;
    }

    @Nullable @Override protected Header build(List<Row> rows) {
        Track t = track;
        FragmentActivity a = host;
        AppContainer c = Screens.container(a);
        rows.add(Row.of(R.drawable.u2_ic_play_next, getString(R.string.library_menu_play_next), () -> {
            c.player.playNext(t);
            toast(a, a.getString(R.string.u2_playing_next, t.title()));
        }));
        rows.add(Row.of(R.drawable.ic_mp_queue, getString(R.string.library_menu_add_queue), () -> {
            c.player.addToQueue(t);
            toast(a, a.getString(R.string.u2_added_to_queue, t.title()));
        }));
        rows.add(Row.of(R.drawable.library_ic_playlist, getString(R.string.library_menu_add_playlist),
                () -> AddToPlaylistSheet.show(a.getSupportFragmentManager(), Collections.singletonList(t))));
        rows.add(Row.of(t.liked() ? R.drawable.ic_mp_heart_filled : R.drawable.ic_mp_heart,
                getString(t.liked() ? R.string.library_menu_unlike : R.string.library_menu_like),
                () -> c.library.toggleLike(t.id())));
        rows.add(Row.of(R.drawable.library_ic_album, getString(R.string.u2_go_album), () -> open(a,
                ScreenNavigator.TrackList.ALBUM, t.albumId())));
        rows.add(Row.of(R.drawable.library_ic_artist, getString(R.string.u2_go_artist), () -> open(a,
                ScreenNavigator.TrackList.ARTIST, t.artistId())));
        String pid = playlistId;
        if (pid != null) {
            rows.add(Row.of(R.drawable.ic_mp_close, getString(R.string.u2_remove_from_playlist),
                    () -> c.libraryAdmin().removeFromPlaylist(pid, t.id())));
        }
        rows.add(Row.of(R.drawable.u2_ic_info, getString(R.string.u2_details), () -> TrackDetailsSheet.show(a, t)));
        if (t.mediaUri() != null) {
            rows.add(Row.of(R.drawable.u2_ic_share, getString(R.string.u2_share),
                    () -> TrackActions.share(a, Collections.singletonList(t))));
            rows.add(new Row(R.drawable.u2_ic_delete, getString(R.string.u2_delete_device), true, false,
                    () -> TrackActions.deleteFromDevice(a, Collections.singletonList(t))));
        }
        return new Header(t.artworkUri() == null ? Collections.emptyList() : Collections.singletonList(t.artworkUri()),
                t.placeholderSeed(), t.title(), t.artistName() + " · " + t.albumTitle());
    }

    private static void open(FragmentActivity a, ScreenNavigator.TrackList kind, String id) {
        ScreenNavigator nav = ScreenNavigator.from(a);
        if (nav != null) nav.openTrackList(kind, id);
    }

    static void toast(FragmentActivity a, CharSequence text) {
        Toast.makeText(a, text, Toast.LENGTH_SHORT).show();
    }
}
