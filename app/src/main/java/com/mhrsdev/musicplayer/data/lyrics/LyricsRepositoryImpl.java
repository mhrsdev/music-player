package com.mhrsdev.musicplayer.data.lyrics;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import com.mhrsdev.musicplayer.domain.model.Lyrics;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.repository.LyricsRepository;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Lazy {@link LyricsRepository}: nothing runs until the returned LiveData gets an active observer, the work happens on
 * {@code executor}, and it is cancelled when the last observer goes away (the lookup restarts if observed again).
 * While a track's lookup is pending, every {@link #lyricsFor} call for it returns the same LiveData, so one track never
 * has two requests in flight. Each LiveData emits exactly one value: lyrics or {@link Lyrics#none}.
 */
public final class LyricsRepositoryImpl implements LyricsRepository {
    private final LyricsResolver resolver;
    private final Executor executor;
    private final Map<String, Lookup> pending = new ConcurrentHashMap<>();

    public LyricsRepositoryImpl(LyricsResolver resolver, Executor executor) {
        this.resolver = resolver;
        this.executor = executor;
    }

    @Override public LiveData<Lyrics> lyricsFor(Track track) {
        return pending.computeIfAbsent(track.id(), id -> new Lookup(track));
    }

    private final class Lookup extends LiveData<Lyrics> {
        private final Track track;
        @Nullable private CancelToken token; // main thread only

        Lookup(Track track) {
            this.track = track;
        }

        @Override protected void onActive() {
            if (getValue() != null || token != null) return;
            pending.putIfAbsent(track.id(), this); // re-register after an earlier cancel
            CancelToken mine = new CancelToken();
            token = mine;
            executor.execute(() -> run(mine));
        }

        @Override protected void onInactive() {
            if (token == null) return;
            token.cancel();
            token = null;
            if (getValue() == null) pending.remove(track.id(), this); // a later call starts a fresh lookup
        }

        private void run(CancelToken mine) {
            Lyrics result;
            try {
                result = resolver.resolve(track, mine);
            } catch (RuntimeException e) { // e.g. SQLite failure: show "no lyrics" rather than loading forever
                result = Lyrics.none(track.id());
            }
            if (result == null || mine.isCancelled()) return;
            pending.remove(track.id(), this);
            postValue(result);
        }
    }
}
