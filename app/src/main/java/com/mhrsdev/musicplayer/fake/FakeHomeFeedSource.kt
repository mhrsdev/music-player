package com.mhrsdev.musicplayer.fake

import com.mhrsdev.musicplayer.domain.model.HomeItem
import com.mhrsdev.musicplayer.domain.model.HomeSection
import com.mhrsdev.musicplayer.domain.model.HomeSectionStyle
import com.mhrsdev.musicplayer.domain.repository.HomeFeedSource
import com.mhrsdev.musicplayer.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Builds Home sections from the library: New releases (recently added), Throwbacks (oldest years), Quick picks. */
class FakeHomeFeedSource(private val library: LibraryRepository) : HomeFeedSource {
    override fun sections(): Flow<List<HomeSection>> =
        combine(library.albums, library.recentlySaved, library.history, library.playlists) { albums, recent, history, playlists ->
            val newest = albums.sortedByDescending { it.year ?: 0 }
            val oldest = albums.sortedBy { it.year ?: Int.MAX_VALUE }
            val quick = (history + recent).distinctBy { it.id }.take(8)
            listOf(
                HomeSection("new-releases", "New releases", "Fresh in your library",
                    HomeSectionStyle.HERO_CAROUSEL, newest.take(6).map { HomeItem.OfAlbum(it) }),
                HomeSection("throwbacks", "Throwbacks", "From a while ago",
                    HomeSectionStyle.CAROUSEL, oldest.take(6).map { HomeItem.OfAlbum(it) }),
                HomeSection("playlists", "Your playlists", null,
                    HomeSectionStyle.CAROUSEL, playlists.map { HomeItem.OfPlaylist(it) }),
                HomeSection("quick-picks", "Quick picks", "Start radio from a song",
                    HomeSectionStyle.LIST, quick.map { HomeItem.OfTrack(it) }),
            ).filter { it.items.isNotEmpty() }
        }
}
