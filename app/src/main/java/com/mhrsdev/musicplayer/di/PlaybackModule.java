package com.mhrsdev.musicplayer.di;

import android.content.Context;
import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.repository.AudioEffectsController;
import com.mhrsdev.musicplayer.domain.repository.PlayerController;
import com.mhrsdev.musicplayer.domain.repository.LibraryReader;
import com.mhrsdev.musicplayer.playback.AudioEffectsEngine;
import com.mhrsdev.musicplayer.playback.BrowseHolder;
import com.mhrsdev.musicplayer.domain.repository.OutputDevices;
import com.mhrsdev.musicplayer.playback.ExoPlayerController;
import com.mhrsdev.musicplayer.playback.HeadsetControls;
import com.mhrsdev.musicplayer.playback.OutputDevicesImpl;
import com.mhrsdev.musicplayer.playback.PlayCountListener;
import java.util.function.Consumer;

/** Factory for the real playback stack; {@code AppContainer} calls it instead of constructing the fake. */
public final class PlaybackModule {
    private PlaybackModule() {}

    /**
     * @param playCountListener called once per real listen of a track, e.g. {@code library::recordPlay}
     * @param nextTrackListener called while playing whenever the next queued track changes, e.g. waveform prefetch
     */
    public static PlayerController createController(Context context, @Nullable PlayCountListener playCountListener,
            @Nullable Consumer<Track> nextTrackListener) {
        return new ExoPlayerController(context, playCountListener, nextTrackListener);
    }

    /** Makes the library browsable from outside the app (Android Auto, Assistant) through the playback service. */
    public static void setLibraryReader(LibraryReader reader) {
        BrowseHolder.set(reader);
    }

    /** Equalizer / bass / virtualizer / loudness on the shared player's audio session (effects attach lazily). */
    public static AudioEffectsController createAudioEffects(Context context) {
        return new AudioEffectsEngine(context);
    }

    /** Connected outputs from AudioManager; the device callback is registered only while the list is observed. */
    public static OutputDevices createOutputDevices(Context context) {
        return new OutputDevicesImpl(context);
    }

    /** What the headset "like" gesture does with the current track id, e.g. {@code library::toggleLike}. */
    public static void setLikeHandler(Consumer<String> handler) {
        HeadsetControls.setLikeHandler(handler);
    }
}
