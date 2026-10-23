package com.mhrsdev.musicplayer.appwidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.mhrsdev.musicplayer.domain.repository.PlayerController;
import com.mhrsdev.musicplayer.MusicPlayerApp;

/**
 * Target of the widget buttons (not exported). Works with the app process dead: the Application builds the player
 * and restores the queue, and the controller restarts the playback service like it does for the in-app buttons.
 */
public final class WidgetActionReceiver extends BroadcastReceiver {
    static final String ACTION_PREVIOUS = "com.mhrsdev.musicplayer.widget.PREVIOUS";
    static final String ACTION_PLAY_PAUSE = "com.mhrsdev.musicplayer.widget.PLAY_PAUSE";
    static final String ACTION_NEXT = "com.mhrsdev.musicplayer.widget.NEXT";

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        PlayerController player = ((MusicPlayerApp) context.getApplicationContext()).getContainer().player;
        switch (action) {
            case ACTION_PREVIOUS -> player.previous();
            case ACTION_NEXT -> player.next();
            case ACTION_PLAY_PAUSE -> player.togglePlayPause();
            default -> { }
        }
    }
}
