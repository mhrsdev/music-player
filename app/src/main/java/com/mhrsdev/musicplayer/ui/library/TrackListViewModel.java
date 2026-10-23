package com.mhrsdev.musicplayer.ui.library;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.repository.LibraryRepository;
import com.mhrsdev.musicplayer.domain.repository.PlayerController;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator.TrackList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Album / Artist / Playlist detail and the Liked, History and Recently saved lists. */
public final class TrackListViewModel extends ViewModel {

    /** Header shown above the list. {@code title} is null for LIKED/HISTORY/RECENT (the screen uses its own label). */
    public record Header(@Nullable String title, @Nullable String subtitle, int seed, boolean circle) { }

    private final PlayerController player;
    private final LiveData<List<Track>> tracks;
    private final LiveData<Header> header;

    TrackListViewModel(LibraryRepository library, PlayerController player, TrackList kind, @Nullable String id) {
        this.player = player;
        switch (kind) {
            case ALBUM -> {
                tracks = library.getAlbumTracks(id);
                header = Transformations.map(library.getAlbum(id), a -> a == null ? null
                        : new Header(a.title(), a.artistName() + (a.year() > 0 ? " · " + a.year() : ""),
                        a.placeholderSeed(), false));
            }
            case ARTIST -> {
                tracks = library.getArtistTracks(id);
                header = Transformations.map(library.getArtist(id), a -> a == null ? null
                        : new Header(a.name(), null, a.placeholderSeed(), true));
            }
            case PLAYLIST -> {
                tracks = library.getPlaylistTracks(id);
                header = Transformations.map(library.getPlaylist(id), p -> p == null ? null
                        : new Header(p.name(), p.description(), p.placeholderSeed(), false));
            }
            case LIKED -> {
                tracks = library.getLikedTracks();
                header = new MutableLiveData<>(new Header(null, null, 1, false));
            }
            case HISTORY -> {
                tracks = library.getHistory();
                header = new MutableLiveData<>(new Header(null, null, 4, false));
            }
            default -> {
                tracks = library.getRecentlySaved();
                header = new MutableLiveData<>(new Header(null, null, 6, false));
            }
        }
    }

    public LiveData<List<Track>> getTracks() { return tracks; }

    public LiveData<Header> getHeader() { return header; }

    public void playAt(int index, String playingFrom) {
        List<Track> list = tracks.getValue();
        if (list != null && index >= 0 && index < list.size()) player.play(list, index, playingFrom);
    }

    public void shuffle(String playingFrom) {
        List<Track> list = tracks.getValue();
        if (list == null || list.isEmpty()) return;
        List<Track> copy = new ArrayList<>(list);
        Collections.shuffle(copy);
        player.play(copy, 0, playingFrom);
    }

    public static ViewModelProvider.Factory factory(LibraryRepository library, PlayerController player,
            TrackList kind, @Nullable String id) {
        return new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> c) {
                return c.cast(new TrackListViewModel(library, player, kind, id));
            }
        };
    }
}
