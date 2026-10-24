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
public class PlayerViewModel extends ViewModel {
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
    private final MediatorLiveData<LyricsAvailability> lyricsAvailability = new MediatorLiveData<>();
    /** What the lyrics view asks for: the current track's normal lookup, or a user action (search again/pick/file). */
    private final MediatorLiveData<LyricsRequest> lyricsRequest = new MediatorLiveData<>();
    /** Track id for which the user opened the lyrics tools ("Find lyrics"): its NONE result must not close the view. */
    @Nullable private String toolsTrackId;
    private long lastSearchAgainMs = Long.MIN_VALUE / 2;
    java.util.function.LongSupplier clock = android.os.SystemClock::elapsedRealtime;
    /** "Search again" is rate limited to one per this many ms. */
    static final long SEARCH_AGAIN_INTERVAL_MS = 10_000;

    /** One lyrics request. {@code mode}: NORMAL lookup, FRESH ("Search again"/Retry), PICK, FILE. */
    record LyricsRequest(Track track, int mode, @Nullable com.mhrsdev.musicplayer.domain.model.LyricsCandidate pick,
            @Nullable String uri) {
        static final int NORMAL = 0, FRESH = 1, PICK = 2, FILE = 3;
    }
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
        // Lyrics are requested for the CURRENT track only, and only while the player observes (its view lifecycle =
        // full player visible): the answer decides whether the lyrics button is shown (owner, D21 request). Local
        // tag/sidecar first; online only via the cache or one LRCLIB request with the setting on (LyricsResolver).
        // Tracks in the queue are never looked up, and nothing runs while the player is hidden.
        lyricsRequest.addSource(currentTrack, t -> updateLyricsRequest());
        lyrics = Transformations.switchMap(lyricsRequest, r -> {
            if (r == null) return new MutableLiveData<Lyrics>(null); // null clears stale lyrics
            LiveData<Lyrics> src = switch (r.mode()) {
                case LyricsRequest.FRESH -> lyricsRepo.searchAgain(r.track());
                case LyricsRequest.PICK -> lyricsRepo.useCandidate(r.track(), r.pick());
                case LyricsRequest.FILE -> lyricsRepo.importFile(r.track(), r.uri());
                default -> lyricsRepo.lyricsFor(r.track());
            };
            // emit "loading" (null) first so a user action shows "Finding lyrics…" instead of the old answer
            MediatorLiveData<Lyrics> m = new MediatorLiveData<>();
            m.setValue(null);
            m.addSource(src, m::setValue);
            return m;
        });
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

        lyricsAvailability.addSource(lyrics, l -> updateAvailability());
        lyricsAvailability.addSource(currentTrack, t -> updateAvailability());
        activeLyricLine.addSource(lyrics, l -> updateActiveLine());
        activeLyricLine.addSource(state, s -> updateActiveLine());
    }

    private void updateLyricsRequest() {
        Track wanted = currentTrack.getValue();
        LyricsRequest r = lyricsRequest.getValue();
        if (r != null && sameId(r.track(), wanted)) return;
        lyricsRequest.setValue(wanted == null ? null : new LyricsRequest(wanted, LyricsRequest.NORMAL, null, null));
    }

    private void request(int mode, @Nullable com.mhrsdev.musicplayer.domain.model.LyricsCandidate pick,
            @Nullable String uri) {
        Track t = currentTrack.getValue();
        if (t == null) return;
        toolsTrackId = t.id(); // the user is working on this track's lyrics: keep the view open
        lyricsRequest.setValue(new LyricsRequest(t, mode, pick, uri));
    }

    /**
     * "Search again" / Retry: a fresh online lookup ignoring the cached answer. At most one per
     * {@link #SEARCH_AGAIN_INTERVAL_MS}; @return false when rate limited.
     */
    public boolean searchAgain() {
        long now = clock.getAsLong();
        if (now - lastSearchAgainMs < SEARCH_AGAIN_INTERVAL_MS) return false;
        lastSearchAgainMs = now;
        request(LyricsRequest.FRESH, null, null);
        return true;
    }

    /** A "Search manually" result picked by the user: kept for this track from now on. */
    public void useLyricsCandidate(com.mhrsdev.musicplayer.domain.model.LyricsCandidate c) {
        request(LyricsRequest.PICK, c, null);
    }

    /** "Add lyrics file": the document the user picked (SAF uri string). */
    public void importLyricsFile(String uri) {
        request(LyricsRequest.FILE, null, uri);
    }

    /** "Find lyrics" (menu, when the track has none): opens the lyrics view on its empty state with the tools. */
    public void openLyricsTools() {
        Track t = currentTrack.getValue();
        if (t == null) return;
        toolsTrackId = t.id();
        showLyrics.setValue(true);
    }

    /** Cleaned artist / title of the current track, to prefill "Search manually". */
    public String[] manualSearchPrefill() {
        Track t = currentTrack.getValue();
        if (t == null) return new String[]{"", ""};
        return new String[]{com.mhrsdev.musicplayer.data.lyrics.LyricsQueryNormalizer.artist(t.artistName()),
                com.mhrsdev.musicplayer.data.lyrics.LyricsQueryNormalizer.title(t.title())};
    }

    /** True while a lookup for the current track is running (no answer yet): "Finding lyrics…". */
    public boolean lyricsLoading() {
        return lyricsAvailability.getValue() == LyricsAvailability.CHECKING;
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

    private void updateAvailability() {
        LyricsAvailability a = LyricsAvailability.of(lyrics.getValue(), currentTrack.getValue());
        if (a == lyricsAvailability.getValue()) return;
        lyricsAvailability.setValue(a);
        // A track without lyrics while the lyrics view is open: back to the cover, unless the user opened the lyrics
        // tools for this very track (then the empty state with Search again / Add file / Search manually stays).
        Track t = currentTrack.getValue();
        boolean tools = t != null && t.id().equals(toolsTrackId);
        if (a == LyricsAvailability.NONE && Boolean.TRUE.equals(showLyrics.getValue()) && !tools) showLyrics.setValue(false);
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
    /** Drives the lyrics button: shown only for {@link LyricsAvailability#AVAILABLE}. */
    public LiveData<LyricsAvailability> getLyricsAvailability() { return lyricsAvailability; }

    public void togglePlayPause() { player.togglePlayPause(); }
    /** "Stop": pause and rewind the current song (PlayerController has no stop). */
    public void stop() {
        player.pause();
        player.seekTo(0L);
    }
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
    /** Retry after a failed online lookup (offline, HTTP error, timeout): a fresh lookup, same rate limit. */
    public void retryLyrics() { searchAgain(); }

    /**
     * Offline state (design 13): the domain {@link Lyrics} has no error value, so "no lyrics while the network is
     * unavailable" is shown as offline with a Retry chip; with a network it is the plain "no lyrics" state.
     */
    static boolean showOfflineRetry(@Nullable Lyrics l, boolean online) {
        return l != null && l.isEmpty() && (l.failed() || !online);
    }

    public void toggleShuffle() { player.toggleShuffle(); }
    public void cycleRepeat() { player.cycleRepeat(); }
    /** Opens lyrics only when the track has some (cover tap, menu, button); closing always works. */
    public void toggleLyrics() {
        boolean open = Boolean.TRUE.equals(showLyrics.getValue());
        if (!open && lyricsAvailability.getValue() != LyricsAvailability.AVAILABLE) return;
        showLyrics.setValue(!open);
    }
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
