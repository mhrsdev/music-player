package com.mhrsdev.musicplayer.di

import com.mhrsdev.musicplayer.domain.repository.HomeFeedSource
import com.mhrsdev.musicplayer.domain.repository.LibraryRepository
import com.mhrsdev.musicplayer.domain.repository.LyricsRepository
import com.mhrsdev.musicplayer.domain.repository.MusicSource
import com.mhrsdev.musicplayer.domain.repository.PlayerController
import com.mhrsdev.musicplayer.fake.FakeHomeFeedSource
import com.mhrsdev.musicplayer.fake.FakeLibraryRepository
import com.mhrsdev.musicplayer.fake.FakeLyricsRepository
import com.mhrsdev.musicplayer.fake.FakeMusicSource
import com.mhrsdev.musicplayer.fake.FakePlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

/**
 * Koin bindings. T1: everything is bound to the in-memory fakes in `fake/`.
 * T8 replaces these bindings with the real `data/` + `playback/` implementations; consumers only ever
 * inject the interfaces from `domain/repository`.
 */
val appModule = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    single<LibraryRepository> { FakeLibraryRepository() }
    single<PlayerController> { FakePlayerController(scope = get()) }
    single<LyricsRepository> { FakeLyricsRepository() }
    single<HomeFeedSource> { FakeHomeFeedSource(get()) }
    single<MusicSource> { FakeMusicSource() }
}
