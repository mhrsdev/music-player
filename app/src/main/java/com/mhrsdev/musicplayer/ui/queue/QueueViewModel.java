package com.mhrsdev.musicplayer.ui.queue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.mhrsdev.musicplayer.domain.model.PlaybackState;
import com.mhrsdev.musicplayer.domain.model.Playlist;
import com.mhrsdev.musicplayer.domain.model.SleepTimer;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.repository.Callback;
import com.mhrsdev.musicplayer.domain.repository.LibraryRepository;
import com.mhrsdev.musicplayer.domain.repository.PlayerController;
import com.mhrsdev.musicplayer.ui.player.PlayerFormat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Queue sheet state. The list shows the tracks AFTER the current one ("Up next"); adapter positions map to queue
 * indices with {@link #toQueueIndex}. Holds no Context/View.
 */
public final class QueueViewModel extends ViewModel {

    /** What the sheet renders. {@code sleepMinutes} is meaningful only for {@link SleepTimer.Kind#COUNTDOWN}. */
    public record UiState(List<Track> upNext, int currentIndex, @Nullable String playingFrom, int minutesLeft,
                          SleepTimer.Kind sleepKind, int sleepMinutes, boolean canUndoShuffle,
                          @Nullable Track current) {}

    /** A removal that can be undone. */
    public record Removed(Track track, int queueIndex) {}

    public static final int[] SLEEP_PRESETS_MIN = {5, 10, 15, 30, 45, 60, 90};

    private final PlayerController player;
    @Nullable private final LibraryRepository library;
    private final LiveData<PlaybackState> state;
    private final LiveData<UiState> ui;

    QueueViewModel(PlayerController player) { this(player, null); }

    QueueViewModel(PlayerController player, @Nullable LibraryRepository library) {
        this.player = player;
        this.library = library;
        this.state = player.getState();
        // The state ticks 4 times a second while playing; the screen only re-renders when what it shows changed.
        MediatorLiveData<UiState> distinct = new MediatorLiveData<>();
        distinct.addSource(state, s -> {
            UiState next = map(s);
            if (!next.equals(distinct.getValue())) distinct.setValue(next);
        });
        this.ui = distinct;
    }

    /** Pure state mapping (unit-tested). */
    static UiState map(@Nullable PlaybackState s) {
        if (s == null) s = PlaybackState.empty();
        int cur = s.currentIndex();
        List<Track> upNext = cur >= 0 && cur + 1 <= s.queue().size()
                ? s.queue().subList(cur + 1, s.queue().size()) : Collections.emptyList();
        SleepTimer t = s.sleepTimer() == null ? SleepTimer.OFF : s.sleepTimer();
        return new UiState(upNext, cur, s.playingFrom(), PlayerFormat.minutesCeil(s.remainingQueueMs()),
                t.kind(), t.kind() == SleepTimer.Kind.COUNTDOWN ? PlayerFormat.minutesCeil(t.remainingMs()) : 0,
                s.canUndoShuffle(), s.currentTrack());
    }

    /** Adapter position in the Up-next list to the index in the full queue. */
    static int toQueueIndex(int currentIndex, int adapterPosition) {
        return Math.max(0, currentIndex) + 1 + adapterPosition;
    }

    public LiveData<UiState> getUi() { return ui; }

    private int currentIndex() {
        PlaybackState s = state.getValue();
        return s == null ? -1 : s.currentIndex();
    }

    public void move(int fromPos, int toPos) {
        if (fromPos == toPos) return;
        int cur = currentIndex();
        player.moveQueueItem(toQueueIndex(cur, fromPos), toQueueIndex(cur, toPos));
    }

    /** Removes the row and returns what Undo needs, or null if the position is stale. */
    @Nullable public Removed remove(int pos) {
        PlaybackState s = state.getValue();
        if (s == null) return null;
        int idx = toQueueIndex(s.currentIndex(), pos);
        if (idx >= s.queue().size()) return null;
        Track t = s.queue().get(idx);
        player.removeFromQueue(idx);
        return new Removed(t, idx);
    }

    public void undoRemove(Removed r) { player.addToQueue(r.track(), r.queueIndex()); }

    /** Queue indices of repeated tracks (same id) after the current one, highest first. The current item stays. */
    static List<Integer> duplicateIndices(List<Track> queue, int currentIndex) {
        Set<String> seen = new HashSet<>();
        List<Integer> out = new ArrayList<>();
        if (currentIndex >= 0 && currentIndex < queue.size()) seen.add(queue.get(currentIndex).id());
        for (int i = 0; i < queue.size(); i++) {
            if (i == currentIndex) continue;
            if (!seen.add(queue.get(i).id()) && i > currentIndex) out.add(i);
        }
        Collections.reverse(out);
        return out;
    }

    /** Removes the given queue indices (highest first) and returns what {@link #undoRemoveAll} needs. */
    private List<Removed> removeIndices(List<Integer> indicesHighFirst) {
        PlaybackState s = state.getValue();
        List<Removed> removed = new ArrayList<>();
        if (s == null) return removed;
        for (int idx : indicesHighFirst) {
            if (idx <= s.currentIndex() || idx >= s.queue().size()) continue;
            removed.add(new Removed(s.queue().get(idx), idx));
            player.removeFromQueue(idx);
        }
        return removed;
    }

    /** Clears "Up next" (the current song keeps playing). Empty list when there was nothing to clear. */
    public List<Removed> clearUpNext() {
        PlaybackState s = state.getValue();
        List<Integer> idx = new ArrayList<>();
        if (s != null) for (int i = s.queue().size() - 1; i > s.currentIndex(); i--) idx.add(i);
        return removeIndices(idx);
    }

    /** Removes repeated songs from Up next, keeping the first occurrence. */
    public List<Removed> removeDuplicates() {
        PlaybackState s = state.getValue();
        return s == null ? new ArrayList<>() : removeIndices(duplicateIndices(s.queue(), s.currentIndex()));
    }

    /** Re-inserts removals (lowest index first so every index is valid again). */
    public void undoRemoveAll(List<Removed> removed) {
        for (int i = removed.size() - 1; i >= 0; i--) player.addToQueue(removed.get(i).track(), removed.get(i).queueIndex());
    }

    /** Saves the whole queue (current song included, in order) as a new playlist; {@code done} gets it or null. */
    public void saveAsPlaylist(String name, Callback<Playlist> done) {
        PlaybackState s = state.getValue();
        if (library == null || s == null || s.queue().isEmpty() || name.trim().isEmpty()) { done.onResult(null); return; }
        List<Track> tracks = new ArrayList<>(s.queue());
        library.createPlaylist(name.trim(), p -> {
            if (p != null) for (Track t : tracks) library.addToPlaylist(p.id(), t.id());
            done.onResult(p);
        });
    }
    public void play(int pos) { player.skipTo(toQueueIndex(currentIndex(), pos)); }
    public void shuffle() { player.shuffleQueue(); }
    public void undoShuffle() { player.undoShuffleQueue(); }
    public void setSleepTimer(int minutes) { player.setSleepTimer(minutes); }
    public void setSleepTimerEndOfTrack() { player.setSleepTimerEndOfTrack(); }
    public void cancelSleepTimer() { player.cancelSleepTimer(); }

    public static ViewModelProvider.Factory factory(PlayerController player, @Nullable LibraryRepository library) {
        return new ViewModelProvider.Factory() {
            @NonNull @Override
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                return modelClass.cast(new QueueViewModel(player, library));
            }
        };
    }
}
