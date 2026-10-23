package com.mhrsdev.musicplayer.playback;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaButtonReceiver;

/**
 * Where the system sends a headset / Bluetooth button when {@link PlaybackService} is not running (it stops itself
 * a minute after pause, or the process was killed). Media3 then starts the service as a foreground service, and a
 * foreground service that does not reach {@code startForeground} in time crashes the app. So the service is only
 * started when there is a queue to resume; with nothing saved the press is a no-op instead of a crash.
 */
@OptIn(markerClass = UnstableApi.class)
public class MediaButtonRestartReceiver extends MediaButtonReceiver {
    @Override protected boolean shouldStartForegroundService(Context context, Intent intent) {
        return PlayerHolder.hasQueue(context);
    }
}
