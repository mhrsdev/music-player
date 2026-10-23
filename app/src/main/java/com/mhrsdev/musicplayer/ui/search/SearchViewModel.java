package com.mhrsdev.musicplayer.ui.search;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.mhrsdev.musicplayer.domain.model.SearchFilter;
import com.mhrsdev.musicplayer.domain.model.SearchResult;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.repository.LibraryRepository;
import com.mhrsdev.musicplayer.domain.repository.PlayerController;
import java.util.ArrayList;
import java.util.List;

/** Search: query + filter chips -> results from the library. */
public class SearchViewModel extends ViewModel {

    /** What the screen should show. */
    public enum Mode { PROMPT, RESULTS, NO_RESULTS }

    private record Request(String query, SearchFilter filter) { }

    private final PlayerController player;
    private final RecentSearches recentStore;
    private final MutableLiveData<List<String>> recent = new MutableLiveData<>();
    private final LiveData<Boolean> libraryEmpty;
    private final MutableLiveData<String> query = new MutableLiveData<>("");
    private final MutableLiveData<SearchFilter> filter = new MutableLiveData<>(SearchFilter.ALL);
    private final LiveData<List<SearchResult>> results;
    private final MediatorLiveData<Mode> mode = new MediatorLiveData<>();

    SearchViewModel(LibraryRepository library, PlayerController player) {
        this(library, player, new RecentSearches((android.content.SharedPreferences) null));
    }

    SearchViewModel(LibraryRepository library, PlayerController player, RecentSearches recentStore) {
        this.player = player;
        this.recentStore = recentStore;
        recent.setValue(recentStore.get());
        libraryEmpty = Transformations.map(library.getAllTracks(), l -> l != null && l.isEmpty());
        MediatorLiveData<Request> request = new MediatorLiveData<>();
        request.addSource(query, q -> request.setValue(new Request(q, filter.getValue())));
        request.addSource(filter, f -> request.setValue(new Request(query.getValue(), f)));
        results = Transformations.switchMap(Transformations.distinctUntilChanged(request),
                r -> library.search(r.query().trim(), r.filter()));
        mode.addSource(results, list -> mode.setValue(modeFor(query.getValue(), list)));
    }

    static Mode modeFor(String q, List<SearchResult> list) {
        if (q == null || q.isBlank()) return Mode.PROMPT;
        return list == null || list.isEmpty() ? Mode.NO_RESULTS : Mode.RESULTS;
    }

    public LiveData<String> getQuery() { return query; }

    public LiveData<SearchFilter> getFilter() { return filter; }

    public LiveData<List<SearchResult>> getResults() { return results; }

    public LiveData<Mode> getMode() { return mode; }

    /** Recent searches, newest first (shown while the field is empty). */
    public LiveData<List<String>> getRecent() { return recent; }

    /** True when the library has no songs at all (search can only say "nothing here yet"). */
    public LiveData<Boolean> isLibraryEmpty() { return libraryEmpty; }

    /** Remembers the current query (IME search action or a result was opened). */
    public void commitQuery() {
        String q = query.getValue();
        if (q == null || q.isBlank()) return;
        recentStore.add(q);
        recent.setValue(recentStore.get());
    }

    public void removeRecent(String q) {
        recentStore.remove(q);
        recent.setValue(recentStore.get());
    }

    public void clearRecent() {
        recentStore.clear();
        recent.setValue(recentStore.get());
    }

    public void setQuery(String q) {
        if (!q.equals(query.getValue())) query.setValue(q);
    }

    public void setFilter(SearchFilter f) {
        if (f != filter.getValue()) filter.setValue(f);
    }

    /** Plays the track results as a queue, starting at {@code track}. */
    public void playTrack(Track track) {
        List<Track> tracks = new ArrayList<>();
        List<SearchResult> list = results.getValue();
        if (list != null) {
            for (SearchResult r : list) if (r.kind() == SearchResult.Kind.TRACK) tracks.add(r.track());
        }
        if (tracks.isEmpty()) tracks.add(track);
        player.play(tracks, Math.max(0, tracks.indexOf(track)), query.getValue());
    }

    public static ViewModelProvider.Factory factory(LibraryRepository library, PlayerController player,
            RecentSearches recent) {
        return new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> c) {
                return c.cast(new SearchViewModel(library, player, recent));
            }
        };
    }
}
