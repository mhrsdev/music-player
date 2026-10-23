package com.mhrsdev.musicplayer.di;

import android.content.Context;
import com.mhrsdev.musicplayer.data.lyrics.AudioFileLocator;
import com.mhrsdev.musicplayer.data.lyrics.EmbeddedLyricsSource;
import com.mhrsdev.musicplayer.data.lyrics.LrclibClient;
import com.mhrsdev.musicplayer.data.lyrics.LyricsCacheStore;
import com.mhrsdev.musicplayer.data.lyrics.LyricsRepositoryImpl;
import com.mhrsdev.musicplayer.data.lyrics.LyricsResolver;
import com.mhrsdev.musicplayer.data.lyrics.MediaStoreFileLocator;
import com.mhrsdev.musicplayer.data.lyrics.SidecarLyricsSource;
import com.mhrsdev.musicplayer.data.lyrics.UrlConnectionFetcher;
import com.mhrsdev.musicplayer.domain.repository.LyricsRepository;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Builds the real {@link LyricsRepository} (embedded tag, sidecar file, LRCLIB, cache). {@code AppContainer} calls
 * {@link #create} once. Lyrics get their own single thread (exits after 10 s idle) so a slow HTTP call never blocks
 * the shared {@code ioExecutor}.
 */
public final class LyricsModule {
    private LyricsModule() { }

    /**
     * @param versionName app version, sent in the User-Agent as LRCLIB requests
     * @param cache       persistent cache bound to the {@code lyrics_cache} table
     * @param onlineAllowed the "lyrics online" setting, read per lookup on the lyrics thread; false = never call LRCLIB
     */
    public static LyricsRepository create(Context context, String versionName, LyricsCacheStore cache,
            BooleanSupplier onlineAllowed) {
        AudioFileLocator files = new MediaStoreFileLocator(context.getApplicationContext().getContentResolver());
        String userAgent = "MusicPlayer/" + versionName + " (https://github.com/mhrsdev/music-player)";
        LyricsResolver resolver = new LyricsResolver(
                Arrays.asList(new EmbeddedLyricsSource(files), new SidecarLyricsSource(files)),
                new LrclibClient(new UrlConnectionFetcher(userAgent)),
                cache,
                System::currentTimeMillis,
                onlineAllowed);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        executor.allowCoreThreadTimeOut(true);
        return new LyricsRepositoryImpl(resolver, executor);
    }
}
