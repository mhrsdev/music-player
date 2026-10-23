package com.mhrsdev.musicplayer.appwidget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;
import androidx.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;

/** Small bitmap helpers: decode a cover down to widget size, and draw rounded covers / the progress bar. */
class WidgetArt {
    private WidgetArt() { }

    /** Decodes the cover at roughly {@code maxPx} (power-of-two subsampling, RGB_565); null when it cannot be read. */
    @Nullable
    static Bitmap decode(Context context, @Nullable String uri, int maxPx) {
        if (uri == null) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = context.getContentResolver().openInputStream(Uri.parse(uri))) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxPx);
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            try (InputStream in = context.getContentResolver().openInputStream(Uri.parse(uri))) {
                return BitmapFactory.decodeStream(in, null, options);
            }
        } catch (IOException | RuntimeException e) {
            return null; // missing / revoked cover: the widget shows its placeholder
        }
    }

    static int sampleSize(int width, int height, int maxPx) {
        int sample = 1;
        while (width / (sample * 2) >= maxPx && height / (sample * 2) >= maxPx) sample *= 2;
        return sample;
    }

    /** {@code src} cropped to fill {@code w x h} with rounded corners. */
    static Bitmap rounded(Bitmap src, int w, int h, float radius) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        float scale = Math.max((float) w / src.getWidth(), (float) h / src.getHeight());
        Matrix m = new Matrix();
        m.setScale(scale, scale);
        m.postTranslate((w - src.getWidth() * scale) / 2f, (h - src.getHeight() * scale) / 2f);
        BitmapShader shader = new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        shader.setLocalMatrix(m);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setShader(shader);
        new Canvas(out).drawRoundRect(new RectF(0, 0, w, h), radius, radius, paint);
        return out;
    }

    /** A rounded track with a rounded filled part; colors include their alpha. */
    static Bitmap progressBar(int w, int h, float fraction, int track, int fill) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float r = h / 2f;
        paint.setColor(track);
        canvas.drawRoundRect(new RectF(0, 0, w, h), r, r, paint);
        float filled = Math.max(fraction > 0 ? h : 0, w * fraction);
        paint.setColor(fill);
        if (filled > 0) canvas.drawRoundRect(new RectF(0, 0, filled, h), r, r, paint);
        return out;
    }
}
