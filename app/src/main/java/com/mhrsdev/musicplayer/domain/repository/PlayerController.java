package com.mhrsdev.musicplayer.domain.repository;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import com.mhrsdev.musicplayer.domain.model.PlaybackState;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.util.List;

/**
 * Domain-facing wrapper over the playback engine. Call from the main thread; calls never block.
 * State is observed through {@link #getState()}; the position inside it is only refreshed while playing.
 */
public interface PlayerController {

    /** Current snapshot; never null after construction (starts as {@link PlaybackState#empty()} or a seeded queue). */
    LiveData<PlaybackState> getState();

    /** Replaces the queue with {@code tracks} and starts playing at {@code startIndex}. */
    void play(List<Track> tracks, int startIndex, @Nullable String playingFrom);

    /** Resumes the current queue. */
    void resume();

    void pause();

    void togglePlayPause();

    void seekTo(long positionMs);

    void next();

    /** Restarts the track when more than 3 s in, otherwise goes to the previous item. */
    void previous();

    void skipTo(int index);

    void toggleShuffle();

    /** OFF -> ALL -> ONE -> OFF. */
    void cycleRepeat();

    void moveQueueItem(int from, int to);

    void removeFromQueue(int index);

    /** Appends to the queue. */
    void addToQueue(Track track);

    /** Inserts at {@code index} (clamped). Also used to undo a removal. */
    void addToQueue(Track track, int index);

    /** Inserts right after the current item. */
    void playNext(Track track);

    /** Shuffles the upcoming items (current stays) and sets {@code canUndoShuffle}. */
    void shuffleQueue();

    void undoShuffleQueue();

    void setSleepTimer(int minutes);

    void setSleepTimerEndOfTrack();

    void cancelSleepTimer();
}
