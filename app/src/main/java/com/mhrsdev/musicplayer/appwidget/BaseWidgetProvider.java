package com.mhrsdev.musicplayer.appwidget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;

/**
 * Shared behaviour of the three widgets. There is no periodic update: the system calls {@code onUpdate} when a
 * widget is added, after a reboot or on resize, and the running app pushes changes via {@link WidgetUpdater}.
 */
public abstract class BaseWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        WidgetUpdater.refresh(context);
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int appWidgetId,
            Bundle newOptions) {
        WidgetUpdater.refresh(context);
    }
}
