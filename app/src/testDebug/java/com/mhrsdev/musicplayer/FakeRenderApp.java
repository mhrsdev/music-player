package com.mhrsdev.musicplayer;

import com.mhrsdev.musicplayer.di.AppContainer;

/** Robolectric application for {@link ScreenRenderTest}: the shell runs on the in-memory fakes (sample library). */
public class FakeRenderApp extends MusicPlayerApp {
    private AppContainer fakes;

    @Override public AppContainer getContainer() {
        if (fakes == null) fakes = new AppContainer(this, true);
        return fakes;
    }
}
