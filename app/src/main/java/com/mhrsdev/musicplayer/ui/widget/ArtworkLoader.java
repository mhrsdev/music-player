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

import com.mhrsdev.musicplayer.data.settings.DeviceClass;
import com.mhrsdev.musicplayer.diag.Breadcrumbs;
import com.mhrsdev.musicplayer.ui.widget.cover.CoverSpec;
import com.mhrsdev.musicplayer.ui.widget.cover.GeneratedCover;

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
 *
 * <p>Generated covers ({@link GeneratedCover}, for tracks without art) go through the same executor, request
 * de-duplication and memory cache, keyed by {@link CoverSpec#cacheKey()} plus a size bucket ({@link #bucket}), so
 * they obey the same RAM cap and a row recycled mid-render drops its job.
 */
public class ArtworkLoader {
    /** Result callback; {@code bitmap} is null when no artwork was found. */
    public interface Callback {
        void onLoaded(String key, @Nullable Bitmap bitmap);
    }

    private static final int MISS_CAPACITY = 512;
    private static ArtworkLoader instance;

    /** Generated-cover size buckets (px): a cover renders once per bucket, not once per view size. */
    static final int[] BUCKETS = {64, 96, 128, 192, 256, 384, 512, 768, 1024};
    /** At or below this bucket a generated cover is rendered on the calling thread (well under a millisecond). */
    public static final int SYNC_MAX_PX = 96;

    /** Largest decoded cover bucket and decode workers for this phone's class (C1); the defaults serve unit tests. */
    private static volatile int maxBucketPx = BUCKETS[BUCKETS.length - 1];
    private static volatile int workerOverride;

    private final ArtworkResolver resolver;
    @Nullable private final GeneratedCover generator;
    /** Main-thread renders (<= {@link #SYNC_MAX_PX}) use their own instance: never blocked behind a worker render. */
    @Nullable private final GeneratedCover syncGenerator;
    private final LruCache<String, Bitmap> cache;
    /**
     * URIs (any size) that have no artwork, with the time it was found out. Bounded; survives app switches (it used to
     * be wiped on every UI_HIDDEN, so each return re-probed every cover-less row: thumbnail, MediaMetadataRetriever,
     * up to 4 tracks x 12 file probes) and process restarts ({@link #missFile}, at most {@link #MISS_TTL_MS} old).
     */
    private final LruCache<String, Long> misses = new LruCache<>(MISS_CAPACITY);
    /** A remembered miss is re-probed after this long (new folder art, re-tagged files); a rescan clears all. */
    static final long MISS_TTL_MS = 3L * 24 * 60 * 60 * 1000;
    @Nullable private final File missFile;
    private final Object missFileLock = new Object();
    private volatile boolean missesLoaded;
    private final Map<String, Pending> pending = new HashMap<>();
    private final ThreadPoolExecutor executor;
    private final Object[] probeLocks = new Object[8];
    private final Handler main = new Handler(Looper.getMainLooper());

    private static class Pending {
        final List<Callback> callbacks = new ArrayList<>(2);
        @Nullable Future<?> future;
    }

    private ArtworkLoader(Context context) {
        this(new ArtworkResolver(new PlatformBackend(context.getApplicationContext())), GeneratedCover.get(context),
                new File(context.getApplicationContext().getCacheDir(), "artwork_misses.txt"));
    }

    @VisibleForTesting
    ArtworkLoader(ArtworkResolver resolver) {
        this(resolver, null, null);
    }

    @VisibleForTesting
    ArtworkLoader(ArtworkResolver resolver, @Nullable GeneratedCover generator) {
        this(resolver, generator, null);
    }

    @VisibleForTesting
    ArtworkLoader(ArtworkResolver resolver, @Nullable GeneratedCover generator, @Nullable File missFile) {
        this.resolver = resolver;
        this.missFile = missFile;
        this.missesLoaded = missFile == null;
        for (int i = 0; i < probeLocks.length; i++) probeLocks[i] = new Object();
        this.generator = generator;
        this.syncGenerator = generator == null ? null : generator.copy();
        // 1/16 of the heap, but never below two hero covers (a 128 MB heap would give 8 MB and thrash on every scroll).
        int max = cacheBytes(Runtime.getRuntime().maxMemory());
        cache = new LruCache<String, Bitmap>(max) {
            @Override protected int sizeOf(String key, Bitmap value) {
                return value.getAllocationByteCount();
            }
        };
        // Decoding is CPU + I/O heavy: never more workers than the device has spare cores (1 on dual-core phones).
        int workers = workerOverride > 0 ? workerOverride
                : Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() - 1));
        executor = new ThreadPoolExecutor(workers, workers, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
            Thread t = new Thread(r, "artwork");
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
        executor.allowCoreThreadTimeOut(true);
        if (!missesLoaded) executor.execute(this::loadMisses); // first job: off the main thread, before any probe
    }

    /** Tests: bytes the bitmap cache holds now / may hold. */
    @VisibleForTesting public int cacheUsedBytes() { return cache.size(); }

    @VisibleForTesting public int cacheMaxBytes() { return cache.maxSize(); }

    /**
     * Bitmap cache budget for a heap of {@code maxHeap} bytes: 1/16 of it, at least two hero covers (6 MB), at most
     * 16 MB whatever the phone (Android 17 kills per-app memory outliers, "MemoryLimiter:AnonSwap").
     */
    static int cacheBytes(long maxHeap) {
        return (int) Math.max(6L * 1024 * 1024, Math.min(16L * 1024 * 1024, maxHeap / 16));
    }

    @MainThread
    public static ArtworkLoader get(Context context) {
        if (instance == null) {
            DeviceClass.Signals phone = DeviceClass.detect(context);
            maxBucketPx = DeviceClass.maxCoverBucketPx(phone);
            workerOverride = DeviceClass.artworkWorkers(phone);
            instance = new ArtworkLoader(context);
        }
        return instance;
    }

    /** Sizes share size buckets: row, card and mini-player of one cover are one decode, not three. */
    public static String key(String uri, int sizePx) {
        return uri + '#' + bucket(sizePx);
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
            Bitmap found;
            // One probe per cover at a time: the same album asked at 3 sizes (row, card, player) must not run the
            // thumbnail / retriever / folder probes three times in parallel. The later ones find the miss or the winner.
            loadMisses();
            synchronized (probeLocks[(uri.hashCode() & 0x7fffffff) % probeLocks.length]) {
                try {
                    found = misses.get(uri) != null ? null : resolver.resolve(uri, bucket(sizePx));
                } catch (RuntimeException | OutOfMemoryError e) { // else the key stays "pending" forever
                    Breadcrumbs.add("artwork error " + e);
                    found = null;
                }
            }
            Bitmap bmp = fit(found, bucket(sizePx));
            main.post(() -> deliver(uri, key, bmp));
        });
    }

    /**
     * Caps a decoded cover to its bucket (worker thread). Power-of-two sampling keeps the shorter side anywhere in
     * [target, 2 x target): a 3000 px embedded picture asked for a 192 px row came back 375 px, i.e. ~4x the memory, so
     * the shared cache held a quarter of the covers it could and scrolling re-decoded (CPU, heat, GC; OOM risk with
     * several decodes in flight on a QHD+ screen). Anything over 1.25 x the bucket is scaled down to it once, here.
     */
    @Nullable static Bitmap fit(@Nullable Bitmap b, int bucketPx) {
        if (b == null) return null;
        int w = b.getWidth(), h = b.getHeight(), shorter = Math.min(w, h);
        if (shorter <= bucketPx * 5 / 4 || b.getConfig() == Bitmap.Config.HARDWARE) return b;
        float s = bucketPx / (float) shorter;
        try {
            return Bitmap.createScaledBitmap(b, Math.max(1, Math.round(w * s)), Math.max(1, Math.round(h * s)), true);
        } catch (RuntimeException | OutOfMemoryError e) {
            return b; // keep the (larger) decode rather than nothing
        }
    }

    /** Smallest bucket at or above {@code px} (capped at the largest). */
    public static int bucket(int px) {
        for (int b : BUCKETS) if (px <= b || b >= maxBucketPx) return b;
        return BUCKETS[BUCKETS.length - 1];
    }

    public static String generatedKey(CoverSpec spec, int sizePx) {
        return spec.cacheKey() + '#' + bucket(sizePx);
    }

    @Nullable
    public Bitmap cachedGenerated(CoverSpec spec, int sizePx) {
        return cache.get(generatedKey(spec, sizePx));
    }

    /** Renders a small generated cover now (only for buckets up to {@link #SYNC_MAX_PX}); cached. */
    @MainThread @Nullable
    public Bitmap generatedNow(CoverSpec spec, int sizePx) {
        String key = generatedKey(spec, sizePx);
        Bitmap hit = cache.get(key);
        if (hit != null || syncGenerator == null || bucket(sizePx) > SYNC_MAX_PX) return hit;
        try {
            Bitmap b = syncGenerator.render(spec, bucket(sizePx));
            cache.put(key, b);
            return b;
        } catch (RuntimeException | OutOfMemoryError e) { // the view keeps its quiet loading tone
            Breadcrumbs.add("generated cover error " + e);
            return null;
        }
    }

    /** The generated cover for {@code spec} at the bucket of {@code sizePx}, rendered on a worker thread. */
    @MainThread
    public void loadGenerated(CoverSpec spec, int sizePx, Callback callback) {
        String key = generatedKey(spec, sizePx);
        Bitmap hit = cache.get(key);
        if (hit != null || generator == null) {
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
        int px = bucket(sizePx);
        GeneratedCover g = generator;
        np.future = executor.submit(() -> {
            Bitmap bmp;
            try {
                bmp = g.render(spec, px);
            } catch (RuntimeException | OutOfMemoryError e) {
                Breadcrumbs.add("generated cover error " + e);
                bmp = null;
            }
            Bitmap result = bmp;
            main.post(() -> deliver(null, key, result));
        });
    }

    private void deliver(@Nullable String uri, String key, @Nullable Bitmap bmp) {
        if (bmp != null) cache.put(key, bmp); else if (uri != null) misses.put(uri, System.currentTimeMillis());
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

    /**
     * UI hidden (Application.onTrimMemory): bitmaps go, the next visit reloads what it shows. The "no artwork" memory
     * stays (tiny) and is written to disk on the worker, so a return or a restart does not re-probe every row.
     */
    public static void trimAll() {
        if (instance == null) return;
        instance.trim();
        instance.saveMisses();
    }

    /** Forget "no artwork" results, e.g. after a library rescan (memory and disk). */
    public void forgetMisses() {
        misses.evictAll();
        File f = missFile;
        if (f != null) executor.execute(() -> { synchronized (missFileLock) { f.delete(); } });
    }

    /** Snapshot on the calling (main) thread, file write on the worker. */
    @VisibleForTesting
    void saveMisses() {
        File f = missFile;
        if (f == null || !missesLoaded) return;
        Map<String, Long> snap = misses.snapshot();
        executor.execute(() -> {
            StringBuilder sb = new StringBuilder(snap.size() * 64);
            for (Map.Entry<String, Long> e : snap.entrySet()) {
                if (e.getKey().indexOf('\n') >= 0) continue;
                sb.append(e.getValue()).append('\t').append(e.getKey()).append('\n');
            }
            synchronized (missFileLock) {
                try (java.io.Writer w = new java.io.OutputStreamWriter(new java.io.FileOutputStream(f),
                        java.nio.charset.StandardCharsets.UTF_8)) {
                    w.write(sb.toString());
                } catch (java.io.IOException | RuntimeException ignored) {
                    // a cache: losing it only costs probes
                }
            }
        });
    }

    /** Worker thread: reads the remembered misses once (entries older than {@link #MISS_TTL_MS} are dropped). */
    private void loadMisses() {
        if (missesLoaded) return;
        synchronized (missFileLock) {
            if (missesLoaded) return;
            File f = missFile;
            long now = System.currentTimeMillis();
            if (f != null && f.isFile()) {
                try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(
                        new java.io.FileInputStream(f), java.nio.charset.StandardCharsets.UTF_8))) {
                    for (String line; (line = r.readLine()) != null; ) {
                        int tab = line.indexOf('\t');
                        if (tab <= 0) continue;
                        long t = Long.parseLong(line.substring(0, tab));
                        String uri = line.substring(tab + 1);
                        if (now - t < MISS_TTL_MS && t <= now && misses.get(uri) == null) misses.put(uri, t);
                    }
                } catch (java.io.IOException | RuntimeException ignored) {
                    // corrupt or unreadable: start empty
                }
            }
            missesLoaded = true;
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

    /** Real platform access: MediaStore thumbnails/queries, MediaMetadataRetriever, files. Worker thread only. */
    public static class PlatformBackend implements ArtworkResolver.Backend {
        private static final String AUDIO_URI = "content://media/external/audio/media/";
        private final Context context;
        private final ContentResolver cr;

        public PlatformBackend(Context context) {
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
