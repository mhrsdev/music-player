package com.mhrsdev.musicplayer.data.repository;

import androidx.lifecycle.LiveData;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * A LiveData backed by a query that runs on the io executor. It only listens (and only loads) while it has active
 * observers: on activation it reloads if one of its topics changed meanwhile, and while active it reloads on every
 * matching {@link ChangeNotifier} event. Bursts of events collapse into one pending load. Results are delivered with
 * {@code postValue}, so observers see them on the main thread. The executor must run tasks in order (single thread).
 */
final class DbLiveData<T> extends LiveData<T> implements ChangeNotifier.Listener {
    private final Executor io;
    private final ChangeNotifier notifier;
    private final int topics;
    private final Supplier<T> query;
    private final AtomicBoolean loadPending = new AtomicBoolean();
    private volatile long loadedStamp = -1;

    DbLiveData(Executor io, ChangeNotifier notifier, int topics, Supplier<T> query) {
        this.io = io;
        this.notifier = notifier;
        this.topics = topics;
        this.query = query;
    }

    @Override protected void onActive() {
        notifier.add(this);
        if (loadedStamp != notifier.stamp(topics)) load();
    }

    @Override protected void onInactive() {
        notifier.remove(this);
    }

    @Override public void onChanged(int changedTopics) {
        if ((changedTopics & topics) != 0) load();
    }

    private void load() {
        if (!loadPending.compareAndSet(false, true)) return;
        io.execute(() -> {
            loadPending.set(false);
            long stamp = notifier.stamp(topics); // before the query: a write racing with it triggers another load
            postValue(query.get());
            loadedStamp = stamp;
        });
    }
}
