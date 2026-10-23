package com.mhrsdev.musicplayer.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

/** Reads a LiveData's value by observing it (keeps the observer so MediatorLiveData sources stay active). */
public final class TestLive {
    private TestLive() { }

    public static <T> T value(LiveData<T> data) {
        Object[] box = new Object[1];
        Observer<T> observer = v -> box[0] = v;
        data.observeForever(observer);
        @SuppressWarnings("unchecked") T result = (T) box[0];
        return result;
    }
}
