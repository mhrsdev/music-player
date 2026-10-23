package com.mhrsdev.musicplayer.ui.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.ui.theme.Tokens;

/**
 * Rounded (or circular) artwork with a seeded gradient placeholder plus note glyph or monogram.
 * Loads through {@link ArtworkLoader} at the view's own size; stale results from recycled rows are ignored.
 * One BitmapShader per bitmap, no allocation in onDraw. Crossfades 150 ms from placeholder to art.
 */
public class ArtworkView extends View {
    private final Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint monogramPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final Matrix shaderMatrix = new Matrix();
    private final Drawable note;

    private float cornerRadius;
    private boolean circle;
    private float zoom = 1f;
    private int seed;
    @Nullable private String uri;
    @Nullable private String wantedKey;
    @Nullable private Bitmap bitmap;
    @Nullable private String monogram;
    private float fade = 1f;
    private long fadeStart;
    private int[] placeholder = PlaceholderArt.colors(0);
    @Nullable private Listener listener;

    /** Notified when real artwork arrives (e.g. the player feeds it to ThemeController). */
    public interface Listener {
        void onArtwork(@Nullable Bitmap bitmap);
    }

    public ArtworkView(Context context) {
        this(context, null);
    }

    public ArtworkView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        cornerRadius = Tokens.dp(context, Tokens.RADIUS_ROW_ART);
        note = ContextCompat.getDrawable(context, R.drawable.ic_mp_note);
        if (note != null) note.mutate().setTint(0xE6FFFFFF);
        monogramPaint.setColor(0xF2FFFFFF);
        monogramPaint.setTextAlign(Paint.Align.CENTER);
        monogramPaint.setFakeBoldText(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setCornerRadiusDp(float dp) {
        cornerRadius = Tokens.dp(getContext(), dp);
        invalidate();
    }

    /** Circle crop (artists). */
    public void setCircle(boolean circle) {
        this.circle = circle;
        invalidate();
    }

    /** Zoom into the center (artist avatars use 1.7-2x crops of album art). */
    public void setZoom(float zoom) {
        this.zoom = Math.max(1f, zoom);
        updateShader();
        invalidate();
    }

    public void setListener(@Nullable Listener l) {
        listener = l;
    }

    public void bind(Track track) {
        setArtwork(track.artworkUri(), track.placeholderSeed());
    }

    /** Artwork URI (nullable) + placeholder seed. Monogram is cleared. */
    public void setArtwork(@Nullable String artworkUri, int placeholderSeed) {
        monogram = null;
        if (seed != placeholderSeed || placeholderPaint.getShader() == null) {
            seed = placeholderSeed;
            placeholder = PlaceholderArt.colors(placeholderSeed);
            updatePlaceholderShader();
        }
        if (artworkUri != null && artworkUri.equals(uri) && bitmap != null) return;
        uri = artworkUri;
        setBitmap(null, false);
        requestLoad();
    }

    /** Artist without art: first letter on the seeded gradient. */
    public void setMonogram(@Nullable String name, int placeholderSeed) {
        setArtwork(null, placeholderSeed);
        monogram = name == null || name.isEmpty() ? null : name.substring(0, name.offsetByCodePoints(0, 1));
        invalidate();
    }

    @Nullable
    public Bitmap getBitmap() {
        return bitmap;
    }

    private void requestLoad() {
        if (uri == null || getWidth() == 0) {
            wantedKey = null;
            return;
        }
        int size = Math.max(getWidth(), getHeight());
        if (zoom > 1f) size = (int) (size * zoom);
        String u = uri;
        wantedKey = ArtworkLoader.key(u, size);
        ArtworkLoader loader = ArtworkLoader.get(getContext());
        Bitmap hit = loader.cached(u, size);
        if (hit != null) {
            setBitmap(hit, false);
            return;
        }
        loader.load(u, size, (key, bmp) -> {
            if (key.equals(wantedKey)) setBitmap(bmp, true);
        });
    }

    private void setBitmap(@Nullable Bitmap b, boolean animate) {
        bitmap = b;
        if (b != null) {
            artPaint.setShader(new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            updateShader();
            fade = animate ? 0f : 1f;
            fadeStart = android.os.SystemClock.uptimeMillis();
        } else {
            artPaint.setShader(null);
        }
        if (listener != null && (b != null || uri == null)) listener.onArtwork(b);
        invalidate();
    }

    private void updatePlaceholderShader() {
        if (getWidth() == 0) return;
        placeholderPaint.setShader(new LinearGradient(0, 0, getWidth(), getHeight(),
                placeholder[0], placeholder[1], Shader.TileMode.CLAMP));
        invalidate();
    }

    private void updateShader() {
        Bitmap b = bitmap;
        Shader s = artPaint.getShader();
        if (b == null || s == null || getWidth() == 0) return;
        float scale = Math.max(getWidth() / (float) b.getWidth(), getHeight() / (float) b.getHeight()) * zoom;
        shaderMatrix.setScale(scale, scale);
        shaderMatrix.postTranslate((getWidth() - b.getWidth() * scale) / 2f, (getHeight() - b.getHeight() * scale) / 2f);
        s.setLocalMatrix(shaderMatrix);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        bounds.set(0, 0, w, h);
        updatePlaceholderShader();
        monogramPaint.setTextSize(Math.min(w, h) * 0.42f);
        int ns = (int) (Math.min(w, h) * 0.42f);
        if (note != null) note.setBounds((w - ns) / 2, (h - ns) / 2, (w + ns) / 2, (h + ns) / 2);
        if (bitmap == null) requestLoad(); else updateShader();
    }

    @Override protected void onDraw(Canvas canvas) {
        float r = circle ? Math.min(getWidth(), getHeight()) / 2f : cornerRadius;
        boolean showArt = bitmap != null;
        if (!showArt || fade < 1f) {
            canvas.drawRoundRect(bounds, r, r, placeholderPaint);
            if (!showArt) {
                if (monogram != null) {
                    float y = getHeight() / 2f - (monogramPaint.descent() + monogramPaint.ascent()) / 2f;
                    canvas.drawText(monogram, getWidth() / 2f, y, monogramPaint);
                } else if (note != null && uri == null) {
                    note.draw(canvas);
                }
            }
        }
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
