package com.mhrsdev.musicplayer.ui.queue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.mhrsdev.musicplayer.domain.model.PlaybackState;
import com.mhrsdev.musicplayer.domain.model.SleepTimer;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.repository.PlayerController;
import com.mhrsdev.musicplayer.ui.player.PlayerFormat;

import java.util.Collections;
import java.util.List;

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

    public static final int[] SLEEP_PRESETS_MIN = {15, 30, 45, 60};

    private final PlayerController player;
    private final LiveData<PlaybackState> state;
    private final LiveData<UiState> ui;

    QueueViewModel(PlayerController player) {
        this.player = player;
        this.state = player.getState();
        this.ui = Transformations.map(state, QueueViewModel::map);
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
    public void play(int pos) { player.skipTo(toQueueIndex(currentIndex(), pos)); }
    public void shuffle() { player.shuffleQueue(); }
    public void undoShuffle() { player.undoShuffleQueue(); }
    public void setSleepTimer(int minutes) { player.setSleepTimer(minutes); }
    public void setSleepTimerEndOfTrack() { player.setSleepTimerEndOfTrack(); }
    public void cancelSleepTimer() { player.cancelSleepTimer(); }

    public static ViewModelProvider.Factory factory(PlayerController player) {
        return new ViewModelProvider.Factory() {
            @NonNull @Override
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                return modelClass.cast(new QueueViewModel(player));
            }
        };
    }
}
