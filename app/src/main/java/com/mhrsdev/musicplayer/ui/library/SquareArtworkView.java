package com.mhrsdev.musicplayer.ui.library;

import android.content.Context;
import android.util.AttributeSet;
import com.mhrsdev.musicplayer.ui.widget.ArtworkView;

/** ArtworkView that is as tall as it is wide (grid covers / artist avatars). */
public final class SquareArtworkView extends ArtworkView {
    public SquareArtworkView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, widthSpec);
    }
}
