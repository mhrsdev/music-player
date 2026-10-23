package com.mhrsdev.musicplayer.ui.library;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.mhrsdev.musicplayer.domain.model.Album;
import com.mhrsdev.musicplayer.domain.model.Artist;
import com.mhrsdev.musicplayer.domain.model.Playlist;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.repository.LibraryRepository;
import com.mhrsdev.musicplayer.domain.repository.PlayerController;
import java.util.List;

/** Library hub: Liked songs card, Albums / Playlists / Artists / History tiles, Recently saved. */
public final class LibraryViewModel extends ViewModel {
    private final LibraryRepository library;
    private final PlayerController player;

    LibraryViewModel(LibraryRepository library, PlayerController player) {
        this.library = library;
        this.player = player;
    }

    public LiveData<List<Track>> getLiked() { return library.getLikedTracks(); }

    public LiveData<List<Album>> getAlbums() { return library.getAlbums(); }

    public LiveData<List<Artist>> getArtists() { return library.getArtists(); }

    public LiveData<List<Playlist>> getPlaylists() { return library.getPlaylists(); }

    public LiveData<List<Track>> getHistory() { return library.getHistory(); }

    public LiveData<List<Track>> getRecentlySaved() { return library.getRecentlySaved(); }

    /** Plays the liked songs from the top; no-op when there are none. */
    public void playLiked(String playingFrom) {
        List<Track> liked = library.getLikedTracks().getValue();
        if (liked != null && !liked.isEmpty()) player.play(liked, 0, playingFrom);
    }

    public void play(List<Track> tracks, int index, String playingFrom) {
        player.play(tracks, index, playingFrom);
    }

    /** Total duration of {@code tracks} in ms. */
    public static long totalMs(List<Track> tracks) {
        long sum = 0;
        for (Track t : tracks) sum += t.durationMs();
        return sum;
    }

    public static ViewModelProvider.Factory factory(LibraryRepository library, PlayerController player) {
        return new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> c) {
                return c.cast(new LibraryViewModel(library, player));
            }
        };
    }
}
