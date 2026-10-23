package com.mhrsdev.musicplayer.ui.widget;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.LruCache;
import android.util.Size;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Tiny artwork loader: async lookup through {@link ArtworkResolver} (thumbnail API, embedded picture, folder art;
 * robust on Android 10+), inSampleSize downsampling to the requested size, a memory LruCache (1/16 of the heap,
 * max 16 MB), a bounded negative cache (failed URIs are not retried on every bind), request de-duplication by key
 * and cancellation: when every caller of a key has cancelled (recycled row), a not-yet-started decode is dropped.
 * Two low-priority worker threads that exit after 10 s idle. Callbacks run on the main thread.
 */
public final class ArtworkLoader {
    /** Result callback; {@code bitmap} is null when no artwork was found. */
    public interface Callback {
        void onLoaded(String key, @Nullable Bitmap bitmap);
    }

    private static final int MISS_CAPACITY = 512;
    private static ArtworkLoader instance;

    private final ArtworkResolver resolver;
    private final LruCache<String, Bitmap> cache;
    /** URIs (any size) that have no artwork. Bounded; {@link #forgetMisses()} clears it. */
    private final LruCache<String, Boolean> misses = new LruCache<>(MISS_CAPACITY);
    private final Map<String, Pending> pending = new HashMap<>();
    private final ThreadPoolExecutor executor;
    private final Object[] probeLocks = new Object[8];
    private final Handler main = new Handler(Looper.getMainLooper());

    private static final class Pending {
        final List<Callback> callbacks = new ArrayList<>(2);
        @Nullable Future<?> future;
    }

    private ArtworkLoader(Context context) {
        this(new ArtworkResolver(new PlatformBackend(context.getApplicationContext())));
    }

    @VisibleForTesting
    ArtworkLoader(ArtworkResolver resolver) {
        this.resolver = resolver;
        for (int i = 0; i < probeLocks.length; i++) probeLocks[i] = new Object();
        int max = (int) Math.min(16L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 16);
        cache = new LruCache<String, Bitmap>(max) {
            @Override protected int sizeOf(String key, Bitmap value) {
                return value.getAllocationByteCount();
            }
        };
        // Decoding is CPU + I/O heavy: never more workers than the device has spare cores (1 on dual-core phones).
        int workers = Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() - 1));
        executor = new ThreadPoolExecutor(workers, workers, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
            Thread t = new Thread(r, "artwork");
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
        executor.allowCoreThreadTimeOut(true);
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

    /** True when {@code uri} is known to have no artwork (the placeholder can be drawn without a request). */
    public boolean isKnownMiss(String uri) {
        return misses.get(uri) != null;
    }

    /** Loads {@code uri} scaled so its shorter side is about {@code sizePx}. */
    @MainThread
    public void load(String uri, int sizePx, Callback callback) {
        String key = key(uri, sizePx);
        Bitmap hit = cache.get(key);
        if (hit != null || misses.get(uri) != null) {
            callback.onLoaded(key, hit);
            return;
        }
        Pending p = pending.get(key);
        if (p != null) {
            p.callbacks.add(callback);
            return;
        }
        Pending np = new Pending();
        np.callbacks.add(callback);
        pending.put(key, np);
        np.future = executor.submit(() -> {
            Bitmap bmp;
            // One probe per cover at a time: the same album asked at 3 sizes (row, card, player) must not run the
            // thumbnail / retriever / folder probes three times in parallel. The later ones find the miss or the winner.
            synchronized (probeLocks[(uri.hashCode() & 0x7fffffff) % probeLocks.length]) {
                bmp = misses.get(uri) != null ? null : resolver.resolve(uri, sizePx);
            }
            main.post(() -> deliver(uri, key, bmp));
        });
    }

    private void deliver(String uri, String key, @Nullable Bitmap bmp) {
        if (bmp != null) cache.put(key, bmp); else misses.put(uri, Boolean.TRUE);
        Pending p = pending.remove(key);
        if (p == null) return;
        for (Callback c : p.callbacks) c.onLoaded(key, bmp);
    }

    /** The caller no longer wants {@code key} (row recycled). Drops the decode if nobody else is waiting. */
    @MainThread
    public void cancel(String key, Callback callback) {
        Pending p = pending.get(key);
        if (p == null) return;
        p.callbacks.remove(callback);
        if (p.callbacks.isEmpty() && p.future != null && p.future.cancel(false)) pending.remove(key);
    }

    /** Drop all bitmaps (call from onTrimMemory at RUNNING_LOW or worse). */
    public void trim() {
        cache.evictAll();
    }

    /** UI hidden (Application.onTrimMemory): bitmaps and miss memory go; the next visit reloads what it shows. */
    public static void trimAll() {
        if (instance == null) return;
        instance.trim();
        instance.forgetMisses();
    }

    /** Forget "no artwork" results, e.g. after a library rescan. */
    public void forgetMisses() {
        misses.evictAll();
    }

    /** Largest power of two that keeps the shorter side at or above {@code target}. */
    public static int sampleSize(int w, int h, int target) {
        int s = 1;
        if (w <= 0 || h <= 0 || target <= 0) return 1;
        int shorter = Math.min(w, h);
        while (shorter / (s * 2) >= target) s *= 2;
        return s;
    }

    /** Real platform access: MediaStore thumbnails/queries, MediaMetadataRetriever, files. Worker thread only. */
    static final class PlatformBackend implements ArtworkResolver.Backend {
        private static final String AUDIO_URI = "content://media/external/audio/media/";
        private final Context context;
        private final ContentResolver cr;

        PlatformBackend(Context context) {
            this.context = context;
            this.cr = context.getContentResolver();
        }

        @Nullable @Override public Bitmap thumbnail(Uri uri, int sizePx) throws Exception {
            if (Build.VERSION.SDK_INT < 29) return null;
            return cr.loadThumbnail(uri, new Size(sizePx, sizePx), null);
        }

        @Nullable @Override public InputStream open(Uri uri) throws Exception {
            return cr.openInputStream(uri);
        }

        @Nullable @Override public byte[] embeddedPicture(Uri audioUri) throws Exception {
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            try {
                r.setDataSource(context, audioUri);
                return r.getEmbeddedPicture();
            } finally {
                r.release();
            }
        }

        @SuppressWarnings("deprecation") // DATA: the only way to find folder art next to the file
        @Override public List<ArtworkResolver.AudioRef> albumTracks(String albumId, int limit) {
            List<ArtworkResolver.AudioRef> out = new ArrayList<>();
            try (Cursor c = cr.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA},
                    MediaStore.Audio.Media.ALBUM_ID + "=?", new String[]{albumId},
                    MediaStore.Audio.Media.TRACK + " ASC")) {
                while (c != null && c.moveToNext() && out.size() < limit) {
                    out.add(new ArtworkResolver.AudioRef(Uri.parse(AUDIO_URI + c.getLong(0)), c.getString(1)));
                }
            }
            return out;
        }

        @SuppressWarnings("deprecation")
        @Nullable @Override public ArtworkResolver.AudioRef audio(String audioId) {
            Uri uri = Uri.parse(AUDIO_URI + audioId);
            try (Cursor c = cr.query(uri, new String[]{MediaStore.Audio.Media.DATA}, null, null, null)) {
                return c != null && c.moveToFirst() ? new ArtworkResolver.AudioRef(uri, c.getString(0)) : null;
            }
        }

        @Override public boolean isFile(File f) {
            return f.isFile() && f.canRead();
        }
    }
}
