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
import com.mhrsdev.musicplayer.data.lyrics.UserLyricsStore;
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
public class LyricsModule {
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
        UserLyricsStore userStore = new UserLyricsStore(new java.io.File(context.getFilesDir(), "lyrics"));
        LrclibClient lrclib = new LrclibClient(new UrlConnectionFetcher(userAgent));
        LyricsResolver resolver = new LyricsResolver(
                Arrays.asList(userStore, new EmbeddedLyricsSource(files), new SidecarLyricsSource(files)),
                lrclib,
                cache,
                System::currentTimeMillis,
                onlineAllowed);
        ThreadPoolExecutor executor = new com.mhrsdev.musicplayer.diag.SafeExecutor(1, 1, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), com.mhrsdev.musicplayer.BuildConfig.DEBUG);
        executor.allowCoreThreadTimeOut(true);
        android.content.ContentResolver cr = context.getApplicationContext().getContentResolver();
        return new LyricsRepositoryImpl(resolver, executor, lrclib, userStore, uri -> readDocument(cr, uri));
    }

    /** The user's picked lyrics document, at most 512 KB, charset sniffed like sidecar files. */
    private static String readDocument(android.content.ContentResolver cr, String uri) throws java.io.IOException {
        try (java.io.InputStream in = cr.openInputStream(android.net.Uri.parse(uri))) {
            if (in == null) throw new java.io.IOException("cannot open " + uri);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n, total = 0;
            while ((n = in.read(buf)) > 0 && total < 512 * 1024) {
                out.write(buf, 0, n);
                total += n;
            }
            return UserLyricsStore.decode(out.toByteArray());
        } catch (SecurityException e) {
            throw new java.io.IOException(e);
        }
    }
}
