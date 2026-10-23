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
public final class SearchViewModel extends ViewModel {

    /** What the screen should show. */
    public enum Mode { PROMPT, RESULTS, NO_RESULTS }

    private record Request(String query, SearchFilter filter) { }

    private final PlayerController player;
    private final MutableLiveData<String> query = new MutableLiveData<>("");
    private final MutableLiveData<SearchFilter> filter = new MutableLiveData<>(SearchFilter.ALL);
    private final LiveData<List<SearchResult>> results;
    private final MediatorLiveData<Mode> mode = new MediatorLiveData<>();

    SearchViewModel(LibraryRepository library, PlayerController player) {
        this.player = player;
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

    public static ViewModelProvider.Factory factory(LibraryRepository library, PlayerController player) {
        return new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> c) {
                return c.cast(new SearchViewModel(library, player));
            }
        };
    }
}
