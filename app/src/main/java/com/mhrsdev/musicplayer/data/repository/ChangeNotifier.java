package com.mhrsdev.musicplayer.data.repository;

import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Tiny in-process "something was written" bus. Writers call {@link #notifyChanged} with the topics they touched;
 * active {@link DbLiveData}s reload only when one of their topics changed. Per-topic counters let a LiveData that was
 * inactive at the time notice on its next activation, so nothing listens (or wakes the CPU) while the UI is hidden.
 */
public final class ChangeNotifier {
    public static final int TRACKS = 1;
    public static final int LIKES = 1 << 1;
    public static final int PLAYLISTS = 1 << 2;
    public static final int HISTORY = 1 << 3;
    public static final int ALL = TRACKS | LIKES | PLAYLISTS | HISTORY;
    private static final int TOPIC_COUNT = 4;

    interface Listener {
        void onChanged(int topics);
    }

    private final CopyOnWriteArraySet<Listener> listeners = new CopyOnWriteArraySet<>();
    private final AtomicLongArray counters = new AtomicLongArray(TOPIC_COUNT);

    void add(Listener listener) {
        listeners.add(listener);
    }

    void remove(Listener listener) {
        listeners.remove(listener);
    }

    /** Changes whenever any topic in {@code topics} changes. */
    long stamp(int topics) {
        long sum = 0;
        for (int i = 0; i < TOPIC_COUNT; i++) if ((topics & (1 << i)) != 0) sum += counters.get(i);
        return sum;
    }

    /** Call after the write is committed. Any thread. */
    public void notifyChanged(int topics) {
        for (int i = 0; i < TOPIC_COUNT; i++) if ((topics & (1 << i)) != 0) counters.incrementAndGet(i);
        for (Listener l : listeners) l.onChanged(topics);
    }
}
