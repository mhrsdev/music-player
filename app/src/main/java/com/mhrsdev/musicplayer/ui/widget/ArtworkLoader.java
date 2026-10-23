package com.mhrsdev.musicplayer.ui.widget;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tiny artwork loader: async decode from content://, file:// or android.resource:// URIs with inSampleSize
 * downsampling to the requested size, a memory LruCache (1/16 of the heap, max 16 MB), and request
 * de-duplication by key. RGB_565 is used for sizes at or under 160 px (row art) to halve memory.
 * Callbacks run on the main thread; callers must check that the result is still wanted (ArtworkView does).
 */
public final class ArtworkLoader {
    /** Result callback; {@code bitmap} is null when the URI could not be decoded. */
    public interface Callback {
        void onLoaded(String key, @Nullable Bitmap bitmap);
    }

    private static ArtworkLoader instance;

    private final ContentResolver resolver;
    private final LruCache<String, Bitmap> cache;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "artwork");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private final Handler main = new Handler(Looper.getMainLooper());

    private ArtworkLoader(Context context) {
        resolver = context.getApplicationContext().getContentResolver();
        int max = (int) Math.min(16L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 16);
        cache = new LruCache<String, Bitmap>(max) {
            @Override protected int sizeOf(String key, Bitmap value) {
                return value.getAllocationByteCount();
            }
        };
    }

    @MainThread
    public static ArtworkLoader get(Context context) {
        if (instance == null) instance = new ArtworkLoader(context);
        return instance;
    }

    public static String key(String uri, int sizePx) {
        return uri + '#' + sizePx;
    }

    @Nullable
    public Bitmap cached(String uri, int sizePx) {
        return cache.get(key(uri, sizePx));
    }

    /** Loads {@code uri} scaled so its shorter side is about {@code sizePx}. */
    @MainThread
    public void load(String uri, int sizePx, Callback callback) {
        String key = key(uri, sizePx);
        Bitmap hit = cache.get(key);
        if (hit != null) {
            callback.onLoaded(key, hit);
            return;
        }
        executor.execute(() -> {
            Bitmap bmp = decode(uri, sizePx);
            main.post(() -> {
                if (bmp != null) cache.put(key, bmp);
                callback.onLoaded(key, bmp);
            });
        });
    }

    /** Drop everything (call from onTrimMemory at RUNNING_LOW or worse). */
    public void trim() {
        cache.evictAll();
    }

    @Nullable
    private Bitmap decode(String uri, int sizePx) {
        try {
            Uri u = Uri.parse(uri);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = resolver.openInputStream(u)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, o);
            }
            o.inSampleSize = sampleSize(o.outWidth, o.outHeight, sizePx);
            o.inJustDecodeBounds = false;
            o.inPreferredConfig = sizePx <= 160 ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;
            try (InputStream in = resolver.openInputStream(u)) {
                if (in == null) return null;
                return BitmapFactory.decodeStream(in, null, o);
            }
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    /** Largest power of two that keeps the shorter side at or above {@code target}. */
    public static int sampleSize(int w, int h, int target) {
        int s = 1;
        if (w <= 0 || h <= 0 || target <= 0) return 1;
        int shorter = Math.min(w, h);
        while (shorter / (s * 2) >= target) s *= 2;
        return s;
    }
}
