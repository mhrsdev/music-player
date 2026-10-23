package com.mhrsdev.musicplayer.appwidget;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.RemoteViews;
import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.MainActivity;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.domain.model.NowPlaying;

/** Builds the RemoteViews of one widget instance: a pure function of its arguments (also used by the render test). */
class WidgetRenderer {
    private static final int PROGRESS_W = 480;
    private static final int PROGRESS_H = 12;
    /** RemoteViews bitmaps count against a binder-size budget: keep every cover under this edge. */
    private static final int MAX_COVER_PX = 420;
    private static final int FLAGS = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;

    private WidgetRenderer() { }

    static RemoteViews build(Context context, WidgetKind kind, @Nullable NowPlaying np, @Nullable Bitmap art,
            boolean dark, int widthDp, int heightDp) {
        WidgetPalette p = WidgetPalette.of(context, art, dark);
        RemoteViews rv = new RemoteViews(context.getPackageName(), kind.layout);
        boolean idle = np == null;

        rv.setInt(R.id.bg, "setColorFilter", p.bg());
        if (kind != WidgetKind.SQUARE) {
            rv.setTextViewText(R.id.title, idle ? context.getString(R.string.widget_empty_title) : np.title());
            rv.setTextViewText(R.id.artist, idle ? context.getString(R.string.widget_empty_subtitle) : np.artist());
            rv.setTextColor(R.id.title, p.text());
            rv.setTextColor(R.id.artist, p.subText());
            rv.setOnClickPendingIntent(R.id.text_block, open(context));
        }
        if (kind == WidgetKind.WIDE) {
            rv.setImageViewBitmap(R.id.progress, WidgetArt.progressBar(PROGRESS_W, PROGRESS_H,
                    idle ? 0f : np.progress(), WidgetPalette.withAlpha(p.text(), 0.22f), p.primary()));
        }
        if (kind == WidgetKind.WIDE) rv.setViewVisibility(R.id.progress, idle ? View.INVISIBLE : View.VISIBLE);
        cover(context, rv, kind, p, art, widthDp, heightDp);

        rv.setInt(R.id.prev, "setColorFilter", p.text());
        rv.setInt(R.id.next, "setColorFilter", p.text());
        rv.setInt(R.id.play_bg, "setColorFilter", p.primary());
        rv.setInt(R.id.play, "setColorFilter", p.onPrimary());
        if (kind == WidgetKind.SQUARE) rv.setInt(R.id.pill, "setColorFilter", p.bg());
        boolean playing = !idle && np.playing();
        rv.setImageViewResource(R.id.play, playing ? R.drawable.widget_ic_pause : R.drawable.widget_ic_play);
        rv.setContentDescription(R.id.play_frame,
                context.getString(playing ? R.string.widget_cd_pause : R.string.widget_cd_play));
        // Nothing queued: the only action is opening the app to pick a song.
        rv.setViewVisibility(R.id.prev, idle ? View.GONE : View.VISIBLE);
        rv.setViewVisibility(R.id.next, idle ? View.GONE : View.VISIBLE);
        rv.setOnClickPendingIntent(R.id.prev, action(context, WidgetActionReceiver.ACTION_PREVIOUS));
        rv.setOnClickPendingIntent(R.id.next, action(context, WidgetActionReceiver.ACTION_NEXT));
        rv.setOnClickPendingIntent(R.id.play_frame,
                idle ? open(context) : action(context, WidgetActionReceiver.ACTION_PLAY_PAUSE));
        rv.setOnClickPendingIntent(R.id.cover_frame, open(context));
        return rv;
    }

    private static void cover(Context context, RemoteViews rv, WidgetKind kind, WidgetPalette p, @Nullable Bitmap art,
            int widthDp, int heightDp) {
        rv.setInt(R.id.cover_bg, "setColorFilter", p.coverBg());
        rv.setInt(R.id.cover_note, "setColorFilter", p.note());
        rv.setViewVisibility(R.id.cover_note, art == null ? View.VISIBLE : View.GONE);
        rv.setViewVisibility(R.id.cover, art == null ? View.GONE : View.VISIBLE);
        if (art == null) return;
        float density = context.getResources().getDisplayMetrics().density;
        float w = kind.coverDp > 0 ? kind.coverDp : widthDp - 16;
        float h = kind.coverDp > 0 ? kind.coverDp : heightDp - 16;
        float radius = kind.coverDp > 0 ? context.getResources().getDimension(R.dimen.widget_cover_radius)
                : kind.fillRadiusDp * density;
        float shrink = Math.min(1f, MAX_COVER_PX / (Math.max(w, h) * density));
        rv.setImageViewBitmap(R.id.cover, WidgetArt.rounded(art, Math.round(w * density * shrink),
                Math.round(h * density * shrink), radius * shrink));
    }

    private static PendingIntent open(Context context) {
        Intent launch = new Intent(context, MainActivity.class).setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, 0, launch, FLAGS);
    }

    private static PendingIntent action(Context context, String action) {
        Intent intent = new Intent(context, WidgetActionReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(context, 0, intent, FLAGS);
    }
}
