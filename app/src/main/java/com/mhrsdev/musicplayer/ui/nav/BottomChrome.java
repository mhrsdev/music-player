package com.mhrsdev.musicplayer.ui.nav;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.mhrsdev.musicplayer.R;

/**
 * The dock (mini-player + floating nav) floats over the screens. Scrolling screens mark their list with
 * {@code paddingBottom="@dimen/scr_bottom_chrome"}; the shell replaces that placeholder with the real dock height
 * (it changes when the mini-player appears/disappears, with insets and font scale) so the last item always clears it.
 */
public class BottomChrome {
    private BottomChrome() { }

    /** Sets the bottom padding of every marked view under {@code root} to {@code bottomPx}. */
    public static void apply(View root, int bottomPx) {
        int marker = root.getResources().getDimensionPixelSize(R.dimen.scr_bottom_chrome);
        walk(root, marker, bottomPx);
    }

    private static void walk(View v, int marker, int bottomPx) {
        boolean marked = v.getTag(R.id.mp_dock_padded) != null;
        if (!marked && v.getPaddingBottom() == marker) {
            v.setTag(R.id.mp_dock_padded, Boolean.TRUE);
            marked = true;
        }
        if (marked && v.getPaddingBottom() != bottomPx) {
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottomPx);
            if (v instanceof ViewGroup g) g.setClipToPadding(false);
        }
        if (v instanceof RecyclerView) return; // its rows are not screen containers
        if (v instanceof ViewGroup g) {
            for (int i = 0; i < g.getChildCount(); i++) walk(g.getChildAt(i), marker, bottomPx);
        }
    }

    /** Opaque background behind the dock that fades out {@code fadePx} above it, so content never shows through. */
    public static class Scrim extends Drawable {
        private final Paint paint = new Paint();
        private final int color;
        private final int fadePx;

        public Scrim(int color, int fadePx) {
            this.color = color | 0xFF000000;
            this.fadePx = fadePx;
        }

        @Override protected void onBoundsChange(@NonNull Rect b) {
            int top = b.top, solid = Math.min(b.bottom, b.top + fadePx);
            paint.setShader(new LinearGradient(0, top, 0, solid, color & 0x00FFFFFF, color, Shader.TileMode.CLAMP));
        }

        @Override public void draw(@NonNull Canvas canvas) {
            canvas.drawRect(getBounds(), paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }

        @Override public void setColorFilter(@Nullable ColorFilter cf) { paint.setColorFilter(cf); }

        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
