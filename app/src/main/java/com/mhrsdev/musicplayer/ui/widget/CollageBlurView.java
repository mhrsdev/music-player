package com.mhrsdev.musicplayer.ui.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.LruCache;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.ui.theme.Tokens;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Card background (Library "Liked songs", DESIGN.md 03): up to three covers side by side, box-blurred once into a
 * tiny 96 x 48 bitmap on a background thread ({@link BackdropBlur#blur}), cached by the cover keys and drawn
 * stretched with bitmap filtering plus a legibility scrim. No live blur, zero per-frame work.
 * Without covers it falls back to the seed colours of {@link PlaceholderArt}.
 */
public final class CollageBlurView extends View {
    private static final int W = 96, H = 48, COVER_PX = 96;
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "collage-blur");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint scrim = new Paint();
    private final Path clip = new Path();
    private final RectF bounds = new RectF();
    private final Rect dst = new Rect();
    private final float radius;
    private String wantedKey = "";
    @Nullable private Bitmap blurred;

    public CollageBlurView(Context context) {
        this(context, null);
    }

    public CollageBlurView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        radius = 32 * getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Sets the covers (null entries use their seed's placeholder colours). Rebuilds only when the set changes. */
    public void setCovers(List<String> uris, List<Integer> seeds) {
        StringBuilder k = new StringBuilder();
        int n = Math.min(3, Math.max(uris.size(), seeds.size()));
        for (int i = 0; i < n; i++) {
            k.append(i < uris.size() ? uris.get(i) : null).append('|').append(i < seeds.size() ? seeds.get(i) : 0).append(';');
        }
        String key = k.toString();
        if (key.equals(wantedKey)) return;
        wantedKey = key;
        Bitmap hit = CACHE.get(key);
        if (hit != null) {
            blurred = hit;
            invalidate();
            return;
        }
        Bitmap[] covers = new Bitmap[n];
        int[] seedArr = new int[n];
        int[] pending = {n};
        Runnable build = () -> EXEC.execute(() -> {
            Bitmap out = compose(covers, seedArr);
            MAIN.post(() -> {
                CACHE.put(key, out);
                if (key.equals(wantedKey)) {
                    blurred = out;
                    invalidate();
                }
            });
        });
        if (n == 0) {
            build.run();
            return;
        }
        ArtworkLoader loader = ArtworkLoader.get(getContext());
        for (int i = 0; i < n; i++) {
            final int idx = i;
            seedArr[i] = i < seeds.size() ? seeds.get(i) : i;
            String u = i < uris.size() ? uris.get(i) : null;
            if (u == null) {
                if (--pending[0] == 0) build.run();
            } else {
                loader.load(u, COVER_PX, (kk, bmp) -> {
                    covers[idx] = bmp;
                    if (--pending[0] == 0) build.run();
                });
            }
        }
    }

    /** Lays the covers out left to right into W x H, blurs and saturates the pixels. */
    private static Bitmap compose(Bitmap[] covers, int[] seeds) {
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        int n = Math.max(1, covers.length);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        for (int i = 0; i < n; i++) {
            Rect r = new Rect(i * W / n, 0, (i + 1) * W / n, H);
            Bitmap b = i < covers.length ? covers[i] : null;
            if (b != null) {
                int side = Math.min(b.getWidth(), b.getHeight());
                Rect src = new Rect((b.getWidth() - side) / 2, (b.getHeight() - side) / 2,
                        (b.getWidth() + side) / 2, (b.getHeight() + side) / 2);
                c.drawBitmap(b, src, r, p);
            } else {
                int[] cols = PlaceholderArt.colors(i < seeds.length ? seeds[i] : i);
                p.setShader(new LinearGradient(r.left, 0, r.right, H, cols[0], cols[cols.length - 1], Shader.TileMode.CLAMP));
                c.drawRect(r, p);
                p.setShader(null);
            }
        }
        int[] px = new int[W * H];
        out.getPixels(px, 0, W, 0, 0, W, H);
        BackdropBlur.blur(px, W, H, 6);
        BackdropBlur.saturate(px, 1.35f);
        out.setPixels(px, 0, W, 0, 0, W, H);
        return out;
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        bounds.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        dst.set(0, 0, w, h);
        int base = Tokens.color(getContext(), R.attr.mpBackground);
        int a = (base & 0x00FFFFFF) | 0x8C000000, b = (base & 0x00FFFFFF) | 0x26000000;
        scrim.setShader(new LinearGradient(0, h, 0, 0, a, b, Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(@NonNull Canvas c) {
        c.save();
        c.clipPath(clip);
        if (blurred != null) {
            c.drawBitmap(blurred, null, dst, bmpPaint);
        } else {
            c.drawColor(Tokens.color(getContext(), R.attr.mpPrimaryContainer));
        }
        c.drawRect(bounds, scrim);
        c.restore();
    }
}
