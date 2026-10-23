package com.mhrsdev.musicplayer.playback;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import androidx.annotation.OptIn;
import androidx.media3.common.util.BitmapLoader;
import androidx.media3.common.util.UnstableApi;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Notification / lock-screen artwork loader that decodes straight down to at most {@link #MAX_PX} px in RGB_565
 * (the stock loader decodes full size). Local sources only (content, file, resource); anything else fails the
 * future, which Media3 treats as "no artwork". One worker thread that exits after 10 s idle.
 */
@OptIn(markerClass = UnstableApi.class)
final class ArtworkLoader implements BitmapLoader {
    static final int MAX_PX = 512;

    private final ContentResolver resolver;
    private final ListeningExecutorService executor;

    ArtworkLoader(Context context) {
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
