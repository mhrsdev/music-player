package com.mhrsdev.musicplayer.ui.widget.cover;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;

/**
 * A generated cover as a {@link Drawable} (e.g. an ImageView or a RemoteViews-free surface). Renders once per bounds
 * size into a bitmap and then only blits it, so a redraw costs one drawBitmap. For covers above
 * {@link GeneratedCover#TEXT_MIN_PX} prefer the artwork loader, which renders off the main thread and caches.
 */
public class GeneratedCoverDrawable extends Drawable {
    private final GeneratedCover cover;
    private final CoverSpec spec;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    @Nullable private Bitmap bitmap;

    public GeneratedCoverDrawable(Context context, CoverSpec spec) {
        this.cover = GeneratedCover.get(context);
        this.spec = spec;
    }

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        int size = Math.min(b.width(), b.height());
        if (size <= 0) return;
        if (bitmap == null || bitmap.getWidth() != size) bitmap = cover.render(spec, size);
        canvas.drawBitmap(bitmap, b.left + (b.width() - size) / 2f, b.top + (b.height() - size) / 2f, paint);
    }

    @Override public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
