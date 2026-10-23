package com.mhrsdev.musicplayer.ui.library;

import android.content.Context;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageView;

/** ImageView that is as tall as it is wide (grid covers / artist avatars). */
public final class SquareImageView extends AppCompatImageView {
    public SquareImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, widthSpec);
    }
}
