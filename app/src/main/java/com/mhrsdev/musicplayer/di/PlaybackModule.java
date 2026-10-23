package com.mhrsdev.musicplayer.di;

import android.content.Context;
import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.repository.PlayerController;
import com.mhrsdev.musicplayer.playback.ExoPlayerController;
import com.mhrsdev.musicplayer.playback.PlayCountListener;

/** Factory for the real playback stack; {@code AppContainer} calls it instead of constructing the fake. */
public final class PlaybackModule {
    private PlaybackModule() {}

    /**
     * @param playCountListener called once per real listen of a track, e.g. {@code library::recordPlay}
     */
    public static PlayerController createController(Context context, @Nullable PlayCountListener playCountListener) {
        return new ExoPlayerController(context, playCountListener);
    }
}
