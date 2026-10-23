package com.mhrsdev.musicplayer.ui.player.lyrics;

import android.graphics.RenderEffect;
import android.graphics.Shader;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

/**
 * Blur effects for inactive lyric lines, cached by half-pixel bucket (0..32 → 0..16 px): a line changing distance
 * only swaps a cached object on its own RenderNode, so the GPU blurs one short line, never the screen, and nothing
 * is allocated while scrolling or animating.
 */
final class LyricsBlur {
    private static final RenderEffect[] CACHE = new RenderEffect[33];

    private LyricsBlur() { }

    /** @param bucket radius × 2 in px; 0 = no effect */
    @Nullable
    @RequiresApi(31)
    static RenderEffect effect(int bucket) {
        int b = Math.max(0, Math.min(CACHE.length - 1, bucket));
        if (b == 0) return null;
        RenderEffect e = CACHE[b];
        if (e == null) {
            e = RenderEffect.createBlurEffect(b / 2f, b / 2f, Shader.TileMode.DECAL);
            CACHE[b] = e;
        }
        return e;
    }
}
