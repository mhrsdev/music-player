package com.mhrsdev.musicplayer.playback;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.LruCache;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.util.BitmapLoader;
import androidx.media3.common.util.UnstableApi;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.ui.widget.ArtworkResolver;
import com.mhrsdev.musicplayer.ui.widget.cover.CoverSpec;
import com.mhrsdev.musicplayer.ui.widget.cover.GeneratedCover;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Notification / lock-screen artwork loader that decodes straight down to at most {@link #MAX_PX} px in RGB_565
 * (the stock loader decodes full size). Local sources only (content, file, resource); anything else fails the
 * future. A track without artwork (no URI, or one that resolves to nothing) gets Mowj's generated cover instead
 * ({@link GeneratedCover}, 512 px RGB_565, the same spec as in the app, so notification, lock screen, the platform
 * session's METADATA_KEY_ART and the app agree), kept in the same bounded cache. One worker thread that exits after
 * 10 s idle.
 */
@OptIn(markerClass = UnstableApi.class)
class ArtworkLoader implements BitmapLoader {
    static final int MAX_PX = 512;

    /** Test seam: replaces the platform access of loaders created afterwards. */
    @VisibleForTesting static ArtworkResolver.Backend backendOverride;

    private final Context context;
    private final ArtworkResolver resolver;
    private final ListeningExecutorService executor;
    /** A few covers (<= 4 MB, RGB_565 512 px = 0.5 MB each) so a track change or re-post does not decode again. */
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) {
            return value.getAllocationByteCount();
        }
    };
    private final LruCache<String, Boolean> misses = new LruCache<>(64);

    ArtworkLoader(Context context) {
        this.context = context.getApplicationContext();
        resolver = new ArtworkResolver(backendOverride != null ? backendOverride
                : new com.mhrsdev.musicplayer.ui.widget.ArtworkLoader.PlatformBackend(context.getApplicationContext()));
        ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        pool.allowCoreThreadTimeOut(true);
        executor = MoreExecutors.listeningDecorator(pool);
    }

    /** Memory is tight / the UI is gone: forget cached covers (they are re-decoded on the next track change). */
    void trim() {
        cache.evictAll();
    }

    /** Largest power-of-two sample size that keeps both sides at or above {@code maxPx} (1 for small images). */
    static int sampleSize(int width, int height, int maxPx) {
        int sample = 1;
        while (width / (sample * 2) >= maxPx && height / (sample * 2) >= maxPx) sample *= 2;
        return sample;
    }

    @Override public boolean supportsMimeType(String mimeType) {
        return mimeType.startsWith("image/");
    }

    @Override public ListenableFuture<Bitmap> decodeBitmap(byte[] data) {
        return executor.submit(() -> {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, decodeOptions(bounds));
            if (bitmap == null) throw new IOException("Undecodable artwork");
            return bitmap;
        });
    }

    /** Real artwork when there is some, otherwise (or when it fails) the generated cover of the track. */
    @Override public ListenableFuture<Bitmap> loadBitmapFromMetadata(MediaMetadata metadata) {
        @Nullable ListenableFuture<Bitmap> real = BitmapLoader.super.loadBitmapFromMetadata(metadata);
        @Nullable CoverSpec spec = coverSpec(metadata);
        if (spec == null) return real;
        if (real == null) return generate(spec);
        return Futures.catchingAsync(real, Throwable.class, t -> generate(spec), MoreExecutors.directExecutor());
    }

    /** The app's spec for a queue item (track extras from {@link TrackMapper}), else from the plain metadata. */
    @Nullable
    static CoverSpec coverSpec(MediaMetadata m) {
        Track t = TrackMapper.fromMetadata(m);
        if (t != null) return CoverSpec.forTrack(t);
        if (m.title == null) return null;
        String artist = m.artist == null ? null : m.artist.toString();
        return m.albumTitle != null && CoverSpec.isRealAlbum(m.albumTitle.toString())
                ? CoverSpec.forAlbum(null, m.albumTitle.toString(), artist)
                : CoverSpec.single(m.title.toString(), artist);
    }

    private ListenableFuture<Bitmap> generate(CoverSpec spec) {
        String key = spec.cacheKey();
        Bitmap hit = cache.get(key);
        if (hit != null) return Futures.immediateFuture(hit);
        return executor.submit(() -> {
            Bitmap b = GeneratedCover.get(context).render(spec, MAX_PX);
            cache.put(key, b);
            return b;
        });
    }

    /** Cover for the media notification / lock screen, through the same resolver chain as the UI. */
    @Override public ListenableFuture<Bitmap> loadBitmap(Uri uri) {
        String key = uri.toString();
        Bitmap hit = cache.get(key);
        if (hit != null) return Futures.immediateFuture(hit);
        return executor.submit(() -> {
            if (misses.get(key) != null) throw new IOException("No artwork: " + uri);
            Bitmap found = resolver.resolve(key, MAX_PX);
            if (found == null) {
                misses.put(key, Boolean.TRUE);
                throw new IOException("No artwork: " + uri);
            }
            // RGB_565 and <= 512 px keep the notification well under the 1 MB binder limit.
            Bitmap small = toRgb565(found);
            cache.put(key, small);
            return small;
        });
    }

    private static Bitmap toRgb565(Bitmap b) {
        int longest = Math.max(b.getWidth(), b.getHeight());
        if (b.getConfig() == Bitmap.Config.RGB_565 && longest <= MAX_PX) return b;
        float scale = Math.min(1f, (float) MAX_PX / longest);
        Bitmap scaled = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * scale)),
                Math.max(1, Math.round(b.getHeight() * scale)), true);
        Bitmap out = scaled.copy(Bitmap.Config.RGB_565, false);
        return out == null ? scaled : out;
    }

    private static BitmapFactory.Options decodeOptions(BitmapFactory.Options bounds) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_PX);
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return options;
    }
}
