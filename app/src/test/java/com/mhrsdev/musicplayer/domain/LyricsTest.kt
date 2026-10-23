package com.mhrsdev.musicplayer.domain

import com.mhrsdev.musicplayer.domain.model.LyricLine
import com.mhrsdev.musicplayer.domain.model.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsTest {
    private val lyrics = Lyrics(
        "t", listOf(LyricLine(1_000, "a"), LyricLine(5_000, "b"), LyricLine(9_000, "c")), isSynced = true,
    )

    @Test fun activeLineBeforeFirstIsMinusOne() = assertEquals(-1, lyrics.activeLineIndex(999))
    @Test fun activeLineAtBoundary() = assertEquals(1, lyrics.activeLineIndex(5_000))
    @Test fun activeLineBetween() = assertEquals(1, lyrics.activeLineIndex(8_999))
    @Test fun activeLineAfterLast() = assertEquals(2, lyrics.activeLineIndex(1_000_000))
    @Test fun unsyncedHasNoActiveLine() =
        assertEquals(-1, lyrics.copy(isSynced = false).activeLineIndex(6_000))
}
