package com.mhrsdev.musicplayer.fake

import com.mhrsdev.musicplayer.domain.model.PlaybackState
import com.mhrsdev.musicplayer.domain.model.RepeatMode
import com.mhrsdev.musicplayer.domain.model.SleepTimer
import com.mhrsdev.musicplayer.domain.model.Track
import com.mhrsdev.musicplayer.domain.repository.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * In-memory PlayerController that simulates playback with a coroutine ticker (no audio).
 * Starts with a populated queue (paused at 0:42) so the UI has something to render on first launch.
 */
class FakePlayerController(
    private val scope: CoroutineScope,
    initialQueue: List<Track> = SampleData.tracks.take(12),
    startIndex: Int = 1,
    private val tickMs: Long = 250L,
    private val random: Random = Random(42),
) : PlayerController {

    private val _state = MutableStateFlow(
        PlaybackState(
            currentTrack = initialQueue.getOrNull(startIndex),
            isPlaying = false,
            positionMs = 42_000L.coerceAtMost(initialQueue.getOrNull(startIndex)?.durationMs ?: 0L),
            durationMs = initialQueue.getOrNull(startIndex)?.durationMs ?: 0L,
            queue = initialQueue,
            currentIndex = if (initialQueue.isEmpty()) -1 else startIndex,
            playingFrom = initialQueue.firstOrNull()?.artistName,
        ),
    )
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var ticker: Job? = null
    private var preShuffleQueue: List<Track>? = null
    private var preShuffleIndex: Int = -1

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                delay(tickMs)
                tick()
            }
        }
    }

    private fun tick() {
        val s = _state.value
        if (!s.isPlaying || s.currentTrack == null) return
        val timer = s.sleepTimer
        val newTimer = if (timer is SleepTimer.Countdown) {
            val left = timer.remainingMs - tickMs
            if (left <= 0) SleepTimer.Off else timer.copy(remainingMs = left)
        } else timer
        if (timer is SleepTimer.Countdown && newTimer is SleepTimer.Off) {
            _state.update { it.copy(isPlaying = false, sleepTimer = SleepTimer.Off) }
            return
        }
        val pos = s.positionMs + tickMs
        if (pos < s.durationMs) {
            _state.update { it.copy(positionMs = pos, sleepTimer = newTimer) }
        } else {
            onTrackEnded()
        }
    }

    private fun onTrackEnded() {
        val s = _state.value
        if (s.sleepTimer is SleepTimer.EndOfTrack) {
            _state.update { it.copy(isPlaying = false, positionMs = 0L, sleepTimer = SleepTimer.Off) }
            return
        }
        when {
            s.repeatMode == RepeatMode.ONE -> _state.update { it.copy(positionMs = 0L) }
            s.hasNext -> goTo(s.currentIndex + 1, keepPlaying = true)
            s.repeatMode == RepeatMode.ALL && s.queue.isNotEmpty() -> goTo(0, keepPlaying = true)
            else -> _state.update { it.copy(isPlaying = false, positionMs = 0L) }
        }
    }

    private fun goTo(index: Int, keepPlaying: Boolean) {
        _state.update { s ->
            val t = s.queue.getOrNull(index) ?: return@update s
            s.copy(
                currentIndex = index, currentTrack = t, durationMs = t.durationMs, positionMs = 0L,
                isPlaying = keepPlaying, isBuffering = false,
            )
        }
    }

    override fun play(tracks: List<Track>, startIndex: Int, playingFrom: String?) {
        if (tracks.isEmpty()) return
        preShuffleQueue = null
        val idx = startIndex.coerceIn(0, tracks.lastIndex)
        _state.update {
            it.copy(
                queue = tracks, currentIndex = idx, currentTrack = tracks[idx], durationMs = tracks[idx].durationMs,
                positionMs = 0L, isPlaying = true, playingFrom = playingFrom, canUndoShuffle = false,
            )
        }
        startTicker()
    }

    override fun resume() {
        if (_state.value.currentTrack == null) return
        _state.update { it.copy(isPlaying = true) }
        startTicker()
    }

    override fun pause() = _state.update { it.copy(isPlaying = false) }

    override fun togglePlayPause() = if (_state.value.isPlaying) pause() else resume()

    override fun seekTo(positionMs: Long) =
        _state.update { it.copy(positionMs = positionMs.coerceIn(0L, it.durationMs)) }

    override fun next() {
        val s = _state.value
        when {
            s.hasNext -> goTo(s.currentIndex + 1, s.isPlaying)
            s.repeatMode == RepeatMode.ALL && s.queue.isNotEmpty() -> goTo(0, s.isPlaying)
            else -> seekTo(0L)
        }
    }

    override fun previous() {
        val s = _state.value
        when {
            s.positionMs > 3_000L || !s.hasPrevious -> seekTo(0L)
            else -> goTo(s.currentIndex - 1, s.isPlaying)
        }
    }

    override fun skipTo(index: Int) = goTo(index, keepPlaying = true).also { startTicker() }

    override fun toggleShuffle() = _state.update { it.copy(shuffleEnabled = !it.shuffleEnabled) }

    override fun cycleRepeat() = _state.update {
        it.copy(
            repeatMode = when (it.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            },
        )
    }

    override fun moveQueueItem(from: Int, to: Int) {
        _state.update { s ->
            if (from !in s.queue.indices || to !in s.queue.indices || from == to) return@update s
            val q = s.queue.toMutableList()
            val moved = q.removeAt(from)
            q.add(to, moved)
            s.copy(queue = q, currentIndex = q.indexOfFirst { it.id == s.currentTrack?.id }.takeIf { s.currentTrack != null } ?: -1)
        }
    }

    override fun removeFromQueue(index: Int) {
        _state.update { s ->
            if (index !in s.queue.indices) return@update s
            val q = s.queue.toMutableList().also { it.removeAt(index) }
            when {
                q.isEmpty() -> s.copy(queue = q, currentIndex = -1, currentTrack = null, isPlaying = false, positionMs = 0L, durationMs = 0L)
                index < s.currentIndex -> s.copy(queue = q, currentIndex = s.currentIndex - 1)
                index == s.currentIndex -> {
                    val ni = s.currentIndex.coerceAtMost(q.lastIndex)
                    s.copy(queue = q, currentIndex = ni, currentTrack = q[ni], durationMs = q[ni].durationMs, positionMs = 0L)
                }
                else -> s.copy(queue = q)
            }
        }
    }

    override fun addToQueue(track: Track, index: Int?) {
        _state.update { s ->
            val at = (index ?: s.queue.size).coerceIn(0, s.queue.size)
            val q = s.queue.toMutableList().also { it.add(at, track) }
            if (s.currentTrack == null) {
                s.copy(queue = q, currentIndex = 0, currentTrack = track, durationMs = track.durationMs, positionMs = 0L)
            } else {
                s.copy(queue = q, currentIndex = if (at <= s.currentIndex) s.currentIndex + 1 else s.currentIndex)
            }
        }
    }

    override fun playNext(track: Track) = addToQueue(track, _state.value.currentIndex + 1)

    override fun shuffleQueue() {
        val s = _state.value
        if (s.queue.size < 3 || s.currentIndex < 0) return
        preShuffleQueue = s.queue
        preShuffleIndex = s.currentIndex
        val head = s.queue.take(s.currentIndex + 1)
        val tail = s.queue.drop(s.currentIndex + 1).shuffled(random)
        _state.update { it.copy(queue = head + tail, canUndoShuffle = true) }
    }

    override fun undoShuffleQueue() {
        val old = preShuffleQueue ?: return
        preShuffleQueue = null
        _state.update { s ->
            val idx = old.indexOfFirst { it.id == s.currentTrack?.id }.takeIf { it >= 0 } ?: preShuffleIndex
            s.copy(queue = old, currentIndex = idx, canUndoShuffle = false)
        }
    }

    override fun setSleepTimer(minutes: Int) {
        val ms = minutes * 60_000L
        _state.update { it.copy(sleepTimer = SleepTimer.Countdown(ms, ms)) }
    }

    override fun setSleepTimerEndOfTrack() = _state.update { it.copy(sleepTimer = SleepTimer.EndOfTrack) }

    override fun cancelSleepTimer() = _state.update { it.copy(sleepTimer = SleepTimer.Off) }
}
