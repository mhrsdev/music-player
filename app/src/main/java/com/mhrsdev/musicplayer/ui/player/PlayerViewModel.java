package com.mhrsdev.musicplayer.ui.player;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.mhrsdev.musicplayer.domain.model.Lyrics;
import com.mhrsdev.musicplayer.domain.model.PlaybackState;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.repository.LibraryRepository;
import com.mhrsdev.musicplayer.domain.repository.LyricsRepository;
import com.mhrsdev.musicplayer.domain.repository.PlayerController;
import com.mhrsdev.musicplayer.domain.repository.WaveformRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Full player screen state. Holds no Context/View. Position ticks come from {@link PlayerController#getState()}; the
 * fragment observes with its view lifecycle, so nothing is delivered while the player is not visible.
 */
public final class PlayerViewModel extends ViewModel {
    /** Number of buckets requested for the Waveform / Bars seek styles. */
    public static final int WAVEFORM_BARS = 120;

    private final PlayerController player;
    private final LibraryRepository library;
    private final LiveData<PlaybackState> state;
    private final MediatorLiveData<Track> currentTrack = new MediatorLiveData<>();
    private final LiveData<Lyrics> lyrics;
    private final LiveData<float[]> waveform;
    private final MediatorLiveData<Boolean> liked = new MediatorLiveData<>();
    private final MediatorLiveData<Integer> activeLyricLine = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> showLyrics = new MutableLiveData<>(false);
    /** Re-emits the current track on {@link #retryLyrics()} so the repository is asked again. */
    private final MediatorLiveData<Track> lyricsRequest = new MediatorLiveData<>();
    private Set<String> likedIds = new HashSet<>();
    private boolean likedLoaded;

    PlayerViewModel(PlayerController player, LibraryRepository library, LyricsRepository lyricsRepo,
                    WaveformRepository waveformRepo) {
        this.player = player;
        this.library = library;
        this.state = player.getState();
        currentTrack.addSource(state, s -> {
            Track t = s == null ? null : s.currentTrack();
            Track old = currentTrack.getValue();
            if (!sameId(old, t)) currentTrack.setValue(t);
        });
        lyricsRequest.addSource(currentTrack, lyricsRequest::setValue);
        lyrics = Transformations.switchMap(lyricsRequest, t -> t == null ? new MutableLiveData<>() : lyricsRepo.lyricsFor(t));
        waveform = Transformations.switchMap(currentTrack,
                t -> t == null ? new MutableLiveData<>() : waveformRepo.waveformFor(t, WAVEFORM_BARS));

        LiveData<List<Track>> likedTracks = library.getLikedTracks();
        liked.addSource(likedTracks, list -> {
            Set<String> ids = new HashSet<>();
            if (list != null) for (Track t : list) ids.add(t.id());
            likedIds = ids;
            likedLoaded = list != null;
            updateLiked();
        });
        liked.addSource(currentTrack, t -> updateLiked());

        activeLyricLine.addSource(lyrics, l -> updateActiveLine());
        activeLyricLine.addSource(state, s -> updateActiveLine());
    }

    private static boolean sameId(@Nullable Track a, @Nullable Track b) {
        if (a == null || b == null) return a == b;
        return a.id().equals(b.id());
    }

    private void updateLiked() {
        Track t = currentTrack.getValue();
        boolean v = t != null && (likedLoaded ? likedIds.contains(t.id()) : t.liked());
        if (!Objects.equals(liked.getValue(), v)) liked.setValue(v);
    }

    private void updateActiveLine() {
        int idx = activeLineFor(lyrics.getValue(), state.getValue());
        Integer old = activeLyricLine.getValue();
        if (old == null || old != idx) activeLyricLine.setValue(idx);
    }

    /** Pure mapping: active line for the given lyrics and state, -1 for none / unsynced / other track. */
    static int activeLineFor(@Nullable Lyrics l, @Nullable PlaybackState s) {
        if (l == null || s == null || s.currentTrack() == null || l.isEmpty()) return -1;
        if (!l.trackId().equals(s.currentTrack().id())) return -1;
        return l.activeLineIndex(s.positionMs());
    }

    public LiveData<PlaybackState> getState() { return state; }
    public LiveData<Track> getCurrentTrack() { return currentTrack; }
    /** No value while loading, then {@link Lyrics} (maybe {@link Lyrics#none}). */
    public LiveData<Lyrics> getLyrics() { return lyrics; }
    public LiveData<float[]> getWaveform() { return waveform; }
    public LiveData<Boolean> getLiked() { return liked; }
    /** Distinct-until-changed index of the active lyric line. */
    public LiveData<Integer> getActiveLyricLine() { return activeLyricLine; }
    public LiveData<Boolean> getShowLyrics() { return showLyrics; }

    public void togglePlayPause() { player.togglePlayPause(); }
    public void next() { player.next(); }
    public void previous() { player.previous(); }
    public void seekTo(long positionMs) { player.seekTo(Math.max(0L, positionMs)); }
    public void seekToFraction(float f) {
        PlaybackState s = state.getValue();
        if (s != null && s.durationMs() > 0) seekTo((long) (Math.max(0f, Math.min(1f, f)) * s.durationMs()));
    }
    /** TalkBack / keyboard 10 s steps. */
    public void seekBy(long deltaMs) {
        PlaybackState s = state.getValue();
        if (s == null) return;
        seekTo(Math.min(s.durationMs(), s.positionMs() + deltaMs));
    }
    /** Re-requests lyrics for the current track (offline state's Retry chip). */
    public void retryLyrics() { lyricsRequest.setValue(currentTrack.getValue()); }

    /**
     * Offline state (design 13): the domain {@link Lyrics} has no error value, so "no lyrics while the network is
     * unavailable" is shown as offline with a Retry chip; with a network it is the plain "no lyrics" state.
     */
    static boolean showOfflineRetry(@Nullable Lyrics l, boolean online) {
        return l != null && l.isEmpty() && !online;
    }

    public void toggleShuffle() { player.toggleShuffle(); }
    public void cycleRepeat() { player.cycleRepeat(); }
    public void toggleLyrics() { showLyrics.setValue(!Boolean.TRUE.equals(showLyrics.getValue())); }
    public void toggleLike() {
        Track t = currentTrack.getValue();
        if (t == null) return;
        library.toggleLike(t.id());
    }

    public static ViewModelProvider.Factory factory(PlayerController player, LibraryRepository library,
                                                    LyricsRepository lyrics, WaveformRepository waveform) {
        return new ViewModelProvider.Factory() {
            @NonNull @Override
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                return modelClass.cast(new PlayerViewModel(player, library, lyrics, waveform));
            }
        };
    }
}
