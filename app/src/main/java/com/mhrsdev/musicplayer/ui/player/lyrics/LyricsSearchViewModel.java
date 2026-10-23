package com.mhrsdev.musicplayer.ui.player.lyrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.mhrsdev.musicplayer.domain.model.LyricsCandidate;
import com.mhrsdev.musicplayer.domain.model.LyricsSearchResult;
import com.mhrsdev.musicplayer.domain.repository.LyricsRepository;

import java.util.Collections;
import java.util.List;

/**
 * "Search manually" dialog: artist + title → one online search → candidates (best first). The picked candidate is
 * handed to the player ({@code PlayerViewModel.useLyricsCandidate}), which keeps it for the track.
 */
public class LyricsSearchViewModel extends ViewModel {

    /** Dialog state. {@code searched} = at least one search finished (so "nothing found" can be shown). */
    public record State(boolean loading, List<LyricsCandidate> results, boolean failed, boolean searched) {
        static final State IDLE = new State(false, Collections.emptyList(), false, false);
    }

    private final LyricsRepository repo;
    private final MediatorLiveData<State> state = new MediatorLiveData<>();
    @Nullable private LiveData<LyricsSearchResult> running;

    LyricsSearchViewModel(LyricsRepository repo) {
        this.repo = repo;
        state.setValue(State.IDLE);
    }

    public LiveData<State> getState() { return state; }

    /** @return false when both fields are blank (nothing to search) */
    public boolean search(String artist, String title) {
        String a = artist == null ? "" : artist.trim(), t = title == null ? "" : title.trim();
        if (a.isEmpty() && t.isEmpty()) return false;
        if (running != null) state.removeSource(running);
        State old = state.getValue();
        state.setValue(new State(true, old == null ? Collections.emptyList() : old.results(), false,
                old != null && old.searched()));
        LiveData<LyricsSearchResult> src = repo.search(a, t);
        running = src;
        state.addSource(src, r -> {
            if (r == null) return;
            state.removeSource(src);
            running = null;
            state.setValue(new State(false, r.items(), r.failed(), true));
        });
        return true;
    }

    public static ViewModelProvider.Factory factory(LyricsRepository repo) {
        return new ViewModelProvider.Factory() {
            @NonNull @Override
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                return modelClass.cast(new LyricsSearchViewModel(repo));
            }
        };
    }
}
