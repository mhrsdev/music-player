package com.mhrsdev.musicplayer.ui.diagnostics;

import com.mhrsdev.musicplayer.data.settings.PerformanceProfile;
import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.diag.PerfProbe;
import com.mhrsdev.musicplayer.domain.model.PlaybackState;
import com.mhrsdev.musicplayer.ui.theme.MotionPolicy;

/** What the performance sampler asks the app (on its own thread: LiveData reads and volatile statics only). */
class PerfAppState implements PerfProbe.AppState {
    private final AppContainer container;

    PerfAppState(AppContainer container) {
        this.container = container;
    }

    @Override public boolean playing() {
        PlaybackState s = container.player.getState().getValue();
        return s != null && s.playing();
    }

    @Override public String profile() {
        PerformanceProfile user = container.settings().performance().getValue();
        PerformanceProfile now = MotionPolicy.performance();
        String chosen = user == null ? now.preset().name() : user.preset().name();
        String fps = now.maxFps() == PerformanceProfile.FPS_SCREEN ? "screen" : Integer.toString(now.maxFps());
        return MotionPolicy.thermalThrottled()
                ? chosen + " (thermal-adapted to " + now.preset().name() + ", fps cap " + fps + ")"
                : chosen + " (fps cap " + fps + ")";
    }
}
