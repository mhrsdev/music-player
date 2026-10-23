package com.mhrsdev.musicplayer;

import android.app.Application;
import com.mhrsdev.musicplayer.di.AppContainer;

public class MusicPlayerApp extends Application {
    private AppContainer container;

    @Override public void onCreate() {
        super.onCreate();
        container = new AppContainer(this);
    }

    public AppContainer getContainer() {
        return container;
    }
}
