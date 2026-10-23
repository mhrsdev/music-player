package com.mhrsdev.musicplayer.appwidget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.MusicPlayerApp;
import com.mhrsdev.musicplayer.domain.model.NowPlaying;
import com.mhrsdev.musicplayer.playback.NowPlayingBridge;
import com.mhrsdev.musicplayer.ui.widget.cover.CoverSpec;
import com.mhrsdev.musicplayer.ui.widget.cover.GeneratedCover;
import java.util.concurrent.Executor;

/**
 * Pushes the current {@link NowPlaying} to every placed widget. Called by the playback bridge on changes and by the
 * providers when the system asks; with no widget on the home screen it costs one cheap id lookup per kind.
 */
public class WidgetUpdater {
    private static final int ART_PX = 420;

    private WidgetUpdater() { }

    /** Redraws from the live player (a restored, paused queue after a restart). Main thread. */
    static void refresh(Context context) {
        update(context, NowPlayingBridge.snapshot(context),
                ((MusicPlayerApp) context.getApplicationContext()).getContainer().ioExecutor);
    }

    /** Any thread. Decodes the cover and renders on {@code io}. */
    public static void update(Context context, @Nullable NowPlaying np, Executor io) {
        Context app = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        boolean any = false;
        for (WidgetKind kind : WidgetKind.values()) any |= manager.getAppWidgetIds(kind.component(app)).length > 0;
        if (any) io.execute(() -> render(app, manager, np));
    }

    /** The generated cover the app shows for this track (io thread; one 420 px RGB_565 render per update). */
    static Bitmap generated(Context app, NowPlaying np) {
        CoverSpec spec = CoverSpec.forTrack(np.albumId(), np.albumTitle(), np.title(), np.artist());
        return GeneratedCover.get(app).render(spec, ART_PX);
    }

    private static void render(Context app, AppWidgetManager manager, @Nullable NowPlaying np) {
        Bitmap art = np == null ? null : WidgetArt.decode(app, np.artworkUri(), ART_PX);
        if (art == null && np != null && np.artworkUri() != null) { // Android 10+: albumart URIs often do not open
            art = new com.mhrsdev.musicplayer.ui.widget.ArtworkResolver(
                    new com.mhrsdev.musicplayer.ui.widget.ArtworkLoader.PlatformBackend(app)).resolve(np.artworkUri(), ART_PX);
        }
        if (art == null && np != null) art = generated(app, np); // no cover: the app's generated one
        boolean dark = (app.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        for (WidgetKind kind : WidgetKind.values()) {
            for (int id : manager.getAppWidgetIds(kind.component(app))) {
                Bundle options = manager.getAppWidgetOptions(id);
                int w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
                int h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
                manager.updateAppWidget(id, WidgetRenderer.build(app, kind, np, art, dark,
                        w > 0 ? w : kind.defaultWidthDp, h > 0 ? h : kind.defaultHeightDp));
            }
        }
    }
}
