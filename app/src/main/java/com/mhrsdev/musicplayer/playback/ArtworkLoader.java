package com.mhrsdev.musicplayer.playback;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.LruCache;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.util.BitmapLoader;
import androidx.media3.common.util.UnstableApi;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.ui.widget.cover.CoverSpec;
import com.mhrsdev.musicplayer.ui.widget.cover.GeneratedCover;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Notification / lock-screen artwork loader that decodes straight down to at most {@link #MAX_PX} px in RGB_565
 * (the stock loader decodes full size). Local sources only (content, file, resource); anything else fails the
 * future. A track without artwork (no URI, or one that fails to decode) gets Mowj's generated cover instead
 * ({@link GeneratedCover}, 512 px RGB_565, the same spec as in the app, so notification, lock screen, the
 * platform session's METADATA_KEY_ART and the app agree), cached in a tiny LruCache. One worker thread that exits
 * after 10 s idle.
 */
@OptIn(markerClass = UnstableApi.class)
class ArtworkLoader implements BitmapLoader {
    static final int MAX_PX = 512;
    /** Generated covers kept (512 px RGB_565 = 512 KB each): current, previous, next and one spare. */
    static final int GENERATED_CACHE = 4;

    private final Context context;
    private final ContentResolver resolver;
    private final ListeningExecutorService executor;
    private final LruCache<String, Bitmap> generated = new LruCache<>(GENERATED_CACHE);

    ArtworkLoader(Context context) {
        this.context = context.getApplicationContext();
        resolver = context.getContentResolver();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        pool.allowCoreThreadTimeOut(true);
        executor = MoreExecutors.listeningDecorator(pool);
    }

    /** Largest power-of-two sample size that keeps both sides at or above {@code maxPx} (1 for small images). */
    static int sampleSize(int width, int height, int maxPx) {
        int sample = 1;
        while (width / (sample * 2) >= maxPx && height / (sample * 2) >= maxPx) sample *= 2;
        return sample;
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
        Bitmap hit = generated.get(key);
        if (hit != null) return Futures.immediateFuture(hit);
        return executor.submit(() -> {
            Bitmap b = GeneratedCover.get(context).render(spec, MAX_PX);
            generated.put(key, b);
            return b;
        });
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

    @Override public ListenableFuture<Bitmap> loadBitmap(Uri uri) {
        return executor.submit(() -> {
            String scheme = uri.getScheme();
            if (!ContentResolver.SCHEME_CONTENT.equals(scheme) && !ContentResolver.SCHEME_FILE.equals(scheme)
                    && !ContentResolver.SCHEME_ANDROID_RESOURCE.equals(scheme)) {
                throw new IOException("Unsupported artwork scheme: " + scheme);
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = resolver.openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            Bitmap bitmap;
            try (InputStream in = resolver.openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(in, null, decodeOptions(bounds));
            }
            if (bitmap == null) throw new IOException("Undecodable artwork: " + uri);
            return bitmap;
        });
    }

    private static BitmapFactory.Options decodeOptions(BitmapFactory.Options bounds) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_PX);
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return options;
    }
}
