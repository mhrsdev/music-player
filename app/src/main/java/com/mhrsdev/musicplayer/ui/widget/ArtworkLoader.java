package com.mhrsdev.musicplayer.ui.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.data.artwork.ArtworkSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tiny artwork loader: async decode from content://, file:// or android.resource:// URIs with inSampleSize
 * downsampling to the requested size, a memory LruCache (1/16 of the heap, max 16 MB), and request
 * de-duplication by key (twelve rows of one album decode the cover once). RGB_565 is used for sizes at or under
 * 160 px (row art) to halve memory. Callbacks run on the main thread; a {@link Request} that every waiter cancelled
 * (rows scrolled away / recycled) is never decoded.
 */
public final class ArtworkLoader {
    /** Result callback; {@code bitmap} is null when the URI could not be decoded. */
    public interface Callback {
        void onLoaded(String key, @Nullable Bitmap bitmap);
    }

    /** Handle of one {@link #load}; {@link #cancel()} (main thread) stops its callback and, if alone, the decode. */
    public static final class Request {
        private final Pending pending;
        private final Callback callback;
        private boolean cancelled;

        private Request(Pending pending, Callback callback) {
            this.pending = pending;
            this.callback = callback;
        }

        @MainThread
        public void cancel() {
            if (cancelled) return;
            cancelled = true;
            pending.live.decrementAndGet();
        }
    }

    /** All waiters for one key; {@code live} is read by the decode thread. */
    private static final class Pending {
        final List<Request> waiting = new ArrayList<>();
        final AtomicInteger live = new AtomicInteger();
    }

    private static ArtworkLoader instance;

    private final ArtworkSource source;
    private final LruCache<String, Bitmap> cache;
    private final ExecutorService executor = idleExitPool(2, "artwork");
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Pending> pending = new HashMap<>(); // main thread only

    /** Daemon-like pool whose threads exit after 10 s idle (a fixed pool would keep them, and their stacks, forever). */
    static ThreadPoolExecutor idleExitPool(int threads, String name) {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(threads, threads, 10, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), r -> {
                    Thread t = new Thread(r, name);
                    t.setPriority(Thread.MIN_PRIORITY);
                    return t;
                });
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    private ArtworkLoader(Context context) {
        source = ArtworkSource.get(context);
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

    /** Loads {@code uri} scaled so its shorter side is about {@code sizePx}. A cache hit calls back synchronously. */
    @MainThread
    @Nullable
    public Request load(String uri, int sizePx, Callback callback) {
        String key = key(uri, sizePx);
        Bitmap hit = cache.get(key);
        if (hit != null) {
            callback.onLoaded(key, hit);
            return null;
        }
        Pending p = pending.get(key);
        boolean first = p == null;
        if (first) pending.put(key, p = new Pending());
        Request request = new Request(p, callback);
        p.waiting.add(request);
        p.live.incrementAndGet();
        if (first) submit(key, uri, sizePx, p);
        return request;
    }

    private void submit(String key, String uri, int sizePx, Pending p) {
        executor.execute(() -> {
            boolean wanted = p.live.get() > 0; // everybody scrolled away while queued: skip the decode
            Bitmap bmp = wanted ? decode(uri, sizePx) : null;
            main.post(() -> {
                if (!wanted && p.live.get() > 0) { // someone asked again in the meantime
                    submit(key, uri, sizePx, p);
                    return;
                }
                pending.remove(key);
                if (bmp != null) cache.put(key, bmp);
                for (Request r : p.waiting) if (!r.cancelled) r.callback.onLoaded(key, bmp);
            });
        });
    }

    /** Drops every cached bitmap; a view that still shows one keeps its own reference. */
    public static void trimAll() {
        if (instance != null) instance.trim();
    }

    /** Drop everything (call from onTrimMemory at RUNNING_LOW or worse). */
    public void trim() {
        cache.evictAll();
        source.forgetMisses(); // covers added meanwhile get another chance
    }

    @Nullable
    private Bitmap decode(String uri, int sizePx) {
        // Cover lookup chain (direct URI, thumbnail, embedded picture, folder image) lives in the data layer.
        return source.load(uri, sizePx, sizePx <= 160 ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888);
    }

    /** Largest power of two that keeps the shorter side at or above {@code target}. */
    public static int sampleSize(int w, int h, int target) {
        return ArtworkSource.sampleSize(w, h, target);
    }
}
