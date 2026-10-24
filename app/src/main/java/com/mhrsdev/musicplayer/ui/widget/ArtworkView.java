package com.mhrsdev.musicplayer.ui.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.ui.theme.Tokens;
import com.mhrsdev.musicplayer.ui.widget.cover.CoverSpec;

import java.util.Objects;

/**
 * Rounded (or circular) artwork. Loads through {@link ArtworkLoader} at the view's own size; stale results from
 * recycled rows are ignored. Three states: <b>loading</b> (a quiet surface tone, no glyph), <b>art</b>, and
 * <b>no art</b>, which shows Mowj's generated cover for the bound {@link CoverSpec} (album-level for tracks, an
 * initial for artists, a text-less scene when only the seed is known). One BitmapShader per bitmap, no allocation
 * in onDraw. Crossfades 150 ms from loading to the cover.
 */
public class ArtworkView extends View {
    private final Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final Matrix shaderMatrix = new Matrix();

    private float cornerRadius;
    private boolean circle;
    private float zoom = 1f;
    private int seed;
    @Nullable private String uri;
    @Nullable private CoverSpec spec;
    /** The bitmap shown is a generated cover (zoom does not apply; an artist avatar is already composed). */
    private boolean generated;
    @Nullable private String wantedKey;
    /** In-flight request (cancelled when the view is rebound to another cover or detached, e.g. a recycled row). */
    @Nullable private ArtworkLoader.Callback inFlight;
    @Nullable private Bitmap bitmap;
    private float fade = 1f;
    private long fadeStart;
    @Nullable private Listener listener;
    /** False after {@link #clear()}: nothing to load until the next bind (an unbound view shows the seed scene). */
    private boolean bound = true;

    /** Notified when the cover arrives, real or generated (e.g. the player feeds it to its colour extraction). */
    public interface Listener {
        void onArtwork(@Nullable Bitmap bitmap);
    }

    public ArtworkView(Context context) {
        this(context, null);
    }

    public ArtworkView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        cornerRadius = Tokens.dp(context, Tokens.RADIUS_ROW_ART);
        placeholderPaint.setColor(Tokens.color(context, R.attr.mpSurfaceContainerHigh));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setCornerRadiusDp(float dp) {
        float r = dp * getResources().getDisplayMetrics().density;
        if (r == cornerRadius) return; // set on every row bind: no redraw when nothing changed
        cornerRadius = r;
        invalidate();
    }

    /** Circle crop (artists). */
    public void setCircle(boolean circle) {
        if (this.circle == circle) return;
        this.circle = circle;
        invalidate();
    }

    /** Zoom into the center (artist avatars use 1.7-2x crops of album art). */
    public void setZoom(float zoom) {
        float z = Math.max(1f, zoom);
        if (z == this.zoom) return;
        this.zoom = z;
        updateShader();
        invalidate();
    }

    public void setListener(@Nullable Listener l) {
        listener = l;
    }

    /** A track: its art, else the generated cover of its album (or of the track itself when it has no album). */
    public void bind(Track track) {
        setArtwork(track.artworkUri(), track.placeholderSeed(), CoverSpec.forTrack(track));
    }

    /** Artwork URI (nullable) + seed; without art a text-less generated scene for the seed is shown. */
    public void setArtwork(@Nullable String artworkUri, int placeholderSeed) {
        setArtwork(artworkUri, placeholderSeed, null);
    }

    /** Artwork URI (nullable) + the generated cover to show when there is no art. */
    public void setArtwork(@Nullable String artworkUri, int placeholderSeed, @Nullable CoverSpec coverSpec) {
        boolean same = Objects.equals(artworkUri, uri) && placeholderSeed == seed && Objects.equals(coverSpec, spec);
        seed = placeholderSeed;
        spec = coverSpec;
        boolean wasBound = bound;
        bound = true;
        if (same && wasBound && bitmap != null) return;
        uri = artworkUri;
        setBitmap(null, false, false);
        requestLoad();
    }

    /**
     * A recycled row: drops the cover and any pending load without asking for anything else (setting a null artwork
     * would start the seed cover of a view nobody sees).
     */
    public void clear() {
        cancelInFlight();
        wantedKey = null;
        bound = false;
        if (bitmap != null) setBitmap(null, false, false);
    }

    /** A fling that held this view's load back ended ({@link FlingGate}). */
    void resumeLoad() {
        if (bound && isAttachedToWindow() && bitmap == null && inFlight == null) {
            requestLoad();
        }
    }

    /** Artist without art: generated avatar (the scene of the name with its initial). */
    public void setMonogram(@Nullable String name, int placeholderSeed) {
        setArtwork(null, placeholderSeed, name == null || name.isEmpty() ? null : CoverSpec.forArtist(name));
    }

    @Nullable
    public Bitmap getBitmap() {
        return bitmap;
    }

    /** True when the shown bitmap is a generated cover (no artwork was found). */
    public boolean isGenerated() {
        return generated && bitmap != null;
    }

    /** Below this width (dp) a generated cover drops its text: it would be under ~9 sp and only add noise. */
    private static final float TEXT_MIN_DP = 96f;

    private CoverSpec coverSpec() {
        CoverSpec s = spec != null ? spec : CoverSpec.fromSeed(seed);
        float dp = Math.max(getWidth(), getHeight()) / getResources().getDisplayMetrics().density;
        return dp < TEXT_MIN_DP ? s.textless() : s;
    }

    private void cancelInFlight() {
        if (inFlight != null && wantedKey != null) ArtworkLoader.get(getContext()).cancel(wantedKey, inFlight);
        inFlight = null;
    }

    @Override protected void onDetachedFromWindow() {
        cancelInFlight();
        super.onDetachedFromWindow();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (bound && bitmap == null && inFlight == null) requestLoad();
    }

    private void requestLoad() {
        cancelInFlight();
        if (!bound) return;
        if (getWidth() == 0) {
            wantedKey = null;
            return;
        }
        if (uri == null) {
            requestGenerated();
            return;
        }
        int size = Math.max(getWidth(), getHeight());
        if (zoom > 1f) size = (int) (size * zoom);
        String u = uri;
        String key = ArtworkLoader.key(u, size);
        wantedKey = key;
        ArtworkLoader loader = ArtworkLoader.get(getContext());
        Bitmap hit = loader.cachedKey(key);
        if (hit != null) {
            setBitmap(hit, false, false);
            return;
        }
        if (loader.isKnownMiss(u)) { // no art for this cover: generated cover, no decode
            requestGenerated();
            return;
        }
        if (holdWhileFlinging()) return;
        ArtworkLoader.Callback cb = new ArtworkLoader.Callback() {
            @Override public void onLoaded(String key, @Nullable Bitmap bmp) {
                if (inFlight == this) inFlight = null;
                if (!key.equals(wantedKey)) return;
                if (bmp != null) setBitmap(bmp, true, false); else requestGenerated();
            }
        };
        inFlight = cb;
        loader.load(u, size, cb);
    }

    /** No art: the generated cover from the cache, rendered now when tiny, else on the loader's worker thread. */
    private void requestGenerated() {
        cancelInFlight();
        CoverSpec s = coverSpec();
        int size = Math.max(getWidth(), getHeight());
        ArtworkLoader loader = ArtworkLoader.get(getContext());
        String key = ArtworkLoader.generatedKey(s, size);
        wantedKey = key;
        Bitmap hit = loader.generatedNow(s, size, key);
        if (hit != null) {
            setBitmap(hit, false, true);
            return;
        }
        if (holdWhileFlinging()) return;
        ArtworkLoader.Callback cb = new ArtworkLoader.Callback() {
            @Override public void onLoaded(String key, @Nullable Bitmap bmp) {
                if (inFlight == this) inFlight = null;
                if (!key.equals(wantedKey)) return;
                if (bmp != null) setBitmap(bmp, true, true);
                else if (listener != null) listener.onArtwork(null);
            }
        };
        inFlight = cb;
        loader.loadGenerated(s, size, key, cb);
    }

    /** True when a list around this view flings fast: the load waits for it to end ({@link FlingGate}). */
    private boolean holdWhileFlinging() {
        FlingGate gate = FlingGate.flinging(this);
        if (gate == null) return false;
        gate.hold(this);
        return true;
    }

    private void setBitmap(@Nullable Bitmap b, boolean animate, boolean isGenerated) {
        bitmap = b;
        generated = isGenerated;
        if (b != null) {
            artPaint.setShader(new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            updateShader();
            fade = animate ? 0f : 1f;
            fadeStart = android.os.SystemClock.uptimeMillis();
            if (listener != null) listener.onArtwork(b);
        } else {
            artPaint.setShader(null);
        }
        invalidate();
    }

    private void updateShader() {
        Bitmap b = bitmap;
        Shader s = artPaint.getShader();
        if (b == null || s == null || getWidth() == 0) return;
        float z = generated ? 1f : zoom;
        float scale = Math.max(getWidth() / (float) b.getWidth(), getHeight() / (float) b.getHeight()) * z;
        shaderMatrix.setScale(scale, scale);
        shaderMatrix.postTranslate((getWidth() - b.getWidth() * scale) / 2f, (getHeight() - b.getHeight() * scale) / 2f);
        s.setLocalMatrix(shaderMatrix);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        bounds.set(0, 0, w, h);
        boolean rebucket = generated && bitmap != null && ArtworkLoader.bucket(Math.max(w, h)) != bitmap.getWidth();
        if (bitmap == null || rebucket) requestLoad(); else updateShader();
    }

    @Override protected void onDraw(Canvas canvas) {
        float r = circle ? Math.min(getWidth(), getHeight()) / 2f : cornerRadius;
        boolean showArt = bitmap != null;
        if (!showArt || fade < 1f) canvas.drawRoundRect(bounds, r, r, placeholderPaint);
        if (showArt) {
            if (fade < 1f) {
                fade = Math.min(1f, (android.os.SystemClock.uptimeMillis() - fadeStart) / 150f);
                artPaint.setAlpha(Math.round(255 * fade));
                postInvalidateOnAnimation();
            } else {
                artPaint.setAlpha(255);
            }
            canvas.drawRoundRect(bounds, r, r, artPaint);
        }
    }
}
