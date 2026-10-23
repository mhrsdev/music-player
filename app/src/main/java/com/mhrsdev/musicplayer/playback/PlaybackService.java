package com.mhrsdev.musicplayer.playback;

import androidx.annotation.Nullable;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

/**
 * STUB (playback task implements): Media3 session service hosting ExoPlayer + MediaSession.
 * Intentionally NOT registered in AndroidManifest.xml yet (needs foreground-service permissions/type).
 */
public class PlaybackService extends MediaSessionService {
    @Nullable @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return null;
    }
}
