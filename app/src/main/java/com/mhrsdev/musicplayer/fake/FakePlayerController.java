package com.mhrsdev.musicplayer.fake;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.mhrsdev.musicplayer.domain.model.PlaybackState;
import com.mhrsdev.musicplayer.domain.model.RepeatMode;
import com.mhrsdev.musicplayer.domain.model.SleepTimer;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.repository.PlayerController;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * In-memory {@link PlayerController} that simulates playback (no audio). Main thread only.
 * A {@link Handler} ticker runs only while playing, so a paused fake costs nothing. Starts paused at 0:42
 * with a populated queue so the UI has something to render on first launch.
 */
public final class FakePlayerController implements PlayerController {
    static final long TICK_MS = 250L;

    private final MutableLiveData<PlaybackState> state = new MutableLiveData<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tickTask = this::onTick;
    private final Random random = new Random(42);

    private List<Track> queue;
    private int index;
    private boolean playing;
    private long positionMs;
    private boolean shuffleEnabled;
    private RepeatMode repeatMode = RepeatMode.OFF;
    private SleepTimer sleepTimer = SleepTimer.OFF;
    @Nullable private String playingFrom;
    @Nullable private List<Track> preShuffleQueue;
    private int preShuffleIndex = -1;

    public FakePlayerController() {
        this(SampleData.TRACKS.subList(0, 12), 1);
    }

    public FakePlayerController(List<Track> initialQueue, int startIndex) {
        queue = new ArrayList<>(initialQueue);
        index = queue.isEmpty() ? -1 : startIndex;
        positionMs = Math.min(42_000L, duration());
        playingFrom = queue.isEmpty() ? null : queue.get(0).artistName();
        publish();
    }

    @Override public LiveData<PlaybackState> getState() {
        return state;
    }

    @Nullable private Track current() {
        return index >= 0 && index < queue.size() ? queue.get(index) : null;
    }

    private long duration() {
        Track t = current();
        return t == null ? 0L : t.durationMs();
    }

    private void publish() {
        state.setValue(new PlaybackState(current(), playing, false, positionMs, duration(), shuffleEnabled,
                repeatMode, queue, index, sleepTimer, playingFrom, preShuffleQueue != null));
    }

    // --- ticker -------------------------------------------------------------------------------

    private void startTicker() {
        handler.removeCallbacks(tickTask);
        handler.postDelayed(tickTask, TICK_MS);
    }

    private void onTick() {
        tick();
        if (playing) handler.postDelayed(tickTask, TICK_MS);
    }

    /** One simulated time step; package-private so tests can drive time without a Looper. */
    void tick() {
        if (!playing || current() == null) return;
        if (sleepTimer.kind() == SleepTimer.Kind.COUNTDOWN) {
            long left = sleepTimer.remainingMs() - TICK_MS;
            if (left <= 0) {
                playing = false;
                sleepTimer = SleepTimer.OFF;
                publish();
                return;
            }
            sleepTimer = SleepTimer.countdown(left, sleepTimer.totalMs());
        }
        positionMs += TICK_MS;
        if (positionMs >= duration()) onTrackEnded();
        publish();
    }

    private void onTrackEnded() {
        if (sleepTimer.kind() == SleepTimer.Kind.END_OF_TRACK) {
            playing = false;
            positionMs = 0L;
            sleepTimer = SleepTimer.OFF;
        } else if (repeatMode == RepeatMode.ONE) {
            positionMs = 0L;
        } else if (hasNext()) {
            goTo(index + 1, true);
        } else if (repeatMode == RepeatMode.ALL && !queue.isEmpty()) {
            goTo(0, true);
        } else {
            playing = false;
            positionMs = 0L;
        }
    }

    private boolean hasNext() {
        return index >= 0 && index < queue.size() - 1;
    }

    private void goTo(int newIndex, boolean keepPlaying) {
        if (newIndex < 0 || newIndex >= queue.size()) return;
        index = newIndex;
        positionMs = 0L;
        playing = keepPlaying;
    }

    // --- transport ----------------------------------------------------------------------------

    @Override public void play(List<Track> tracks, int startIndex, @Nullable String playingFrom) {
        if (tracks.isEmpty()) return;
        preShuffleQueue = null;
        queue = new ArrayList<>(tracks);
        this.playingFrom = playingFrom;
        goTo(Math.max(0, Math.min(startIndex, queue.size() - 1)), true);
        publish();
        startTicker();
    }

    @Override public void resume() {
        if (current() == null) return;
        playing = true;
        publish();
        startTicker();
    }

    @Override public void pause() {
        playing = false;
        handler.removeCallbacks(tickTask);
        publish();
    }

    @Override public void togglePlayPause() {
        if (playing) pause(); else resume();
    }

    @Override public void seekTo(long positionMs) {
        this.positionMs = Math.max(0L, Math.min(positionMs, duration()));
        publish();
    }

    @Override public void next() {
        if (hasNext()) goTo(index + 1, playing);
        else if (repeatMode == RepeatMode.ALL && !queue.isEmpty()) goTo(0, playing);
        else positionMs = 0L;
        publish();
    }

    @Override public void previous() {
        if (positionMs > 3_000L || index <= 0) positionMs = 0L;
        else goTo(index - 1, playing);
        publish();
    }

    @Override public void skipTo(int newIndex) {
        goTo(newIndex, true);
        publish();
        startTicker();
    }

    @Override public void toggleShuffle() {
        shuffleEnabled = !shuffleEnabled;
        publish();
    }

    @Override public void cycleRepeat() {
        switch (repeatMode) {
            case OFF: repeatMode = RepeatMode.ALL; break;
            case ALL: repeatMode = RepeatMode.ONE; break;
            default: repeatMode = RepeatMode.OFF;
        }
        publish();
    }

    // --- queue --------------------------------------------------------------------------------

    @Override public void moveQueueItem(int from, int to) {
        if (from < 0 || to < 0 || from >= queue.size() || to >= queue.size() || from == to) return;
        queue = new ArrayList<>(queue);
        queue.add(to, queue.remove(from));
        if (from == index) index = to;
        else if (from < index && to >= index) index--;
        else if (from > index && to <= index) index++;
        publish();
    }

    @Override public void removeFromQueue(int at) {
        if (at < 0 || at >= queue.size()) return;
        queue = new ArrayList<>(queue);
        queue.remove(at);
        if (queue.isEmpty()) {
            index = -1;
            playing = false;
            positionMs = 0L;
        } else if (at < index) {
            index--;
        } else if (at == index) {
            index = Math.min(index, queue.size() - 1);
            positionMs = 0L;
        }
        publish();
    }

    @Override public void addToQueue(Track track) {
        addToQueue(track, queue.size());
    }

    @Override public void addToQueue(Track track, int at) {
        int pos = Math.max(0, Math.min(at, queue.size()));
        queue = new ArrayList<>(queue);
        queue.add(pos, track);
        if (index < 0) {
            index = 0;
            positionMs = 0L;
        } else if (pos <= index) {
            index++;
        }
        publish();
    }

    @Override public void playNext(Track track) {
        addToQueue(track, index + 1);
    }

    @Override public void shuffleQueue() {
        if (queue.size() < 3 || index < 0) return;
        preShuffleQueue = queue;
        preShuffleIndex = index;
        List<Track> shuffled = new ArrayList<>(queue.subList(0, index + 1));
        List<Track> tail = new ArrayList<>(queue.subList(index + 1, queue.size()));
        Collections.shuffle(tail, random);
        shuffled.addAll(tail);
        queue = shuffled;
        publish();
    }

    @Override public void undoShuffleQueue() {
        if (preShuffleQueue == null) return;
        int restored = preShuffleQueue.indexOf(current());
        queue = preShuffleQueue;
        index = restored >= 0 ? restored : preShuffleIndex;
        preShuffleQueue = null;
        publish();
    }

    // --- sleep timer --------------------------------------------------------------------------

    @Override public void setSleepTimer(int minutes) {
        long ms = minutes * 60_000L;
        sleepTimer = SleepTimer.countdown(ms, ms);
        publish();
    }

    @Override public void setSleepTimerEndOfTrack() {
        sleepTimer = SleepTimer.END_OF_TRACK;
        publish();
    }

    @Override public void cancelSleepTimer() {
        sleepTimer = SleepTimer.OFF;
        publish();
    }
}
