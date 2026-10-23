package com.mhrsdev.musicplayer.di;

import android.content.Context;
import com.mhrsdev.musicplayer.domain.repository.HomeFeedSource;
import com.mhrsdev.musicplayer.domain.repository.LibraryRepository;
import com.mhrsdev.musicplayer.domain.repository.LyricsRepository;
import com.mhrsdev.musicplayer.domain.repository.MusicSource;
import com.mhrsdev.musicplayer.domain.repository.PlayerController;
import com.mhrsdev.musicplayer.domain.repository.WaveformRepository;
import com.mhrsdev.musicplayer.fake.FakeHomeFeedSource;
import com.mhrsdev.musicplayer.fake.FakeLibraryRepository;
import com.mhrsdev.musicplayer.fake.FakeLyricsRepository;
import com.mhrsdev.musicplayer.fake.FakeMusicSource;
import com.mhrsdev.musicplayer.fake.FakePlayerController;
import com.mhrsdev.musicplayer.fake.FakeWaveformRepository;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Hand-written dependency container (plain Java DI), created once by {@code MusicPlayerApp}.
 * Screens never construct repositories; they read them from here. Today everything is bound to the in-memory
 * fakes; the data/playback tasks swap these assignments for the real implementations.
 */
public final class AppContainer {
    /** Shared background thread for disk / DB / network work. Its thread exits after 30 s idle (battery, RAM). */
    public final Executor ioExecutor;
    public final LibraryRepository library;
    public final PlayerController player;
    public final LyricsRepository lyrics;
    public final WaveformRepository waveform;
    public final HomeFeedSource homeFeed;
    public final MusicSource musicSource;

    /** @param appContext application context, needed by the real (non-fake) implementations */
    public AppContainer(Context appContext) {
        ThreadPoolExecutor io = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        io.allowCoreThreadTimeOut(true);
        ioExecutor = io;

        library = new FakeLibraryRepository();
        player = new FakePlayerController();
        lyrics = new FakeLyricsRepository();
        waveform = new FakeWaveformRepository();
        homeFeed = new FakeHomeFeedSource(library);
        musicSource = new FakeMusicSource();
    }
}
