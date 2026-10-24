package com.mhrsdev.musicplayer.data.lyrics;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import com.mhrsdev.musicplayer.domain.model.Lyrics;
import com.mhrsdev.musicplayer.domain.model.LyricsCandidate;
import com.mhrsdev.musicplayer.domain.model.LyricsSearchResult;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.repository.LyricsRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Lazy {@link LyricsRepository}: nothing runs until the returned LiveData gets an active observer, the work happens on
 * {@code executor}, and it is cancelled when the last observer goes away (the lookup restarts if observed again).
 * While a track's lookup is pending, every {@link #lyricsFor} call for it returns the same LiveData, so one track never
 * has two requests in flight. Each LiveData emits exactly one value: lyrics or {@link Lyrics#none}.
 */
public class LyricsRepositoryImpl implements LyricsRepository {
    private final LyricsResolver resolver;
    private final Executor executor;
    private final Map<String, Lookup> pending = new ConcurrentHashMap<>();
    @Nullable private final LrclibClient online;
    @Nullable private final UserLyricsStore userStore;
    @Nullable private final DocumentReader documents;

    /** Reads a user-picked document (SAF uri) as text; at most {@link UserLyricsStore#MAX_BYTES}. */
    public interface DocumentReader {
        String read(String uri) throws IOException;
    }

    public LyricsRepositoryImpl(LyricsResolver resolver, Executor executor) {
        this(resolver, executor, null, null, null);
    }

    public LyricsRepositoryImpl(LyricsResolver resolver, Executor executor, @Nullable LrclibClient online,
            @Nullable UserLyricsStore userStore, @Nullable DocumentReader documents) {
        this.resolver = resolver;
        this.executor = executor;
        this.online = online;
        this.userStore = userStore;
        this.documents = documents;
    }

    @Override public LiveData<Lyrics> searchAgain(Track track) {
        return new Task<>(cancel -> resolver.resolve(track, cancel, true), () -> Lyrics.failed(track.id()));
    }

    @Override public LiveData<LyricsSearchResult> search(String artist, String title) {
        return new Task<>(cancel -> {
            if (online == null) return LyricsSearchResult.failure();
            List<LyricsCandidate> out = new ArrayList<>();
            for (LrclibJson.Candidate c : online.search(artist, title, cancel)) {
                String text = c.synced() != null ? c.synced() : c.plain();
                out.add(new LyricsCandidate(nz(c.trackName()), nz(c.artistName()), nz(c.albumName()),
                        Math.round(c.durationSec() * 1000), c.synced() != null, text));
            }
            return new LyricsSearchResult(out, false);
        }, LyricsSearchResult::failure);
    }

    @Override public LiveData<Lyrics> useCandidate(Track track, LyricsCandidate candidate) {
        return saveThenResolve(track, cancel -> candidate.text());
    }

    @Override public LiveData<Lyrics> importFile(Track track, String uri) {
        return saveThenResolve(track, cancel -> {
            if (documents == null) throw new IOException("no document reader");
            return documents.read(uri);
        });
    }

    private LiveData<Lyrics> saveThenResolve(Track track, Work<String> text) {
        return new Task<>(cancel -> {
            String t = text.run(cancel);
            if (userStore == null || t == null || LyricsParser.toLyrics(track.id(), t, UserLyricsStore.LABEL) == null) {
                return resolver.resolve(track, cancel); // nothing usable: keep what the track had
            }
            userStore.save(track.id(), t);
            pending.remove(track.id());
            return resolver.resolve(track, cancel);
        }, () -> Lyrics.failed(track.id()));
    }

    private static String nz(@Nullable String s) {
        return s == null ? "" : s;
    }

    /** A blocking step on the lyrics thread; IOException = failed. */
    interface Work<T> {
        @Nullable T run(CancelToken cancel) throws IOException;
    }

    /**
     * One-shot background work bound to observation like {@link Lookup}: starts when observed, cancelled when the
     * last observer leaves, emits one value ({@code onError} on IOException/RuntimeException).
     */
    private class Task<T> extends LiveData<T> {
        private final Work<T> work;
        private final java.util.function.Supplier<T> onError;
        @Nullable private CancelToken token;

        Task(Work<T> work, java.util.function.Supplier<T> onError) {
            this.work = work;
            this.onError = onError;
        }

        @Override protected void onActive() {
            if (getValue() != null || token != null) return;
            CancelToken mine = new CancelToken();
            token = mine;
            executor.execute(() -> {
                T result;
                try {
                    result = work.run(mine);
                } catch (IOException | RuntimeException e) {
                    result = onError.get();
                }
                if (result != null && !mine.isCancelled()) postValue(result);
            });
        }

        @Override protected void onInactive() {
            if (token == null || getValue() != null) return;
            token.cancel();
            token = null;
        }
    }

    @Override public LiveData<Lyrics> lyricsFor(Track track) {
        return pending.computeIfAbsent(track.id(), id -> new Lookup(track));
    }

    private class Lookup extends LiveData<Lyrics> {
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
