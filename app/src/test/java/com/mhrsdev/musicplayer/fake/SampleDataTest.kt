package com.mhrsdev.musicplayer.fake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleDataTest {
    @Test fun hasRealisticVolume() {
        assertTrue(SampleData.tracks.size >= 30)
        assertTrue(SampleData.artists.size >= 5)
        assertTrue(SampleData.albums.size >= 5)
        assertTrue(SampleData.artists.any { it.name == "NEFFEX" })
    }

    @Test fun idsAreUniqueAndReferencesResolve() {
        assertEquals(SampleData.tracks.size, SampleData.tracks.map { it.id }.toSet().size)
        val albumIds = SampleData.albums.map { it.id }.toSet()
        val artistIds = SampleData.artists.map { it.id }.toSet()
        SampleData.tracks.forEach {
            assertTrue(it.albumId in albumIds)
            assertTrue(it.artistId in artistIds)
        }
        SampleData.albums.forEach {
            assertEquals(it.trackCount, SampleData.tracks.count { t -> t.albumId == it.id })
        }
        SampleData.artists.forEach {
            assertEquals(it.trackCount, SampleData.tracks.count { t -> t.artistId == it.id })
        }
    }

    @Test fun seedPlaylistsAndHistoryReferenceExistingTracks() {
        val ids = SampleData.tracks.map { it.id }.toSet()
        SampleData.seedPlaylists.forEach { (_, trackIds) -> assertTrue(ids.containsAll(trackIds)) }
        assertTrue(ids.containsAll(SampleData.seedHistoryIds))
        assertTrue(ids.containsAll(SampleData.initiallyLikedIds))
    }

    @Test fun lyricsVariants() {
        val synced = SampleData.lyricsFor("track-1")!!
        assertTrue(synced.isSynced)
        assertEquals(synced.lines.sortedBy { it.timeMs }, synced.lines)
        assertEquals(false, SampleData.lyricsFor("track-3")!!.isSynced)
        assertNull(SampleData.lyricsFor("track-4"))
        assertNotNull(SampleData.lyricsFor("track-34"))
    }
}
