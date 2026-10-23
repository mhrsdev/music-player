package com.mhrsdev.musicplayer.ui.library;

import android.graphics.drawable.GradientDrawable;
import android.widget.ImageView;
import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.R;

/**
 * Temporary artwork binder until T2's {@code ArtworkView}/{@code ArtworkLoader} land: draws the design's
 * "no cover" fallback (a two-stop gradient seeded from the item, plus the note glyph). No bitmaps are decoded,
 * so it costs no RAM.
 */
public final class Art {
    private Art() { }

    private static final int[][] PALETTE = {
            {0xFF1A4B66, 0xFF0B2233}, {0xFF7B2950, 0xFF2E0C1E}, {0xFF5A2F78, 0xFF1F0D30},
            {0xFF7C1E24, 0xFF2D0A0C}, {0xFF1D4F49, 0xFF08201D}, {0xFF2E4F0A, 0xFF0F1D02},
            {0xFF6B4A12, 0xFF261904}, {0xFF2F3F7A, 0xFF0D142E}};

    public static void bind(ImageView view, @Nullable String artworkUri, int seed, float cornerDp, boolean circle) {
        int[] colors = PALETTE[Math.floorMod(seed, PALETTE.length)];
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        if (circle) {
            bg.setShape(GradientDrawable.OVAL);
        } else {
            bg.setCornerRadius(cornerDp * view.getResources().getDisplayMetrics().density);
        }
        view.setBackground(bg);
        view.setImageResource(R.drawable.nav_ic_note);
        view.setScaleType(ImageView.ScaleType.CENTER);
        view.setImageAlpha(150);
    }
}
