package com.mhrsdev.musicplayer.fake

import com.mhrsdev.musicplayer.domain.model.RepeatMode
import com.mhrsdev.musicplayer.domain.model.SleepTimer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakePlayerControllerTest {
    private val tracks = SampleData.tracks.take(5)

    @Test fun startsPausedWithQueue() = runTest {
        val c = FakePlayerController(backgroundScope, tracks, startIndex = 1)
        assertEquals(tracks[1], c.state.value.currentTrack)
        assertFalse(c.state.value.isPlaying)
        assertEquals(5, c.state.value.queue.size)
    }

    @Test fun playAdvancesPositionAndTracksEnd() = runTest {
        val c = FakePlayerController(backgroundScope, tracks, startIndex = 0)
        c.play(tracks, 0, "Test")
        c.seekTo(tracks[0].durationMs - 1_000)
        advanceTimeBy(1_500)
        runCurrent()
        assertEquals(1, c.state.value.currentIndex)
        assertTrue(c.state.value.isPlaying)
        assertEquals("Test", c.state.value.playingFrom)
    }

    @Test fun repeatCyclesOffAllOne() = runTest {
        val c = FakePlayerController(backgroundScope, tracks)
        c.cycleRepeat(); assertEquals(RepeatMode.ALL, c.state.value.repeatMode)
        c.cycleRepeat(); assertEquals(RepeatMode.ONE, c.state.value.repeatMode)
        c.cycleRepeat(); assertEquals(RepeatMode.OFF, c.state.value.repeatMode)
    }

    @Test fun moveAndRemoveKeepCurrentTrack() = runTest {
        val c = FakePlayerController(backgroundScope, tracks, startIndex = 2)
        val current = c.state.value.currentTrack
        c.moveQueueItem(0, 4)
        assertEquals(current, c.state.value.currentTrack)
        assertEquals(current, c.state.value.queue[c.state.value.currentIndex])
        c.removeFromQueue(0)
        assertEquals(4, c.state.value.queue.size)
        assertEquals(current, c.state.value.queue[c.state.value.currentIndex])
    }

    @Test fun shuffleThenUndoRestoresOrder() = runTest {
        val c = FakePlayerController(backgroundScope, SampleData.tracks.take(10), startIndex = 2)
        val before = c.state.value.queue
        c.shuffleQueue()
        assertTrue(c.state.value.canUndoShuffle)
        assertEquals(before.take(3), c.state.value.queue.take(3))
        assertEquals(before.toSet(), c.state.value.queue.toSet())
        c.undoShuffleQueue()
        assertEquals(before, c.state.value.queue)
        assertFalse(c.state.value.canUndoShuffle)
    }

    @Test fun sleepTimerEndOfTrackStopsPlayback() = runTest {
        val c = FakePlayerController(backgroundScope, tracks, startIndex = 0)
        c.play(tracks, 0)
        c.setSleepTimerEndOfTrack()
        c.seekTo(tracks[0].durationMs - 500)
        advanceTimeBy(1_000)
        runCurrent()
        assertFalse(c.state.value.isPlaying)
        assertEquals(SleepTimer.Off, c.state.value.sleepTimer)
    }

    @Test fun playNextInsertsAfterCurrent() = runTest {
        val c = FakePlayerController(backgroundScope, tracks, startIndex = 1)
        val extra = SampleData.tracks[20]
        c.playNext(extra)
        assertEquals(extra, c.state.value.queue[2])
        assertEquals(1, c.state.value.currentIndex)
    }
}
