package com.mhrsdev.musicplayer.ui.library;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import androidx.annotation.NonNull;

/** Artist-without-art fallback: the first letter on the seeded circle (DESIGN.md §12). Drawn, not decoded. */
final class Monogram extends Drawable {
    private final String letter;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Monogram(String letter) {
        this.letter = letter;
        paint.setColor(0xE6FFFFFF);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.CENTER);
    }

    static void apply(ImageView view, String name) {
        String trimmed = name.trim();
        String letter = trimmed.isEmpty() ? "?" : trimmed.substring(0, trimmed.offsetByCodePoints(0, 1));
        view.setImageDrawable(new Monogram(letter));
        view.setImageAlpha(255);
        view.setScaleType(ImageView.ScaleType.FIT_XY);
    }

    @Override public void draw(@NonNull Canvas canvas) {
        Rect r = getBounds();
        paint.setTextSize(r.height() * 0.4f);
        float y = r.exactCenterY() - (paint.descent() + paint.ascent()) / 2f;
        canvas.drawText(letter, r.exactCenterX(), y, paint);
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }

    @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
