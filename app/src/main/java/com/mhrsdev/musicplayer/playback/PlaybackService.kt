package com.mhrsdev.musicplayer.playback

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * STUB (T6 implements): Media3 session service hosting ExoPlayer + MediaSession.
 * Intentionally NOT registered in AndroidManifest.xml yet (needs foreground-service permissions/type).
 */
class PlaybackService : MediaSessionService() {
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = null
}
