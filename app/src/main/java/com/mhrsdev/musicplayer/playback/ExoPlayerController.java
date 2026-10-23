package com.mhrsdev.musicplayer.playback;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import com.mhrsdev.musicplayer.domain.model.PlaybackState;
import com.mhrsdev.musicplayer.domain.model.RepeatMode;
import com.mhrsdev.musicplayer.domain.model.SleepTimer;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.repository.PlayerController;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Real {@link PlayerController} on top of the process-wide ExoPlayer ({@link PlayerHolder}). Main thread only.
 *
 * <p>The player is the single source of truth: the queue is decoded from its MediaItems ({@link TrackMapper}) and
 * the {@link PlaybackState} is rebuilt from player events. The only timers are single {@link Handler} callbacks:
 * the position tick (only while playing AND someone observes the state, 4 Hz), one timeout for the play-count
 * threshold and one for the sleep timer; all idle while paused.
 */
@OptIn(markerClass = UnstableApi.class)
public final class ExoPlayerController implements PlayerController {
    static final long TICK_MS = 250L;

    private final Context appContext;
    private final ExoPlayer player;
    private final PlaybackStore store;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StateData state = new StateData();
    private final QueueShuffler<Track> shuffler = new QueueShuffler<>();
    private final SleepTimerLogic sleep = new SleepTimerLogic();
    private final PlayCountTracker playCount = new PlayCountTracker();
    private final Random random = new Random();
    private final Runnable tickTask = this::onTick;
    private final Runnable sleepTask = this::onSleepWake;
    private final Runnable playCountTask = this::onPlayCountWake;
    @Nullable private final PlayCountListener playCountListener;
    @Nullable private final Consumer<Track> nextTrackListener;
    @Nullable private String notifiedNextId;

    private List<Track> queue = Collections.emptyList();
    private int consecutiveErrors;

    /**
     * @param playCountListener told once per real listen of a track (wire to {@code LibraryRepository.recordPlay})
     * @param nextTrackListener told, while playing, whenever the track queued next changes (e.g. to prefetch its
     *                          waveform); event-driven, adds no timers or observers
     */
    public ExoPlayerController(Context context, @Nullable PlayCountListener playCountListener,
            @Nullable Consumer<Track> nextTrackListener) {
        this.appContext = context.getApplicationContext();
        this.playCountListener = playCountListener;
        this.nextTrackListener = nextTrackListener;
        player = PlayerHolder.get(appContext);
        store = PlayerHolder.store(appContext);
        queue = readQueue();
        startPlayCount();
        player.addListener(new PlayerEvents());
        state.setValue(buildState());
    }

    @Override public LiveData<PlaybackState> getState() {
        return state;
    }

    // --- transport ----------------------------------------------------------------------------

    @Override public void play(List<Track> tracks, int startIndex, @Nullable String playingFrom) {
        List<MediaItem> items = new ArrayList<>(tracks.size());
        int start = 0;
        for (int i = 0; i < tracks.size(); i++) {
            if (!TrackMapper.isPlayable(tracks.get(i))) continue;
            if (i < startIndex) start++;
            items.add(TrackMapper.toMediaItem(tracks.get(i)));
        }
        if (items.isEmpty()) return;
        shuffler.clear();
        store.setPlayingFrom(playingFrom);
        ensureService();
        player.setMediaItems(items, Math.min(start, items.size() - 1), 0L);
        player.prepare();
        player.play();
    }

    @Override public void resume() {
        if (player.getMediaItemCount() == 0) return;
        ensureService();
        prepareIfIdle();
        if (player.getPlaybackState() == Player.STATE_ENDED) player.seekToDefaultPosition();
        player.play();
    }

    @Override public void pause() {
        player.pause();
    }

    @Override public void togglePlayPause() {
        if (isPlaying()) pause(); else resume();
    }

    @Override public void seekTo(long positionMs) {
        player.seekTo(Math.max(0L, positionMs));
    }

    @Override public void next() {
        prepareIfIdle();
        if (player.hasNextMediaItem()) player.seekToNextMediaItem(); else player.seekToDefaultPosition();
    }

    /** ExoPlayer's own rule matches the contract: restart when more than 3 s in, else previous item. */
    @Override public void previous() {
        prepareIfIdle();
        player.seekToPrevious();
    }

    @Override public void skipTo(int index) {
        if (index < 0 || index >= player.getMediaItemCount()) return;
        ensureService();
        prepareIfIdle();
        player.seekToDefaultPosition(index);
        player.play();
    }

    @Override public void toggleShuffle() {
        player.setShuffleModeEnabled(!player.getShuffleModeEnabled());
    }

    @Override public void cycleRepeat() {
        switch (player.getRepeatMode()) {
            case Player.REPEAT_MODE_OFF: player.setRepeatMode(Player.REPEAT_MODE_ALL); break;
            case Player.REPEAT_MODE_ALL: player.setRepeatMode(Player.REPEAT_MODE_ONE); break;
            default: player.setRepeatMode(Player.REPEAT_MODE_OFF);
        }
    }

    // --- queue --------------------------------------------------------------------------------

    @Override public void moveQueueItem(int from, int to) {
        int count = player.getMediaItemCount();
        if (from < 0 || to < 0 || from >= count || to >= count || from == to) return;
        shuffler.clear();
        player.moveMediaItem(from, to);
    }

    @Override public void removeFromQueue(int index) {
        if (index < 0 || index >= player.getMediaItemCount()) return;
        shuffler.clear();
        player.removeMediaItem(index);
    }

    @Override public void addToQueue(Track track) {
        addToQueue(track, player.getMediaItemCount());
    }

    @Override public void addToQueue(Track track, int index) {
        if (!TrackMapper.isPlayable(track)) return;
        boolean wasEmpty = player.getMediaItemCount() == 0;
        shuffler.clear();
        player.addMediaItem(Math.max(0, Math.min(index, player.getMediaItemCount())), TrackMapper.toMediaItem(track));
        if (wasEmpty) player.prepare(); // paused, ready to resume
    }

    @Override public void playNext(Track track) {
        addToQueue(track, queue.isEmpty() ? 0 : player.getCurrentMediaItemIndex() + 1);
    }

    @Override public void shuffleQueue() {
        int current = player.getCurrentMediaItemIndex();
        List<Track> shuffled = shuffler.shuffle(queue, current, random);
        if (shuffled == null) return;
        applyOrder(shuffled, current);
        publish();
    }

    @Override public void undoShuffleQueue() {
        List<Track> previous = shuffler.undo();
        if (previous != null && previous.size() == player.getMediaItemCount()) {
            int current = previous.indexOf(currentTrack());
            if (current >= 0) applyOrder(previous, current);
        }
        publish();
    }

    /**
     * Reorders the player's queue to {@code target} without touching the playing item, which must be
     * {@code target.get(targetCurrent)}: first the items after it are replaced, then the ones before it.
     */
    private void applyOrder(List<Track> target, int targetCurrent) {
        int current = player.getCurrentMediaItemIndex();
        player.replaceMediaItems(current + 1, player.getMediaItemCount(), toItems(target.subList(targetCurrent + 1, target.size())));
        if (current > 0 || targetCurrent > 0) {
            player.replaceMediaItems(0, current, toItems(target.subList(0, targetCurrent)));
        }
    }

    private static List<MediaItem> toItems(List<Track> tracks) {
        List<MediaItem> items = new ArrayList<>(tracks.size());
        for (Track t : tracks) items.add(TrackMapper.toMediaItem(t));
        return items;
    }

    // --- sleep timer --------------------------------------------------------------------------

    @Override public void setSleepTimer(int minutes) {
        if (minutes <= 0) {
            cancelSleepTimer();
            return;
        }
        player.setPauseAtEndOfMediaItems(false);
        player.setVolume(1f);
        sleep.startCountdown(minutes * 60_000L, player.isPlaying(), now());
        scheduleSleep();
        publish();
    }

    @Override public void setSleepTimerEndOfTrack() {
        sleep.startEndOfTrack();
        handler.removeCallbacks(sleepTask);
        player.setVolume(1f);
        player.setPauseAtEndOfMediaItems(true); // ExoPlayer pauses itself at the end of the item: no polling
        publish();
    }

    @Override public void cancelSleepTimer() {
        sleep.cancel();
        handler.removeCallbacks(sleepTask);
        player.setPauseAtEndOfMediaItems(false);
        player.setVolume(1f);
        publish();
    }

    private void scheduleSleep() {
        handler.removeCallbacks(sleepTask);
        long delay = sleep.nextWakeMs(now());
        if (delay >= 0) handler.postDelayed(sleepTask, delay);
    }

    private void onSleepWake() {
        long now = now();
        if (sleep.isExpired(now)) {
            sleep.cancel();
            player.pause();
            player.setVolume(1f);
            publish();
            return;
        }
        player.setVolume(sleep.volume(now)); // fade-out during the last seconds
        scheduleSleep();
    }

    // --- play count ---------------------------------------------------------------------------

    private void startPlayCount() {
        Track t = currentTrack();
        playCount.start(t == null ? null : t.id(), t == null ? 0L : t.durationMs());
        playCount.setPlaying(player.isPlaying(), now());
        schedulePlayCount();
    }

    private void schedulePlayCount() {
        handler.removeCallbacks(playCountTask);
        long delay = playCount.msUntilCount(now());
        if (delay >= 0) handler.postDelayed(playCountTask, delay);
    }

    private void onPlayCountWake() {
        String trackId = playCount.claim(now());
        if (trackId != null && playCountListener != null) playCountListener.onTrackPlayed(trackId);
        schedulePlayCount();
    }

    // --- state --------------------------------------------------------------------------------

    private void onTick() {
        publish();
        scheduleTick();
    }

    private void scheduleTick() {
        handler.removeCallbacks(tickTask);
        if (state.hasActiveObservers() && player.isPlaying()) handler.postDelayed(tickTask, TICK_MS);
    }

    private boolean isPlaying() {
        return player.isPlaying()
                || (player.getPlayWhenReady() && player.getPlaybackState() == Player.STATE_BUFFERING);
    }

    private void prepareIfIdle() {
        if (player.getPlaybackState() == Player.STATE_IDLE && player.getMediaItemCount() > 0) player.prepare();
    }

    /** Starts the service (notification, media buttons). */
    private void ensureService() {
        try {
            appContext.startService(new Intent(appContext, PlaybackService.class));
        } catch (IllegalStateException e) {
            // Background-start restriction: playback still works, only the notification is missing.
        }
    }

    private static long now() {
        return SystemClock.uptimeMillis();
    }

    @Nullable private Track currentTrack() {
        int index = player.getCurrentMediaItemIndex();
        return index >= 0 && index < queue.size() ? queue.get(index) : null;
    }

    private List<Track> readQueue() {
        int count = player.getMediaItemCount();
        List<Track> tracks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Track t = TrackMapper.fromMediaItem(player.getMediaItemAt(i));
            if (t != null) tracks.add(t);
        }
        return tracks;
    }

    private void publish() {
        state.setValue(buildState());
    }

    private PlaybackState buildState() {
        long now = now();
        Track current = currentTrack();
        long duration = player.getDuration();
        if (duration == C.TIME_UNSET || duration <= 0) duration = current == null ? 0L : current.durationMs();
        long position = current == null ? 0L : Math.max(0L, player.getCurrentPosition());
        if (duration > 0) position = Math.min(position, duration);
        return new PlaybackState(current, isPlaying(),
                player.getPlayWhenReady() && player.getPlaybackState() == Player.STATE_BUFFERING,
                position, duration, player.getShuffleModeEnabled(), repeatMode(), queue,
                current == null ? -1 : player.getCurrentMediaItemIndex(), sleep.snapshot(now),
                queue.isEmpty() ? null : store.playingFrom(), shuffler.canUndo());
    }

    /** Only while playing (nothing is prefetched for a paused player) and only when the next track changed. */
    private void notifyNextTrack() {
        if (nextTrackListener == null || !player.isPlaying()) return;
        int index = player.getNextMediaItemIndex();
        Track next = index >= 0 && index < queue.size() ? queue.get(index) : null;
        if (next == null || next.id().equals(notifiedNextId)) return;
        notifiedNextId = next.id();
        nextTrackListener.accept(next);
    }

    private RepeatMode repeatMode() {
        switch (player.getRepeatMode()) {
            case Player.REPEAT_MODE_ALL: return RepeatMode.ALL;
            case Player.REPEAT_MODE_ONE: return RepeatMode.ONE;
            default: return RepeatMode.OFF;
        }
    }

    /** LiveData that ticks (and flushes persistence) only while observed. */
    private final class StateData extends MutableLiveData<PlaybackState> {
        @Override protected void onActive() {
            publish();
            scheduleTick();
        }

        @Override protected void onInactive() {
            handler.removeCallbacks(tickTask);
            store.saveNow(); // UI left: persist the position, the only thing ticks would have changed
        }
    }

    /** Individual callbacks run before {@code onEvents}, so the bookkeeping is done before the state is published. */
    private final class PlayerEvents implements Player.Listener {
        @Override public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            startPlayCount();
        }

        @Override public void onIsPlayingChanged(boolean isPlaying) {
            if (isPlaying) consecutiveErrors = 0;
            long now = now();
            playCount.setPlaying(isPlaying, now);
            schedulePlayCount();
            sleep.setPlaying(isPlaying, now);
            scheduleSleep();
            scheduleTick();
        }

        @Override public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
                    && sleep.kind() == SleepTimer.Kind.END_OF_TRACK) {
                sleep.cancel(); // the end-of-track pause just happened
                player.setPauseAtEndOfMediaItems(false);
            }
        }

        /** Unreadable file: skip to the next one, but never loop forever over a fully broken queue. */
        @Override public void onPlayerError(PlaybackException error) {
            if (++consecutiveErrors <= player.getMediaItemCount() && player.hasNextMediaItem()) {
                player.seekToNextMediaItem();
                player.prepare();
                player.play();
            }
        }

        @Override public void onTimelineChanged(Timeline timeline, int reason) {
            queue = readQueue(); // before onMediaItemTransition, which looks up the current track
        }

        @Override public void onEvents(Player p, Player.Events events) {
            publish();
            notifyNextTrack();
        }
    }
}
