package com.mhrsdev.musicplayer.appwidget;

import android.content.ComponentName;
import android.content.Context;
import com.mhrsdev.musicplayer.R;

/** The three widget sizes: layout, provider and the size (dp) assumed when the launcher reports none. */
enum WidgetKind {
    COMPACT(R.layout.widget_compact, CompactWidgetProvider.class, 250, 70, 56, 0),
    SQUARE(R.layout.widget_square, SquareWidgetProvider.class, 150, 150, 0, 20),
    WIDE(R.layout.widget_wide, WideWidgetProvider.class, 250, 130, 96, 0);

    final int layout;
    final Class<? extends BaseWidgetProvider> provider;
    final int defaultWidthDp;
    final int defaultHeightDp;
    /** Fixed cover edge in dp, or 0 when the cover fills the widget. */
    final int coverDp;
    /** Corner radius of a filling cover in dp (widget radius minus its padding). */
    final int fillRadiusDp;

    WidgetKind(int layout, Class<? extends BaseWidgetProvider> provider, int w, int h, int coverDp, int fillRadiusDp) {
        this.layout = layout;
        this.provider = provider;
        this.defaultWidthDp = w;
        this.defaultHeightDp = h;
        this.coverDp = coverDp;
        this.fillRadiusDp = fillRadiusDp;
    }

    ComponentName component(Context context) {
        return new ComponentName(context, provider);
    }
}
