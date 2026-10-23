package com.mhrsdev.musicplayer.ui.library;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.ui.widget.ArtworkView;

/** One call to configure T2's ArtworkView (shape + source); it loads small, cached bitmaps off the main thread. */
public final class Art {
    private Art() { }

    public static void bind(ArtworkView view, @Nullable String artworkUri, int seed, float cornerDp, boolean circle) {
        view.setZoom(1f);
        view.setCircle(circle);
        if (!circle) view.setCornerRadiusDp(cornerDp);
        view.setArtwork(artworkUri, seed);
    }

    /** Artist avatar: circular crop of the artist's art, monogram fallback (DESIGN.md 04 / §12). */
    public static void artist(ArtworkView view, @Nullable String imageUri, String name, int seed) {
        view.setCircle(true);
        if (imageUri == null) {
            view.setMonogram(name, seed);
        } else {
            view.setZoom(1.8f);
            view.setArtwork(imageUri, seed);
        }
    }
}
