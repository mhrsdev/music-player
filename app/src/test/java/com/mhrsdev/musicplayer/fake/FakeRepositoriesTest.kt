package com.mhrsdev.musicplayer.fake

import com.mhrsdev.musicplayer.domain.model.HomeSectionStyle
import com.mhrsdev.musicplayer.domain.model.SearchFilter
import com.mhrsdev.musicplayer.domain.model.SearchResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeRepositoriesTest {
    private val repo = FakeLibraryRepository()

    @Test fun likedTracksMatchSeed() = runTest {
        assertEquals(SampleData.initiallyLikedIds, repo.likedTracks.first().map { it.id }.toSet())
    }

    @Test fun toggleLikeUpdatesFlows() = runTest {
        repo.toggleLike("track-5")
        assertTrue(repo.likedTracks.first().any { it.id == "track-5" })
        repo.toggleLike("track-5")
        assertFalse(repo.likedTracks.first().any { it.id == "track-5" })
    }

    @Test fun searchFiltersByType() = runTest {
        val songs = repo.search("neffex", SearchFilter.SONGS).first()
        assertTrue(songs.isNotEmpty() && songs.all { it is SearchResult.OfTrack })
        val artists = repo.search("neffex", SearchFilter.ARTISTS).first()
        assertEquals(1, artists.size)
        assertTrue(repo.search("   ", SearchFilter.ALL).first().isEmpty())
    }

    @Test fun recordPlayMovesToFrontOfHistory() = runTest {
        repo.recordPlay("track-30")
        assertEquals("track-30", repo.history.first().first().id)
    }

    @Test fun playlistCrud() = runTest {
        val p = repo.createPlaylist("Mine")
        repo.addToPlaylist(p.id, "track-1")
        repo.addToPlaylist(p.id, "track-1")
        assertEquals(1, repo.playlistTracks(p.id).first().size)
        assertEquals(1, repo.playlists.first().first { it.id == p.id }.trackCount)
    }

    @Test fun homeFeedHasExpectedSections() = runTest {
        val sections = FakeHomeFeedSource(repo).sections().first()
        assertEquals(listOf("new-releases", "throwbacks", "playlists", "quick-picks"), sections.map { it.id })
        assertEquals(HomeSectionStyle.HERO_CAROUSEL, sections.first().style)
        assertTrue(sections.all { it.items.isNotEmpty() })
    }

    @Test fun lyricsRepositoryEmitsSamples() = runTest {
        val lyrics = FakeLyricsRepository()
        val t = SampleData.tracks.first()
        assertTrue(lyrics.lyricsFor(t).first()!!.isSynced)
        assertEquals(null, lyrics.lyricsFor(SampleData.tracks[3]).first())
    }
}
