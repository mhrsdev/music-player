package com.mhrsdev.musicplayer.ui.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.ui.widget.cover.GeneratedCover;

import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Scroll-path benchmark harness (SC1, D39): a library shaped like the owner's (2,000 cover-less Telegram downloads +
 * 200 covered tracks), an {@link ArtworkLoader} with a fake platform (covered albums answer a thumbnail, the rest
 * miss), and timers for one row's create / bind / measure+layout / draw and for whole scroll frames. JVM numbers
 * (Robolectric, native graphics, software canvas) are a proxy: a phone is slower per row, but the ranking holds.
 */
public class ScrollBench {
    /**
     * {@code SC1_BENCH=1} in the environment runs the full benchmark (timings, scroll frames, many iterations; ~2 min);
     * the default is the light pass the suite runs: per-bind allocation budgets only (deterministic, unlike timings on
     * a shared machine).
     */
    public static final boolean FULL = "1".equals(System.getenv("SC1_BENCH"));
    public static final int COVERLESS = 2000;
    public static final int COVERED = 200;
    private static final String ALBUM_ART = "content://media/external/audio/albumart/";
    private static final String[] NAMES = {"Mohsen Yeganeh - Behet Ghol Midam", "محسن یگانه - بهت قول میدم",
            "@MusicChannel_Shadmehr Aghili - Taraneh (320)", "Ebi - Khalij e Fars [128]", "سیروان خسروی - ساعت ۹",
            "Track_0042_final_master (Telegram)", "Homayoun Shajarian - Chera Rafti", "رضا بهرام - سهم من",
            "AUD-20240611-WA0007", "Sasy - Gentleman (Remix) @persianmusic"};
    private static final String[] ARTISTS = {Track.UNKNOWN_ARTIST, "Mohsen Yeganeh", "محسن یگانه", "Ebi",
            "Shadmehr Aghili", Track.UNKNOWN_ARTIST, "سیروان خسروی", "Reza Bahram"};

    private ScrollBench() { }

    public static Context context() {
        return new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_MusicPlayer);
    }

    /** The owner's shape: cover-less downloads in one "Telegram" folder album + a few tagged albums with covers. */
    public static List<Track> library() {
        List<Track> out = new ArrayList<>(COVERLESS + COVERED);
        for (int i = 0; i < COVERLESS + COVERED; i++) {
            boolean covered = i % 11 == 10 && out.size() - COVERLESS < COVERED;
            String albumId = covered ? String.valueOf(1 + i % 20) : "900";
            String album = covered ? "Album " + albumId : "Telegram";
            out.add(new Track(String.valueOf(i), NAMES[i % NAMES.length] + " " + i, "a" + (i % ARTISTS.length),
                    ARTISTS[i % ARTISTS.length], albumId, album, 150_000 + (i * 7919L) % 200_000,
                    ALBUM_ART + albumId, albumId.hashCode(), "content://media/external/audio/media/" + i,
                    i % 13 == 0, false, 2020, 1, 1_700_000_000_000L - i * 60_000L));
        }
        return out;
    }

    /** Installs an ArtworkLoader whose platform finds covers for albums 1..20 only (the shared instance). */
    public static ArtworkLoader installLoader(Context c) throws Exception {
        return installLoader(c, GeneratedCover.get(c));
    }

    public static ArtworkLoader installLoader(Context c, GeneratedCover covers) throws Exception {
        ArtworkLoader l = new ArtworkLoader(new ArtworkResolver(new FakePlatform()), covers, null);
        java.lang.reflect.Field f = ArtworkLoader.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, l);
        return l;
    }

    /** Counts generated-cover renders (worker and main thread). */
    public static class CountingCover extends GeneratedCover {
        public final java.util.concurrent.atomic.AtomicInteger renders = new java.util.concurrent.atomic.AtomicInteger();

        public CountingCover() {
            super(null, null);
        }

        @Override public Bitmap render(com.mhrsdev.musicplayer.ui.widget.cover.CoverSpec spec, int sizePx, Bitmap.Config config) {
            renders.incrementAndGet();
            return super.render(spec, sizePx, config);
        }

        @Override public GeneratedCover copy() {
            return this;
        }
    }

    static class FakePlatform implements ArtworkResolver.Backend {
        @Override public Bitmap thumbnail(Uri uri, int sizePx) {
            String id = uri.getLastPathSegment();
            if (uri.toString().contains("/albums/") && id != null && !id.equals("900")) {
                Bitmap b = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565);
                b.eraseColor(0xFF336699);
                return b;
            }
            return null;
        }

        @Override public InputStream open(Uri uri) { return null; }

        @Override public byte[] embeddedPicture(Uri audioUri) { return null; }

        @Override public List<ArtworkResolver.AudioRef> albumTracks(String albumId, int limit) { return List.of(); }

        @Override public ArtworkResolver.AudioRef audio(String audioId) { return null; }

        @Override public boolean isFile(File f) { return false; }
    }

    /** Bytes allocated by the calling thread so far (HotSpot). */
    public static long allocated() {
        try {
            return (Long) ALLOC.invoke(MX);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // java.lang.management is not on the Android compile classpath: reach the HotSpot bean reflectively.
    private static final Object MX;
    private static final java.lang.reflect.Method ALLOC;

    static {
        try {
            MX = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null);
            ALLOC = Class.forName("com.sun.management.ThreadMXBean").getMethod("getCurrentThreadAllocatedBytes");
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** Runs posted main-thread work (artwork deliveries) and gives the artwork workers a moment. */
    public static void settle(int rounds) {
        for (int i = 0; i < rounds; i++) {
            ShadowLooper.shadowMainLooper().idle();
            try {
                Thread.sleep(15);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        ShadowLooper.shadowMainLooper().idle();
    }

    /** A RecyclerView laid out as a phone screen (412 x 915 dp at the test density). */
    public static <R extends RecyclerView> R layOut(R rv, int heightDp) {
        float d = rv.getResources().getDisplayMetrics().density;
        int w = Math.round(412 * d), h = Math.round(heightDp * d);
        rv.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        rv.layout(0, 0, w, h);
        return rv;
    }

    /** Cost of one row: microseconds and bytes per create, bind, measure+layout, draw. */
    public record RowCost(String name, double createUs, long createBytes, double bindUs, long bindBytes,
                          double layoutUs, long layoutBytes, double drawUs, long drawBytes) {
        @Override public String toString() {
            return String.format(Locale.ROOT, "PERF row %-26s create %7.1f us %7d B | bind %6.1f us %6d B | "
                            + "measure+layout %6.1f us %6d B | draw %6.1f us %6d B", name, createUs, createBytes, bindUs,
                    bindBytes, layoutUs, layoutBytes, drawUs, drawBytes);
        }
    }

    /**
     * Times {@code iterations} binds of one holder of {@code viewType} to {@code positions} (round robin), each followed
     * by a measure+layout and a draw into a software canvas, after the same number of warm-up rounds. Artwork
     * deliveries are drained between rounds so the cache state is what a scroll sees (hits after the first pass).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static RowCost rowCost(String name, RecyclerView rv, int viewType, int[] positions, int iterations) {
        RecyclerView.Adapter a = rv.getAdapter();
        int creates = FULL ? 30 : 1;
        if (!FULL) iterations = Math.min(iterations, 40);
        for (int i = 0; i < (FULL ? 10 : 1); i++) a.createViewHolder(rv, viewType);
        long b0 = allocated(), t0 = System.nanoTime();
        RecyclerView.ViewHolder h = null;
        for (int i = 0; i < creates; i++) h = a.createViewHolder(rv, viewType);
        double createUs = (System.nanoTime() - t0) / 1e3 / creates;
        long createBytes = (allocated() - b0) / creates;
        int wSpec = widthSpec(rv, h.itemView), hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        measureLayout(h.itemView, wSpec, hSpec);
        Bitmap bmp = Bitmap.createBitmap(Math.max(1, h.itemView.getMeasuredWidth()),
                Math.max(1, h.itemView.getMeasuredHeight()), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        long[] t = new long[3], bytes = new long[3];
        for (int round = 0; round < 2; round++) { // round 0 warms up (JIT, caches); round 1 is measured
            Arrays.fill(t, 0);
            Arrays.fill(bytes, 0);
            for (int i = 0; i < iterations; i++) {
                int pos = positions[i % positions.length];
                long a0 = allocated(), s0 = System.nanoTime();
                a.bindViewHolder(h, pos);
                long s1 = System.nanoTime(), a1 = allocated();
                h.itemView.requestLayout(); // a newly bound row is always measured by the layout manager
                measureLayout(h.itemView, wSpec, hSpec);
                long s2 = System.nanoTime(), a2 = allocated();
                h.itemView.draw(canvas);
                long s3 = System.nanoTime(), a3 = allocated();
                t[0] += s1 - s0;
                t[1] += s2 - s1;
                t[2] += s3 - s2;
                bytes[0] += a1 - a0;
                bytes[1] += a2 - a1;
                bytes[2] += a3 - a2;
                if (i % 25 == 24) settle(1);
                a.onViewRecycled(h);
            }
            settle(3);
        }
        return new RowCost(name, createUs, createBytes, t[0] / 1e3 / iterations, bytes[0] / iterations,
                t[1] / 1e3 / iterations, bytes[1] / iterations, t[2] / 1e3 / iterations, bytes[2] / iterations);
    }

    private static int widthSpec(RecyclerView rv, View row) {
        ViewGroup.LayoutParams lp = row.getLayoutParams();
        int parent = rv.getWidth() > 0 ? rv.getWidth() : Math.round(412 * rv.getResources().getDisplayMetrics().density);
        if (rv.getLayoutManager() instanceof androidx.recyclerview.widget.GridLayoutManager g) parent /= g.getSpanCount();
        if (lp != null && lp.width > 0) return View.MeasureSpec.makeMeasureSpec(lp.width, View.MeasureSpec.EXACTLY);
        if (lp != null && lp.width == ViewGroup.LayoutParams.WRAP_CONTENT) {
            return View.MeasureSpec.makeMeasureSpec(parent, View.MeasureSpec.AT_MOST);
        }
        return View.MeasureSpec.makeMeasureSpec(parent, View.MeasureSpec.EXACTLY);
    }

    private static void measureLayout(View v, int wSpec, int hSpec) {
        v.measure(wSpec, hSpec);
        v.layout(0, 0, v.getMeasuredWidth(), v.getMeasuredHeight());
    }

    /** Whole scroll frames: scrollBy (binds + layout of entering rows) and a software draw of the list. */
    public record FrameCost(String name, int frames, double scrollMeanMs, double scrollP90Ms, double scrollMaxMs,
                            long bytesPerFrame, double drawMeanMs, int binds) {
        @Override public String toString() {
            return String.format(Locale.ROOT, "PERF scroll %-22s %4d frames | scroll+bind+layout mean %6.3f ms p90 %6.3f "
                            + "max %6.3f | %7d B/frame | draw mean %6.3f ms | binds %d", name, frames, scrollMeanMs,
                    scrollP90Ms, scrollMaxMs, bytesPerFrame, drawMeanMs, binds);
        }
    }

    /** Counts binds through an adapter observer-free hook: callers pass the adapter's own counter. */
    public interface BindCounter {
        int binds();
    }

    public static FrameCost scroll(String name, RecyclerView rv, int frames, int dx, int dy, BindCounter counter) {
        if (!FULL) return new FrameCost(name + " (SC1_BENCH=1)", 0, 0, 0, 0, 0, 0, 0);
        Bitmap bmp = Bitmap.createBitmap(rv.getWidth(), rv.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        // warm-up: one pass down and back (JIT, first renders), then measure the second pass
        for (int i = 0; i < frames; i++) {
            rv.scrollBy(dx, dy);
            if (i % 10 == 0) settle(1);
        }
        for (int i = 0; i < frames; i++) {
            rv.scrollBy(-dx, -dy);
            if (i % 10 == 0) settle(1);
        }
        settle(5);
        double[] scroll = new double[frames];
        double draw = 0;
        long bytes = 0;
        int binds0 = counter == null ? 0 : counter.binds();
        for (int i = 0; i < frames; i++) {
            long a0 = allocated(), s0 = System.nanoTime();
            rv.scrollBy(dx, dy);
            long s1 = System.nanoTime(), a1 = allocated();
            ShadowLooper.shadowMainLooper().idle(); // artwork deliveries land between frames, as on a phone
            long s2 = System.nanoTime();
            rv.draw(canvas);
            draw += System.nanoTime() - s2;
            scroll[i] = (s1 - s0) / 1e6;
            bytes += a1 - a0;
        }
        double[] sorted = scroll.clone();
        Arrays.sort(sorted);
        double mean = 0;
        for (double v : scroll) mean += v;
        return new FrameCost(name, frames, mean / frames, sorted[(int) (frames * 0.9)], sorted[frames - 1],
                bytes / frames, draw / 1e6 / frames, counter == null ? -1 : counter.binds() - binds0);
    }
}
