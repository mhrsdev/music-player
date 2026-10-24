package com.mhrsdev.musicplayer.ui.nav;

import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;

/**
 * Stops the shell from drawing what the opaque full player hides (v0.3.1, D29). The player is added OVER the screen
 * container, mini-player, nav and scrim; without this every player frame (backdrop drift, seek wave, lyrics) also
 * re-rendered Home's covers, the elevated mini-player and nav pills, the scrim and the root background underneath
 * (the GPU has no occlusion culling): roughly 2-4 extra full-screen layers per frame, while nothing of it is visible.
 *
 * <p>Covered = the views that were VISIBLE become INVISIBLE (they keep their size, so nothing re-lays out, and their
 * loops stop through {@code onVisibilityAggregated}) and the root background is dropped; uncovering restores exactly
 * what was hidden. The player uncovers before anything of the shell can show again (exit fade, swipe-down drag).
 */
@MainThread
public class PlayerCover {
    private final View root;
    private final View[] under;
    private final boolean[] hidden;
    @Nullable private Drawable rootBackground;
    private boolean covered;

    /** @param under the shell views below the player layer (screen container, scrim, dock) */
    public PlayerCover(View root, View... under) {
        this.root = root;
        this.under = under;
        this.hidden = new boolean[under.length];
    }

    public boolean isCovered() {
        return covered;
    }

    public void setCovered(boolean on) {
        if (on == covered) return;
        covered = on;
        if (on) {
            for (int i = 0; i < under.length; i++) {
                hidden[i] = under[i].getVisibility() == View.VISIBLE;
                if (hidden[i]) under[i].setVisibility(View.INVISIBLE);
            }
            rootBackground = root.getBackground();
            root.setBackground(null);
        } else {
            for (int i = 0; i < under.length; i++) {
                if (hidden[i] && under[i].getVisibility() == View.INVISIBLE) under[i].setVisibility(View.VISIBLE);
                hidden[i] = false;
            }
            if (root.getBackground() == null) root.setBackground(rootBackground);
            rootBackground = null;
        }
    }
}
