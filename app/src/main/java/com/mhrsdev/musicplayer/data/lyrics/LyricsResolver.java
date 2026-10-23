package com.mhrsdev.musicplayer.data.lyrics;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.Lyrics;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.io.IOException;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * The source-selection policy, blocking (background thread only):
 * <ol>
 *   <li>local sources in order (embedded tag, sidecar file) - cheap, never cached, so edits show at once;</li>
 *   <li>the lookup cache, then the remote source (LRCLIB); every remote outcome is cached, including "not found"
 *       (days) and failures (minutes) so a track never causes repeated requests.</li>
 * </ol>
 * When {@code remoteEnabled} is false (the user's "lyrics online" setting) a valid cache entry is still used but no request
 * is made and nothing is cached, so switching it back on works at once.
 *
 * <p>Synced beats plain: a local plain-text result is kept as fallback and only replaced by a synced remote result.
 * The first synced result wins immediately. Returns null when cancelled.
 */
public final class LyricsResolver {
    static final long FOUND_TTL_MS = 30L * 24 * 3600 * 1000;
    static final long NOT_FOUND_TTL_MS = 3L * 24 * 3600 * 1000;
    static final long ERROR_TTL_MS = 5L * 60 * 1000;

    private final List<LyricsSource> local;
    private final LyricsSource remote;
    private final LyricsCacheStore cache;
    private final LongSupplier clock;
    private final BooleanSupplier remoteEnabled;

    public LyricsResolver(List<LyricsSource> local, LyricsSource remote, LyricsCacheStore cache, LongSupplier clock) {
        this(local, remote, cache, clock, () -> true);
    }

    /** @param remoteEnabled read on the resolving thread for every lookup */
    public LyricsResolver(List<LyricsSource> local, LyricsSource remote, LyricsCacheStore cache, LongSupplier clock,
            BooleanSupplier remoteEnabled) {
        this.remoteEnabled = remoteEnabled;
        this.local = local;
        this.remote = remote;
        this.cache = cache;
        this.clock = clock;
    }

    /** @return the lyrics or {@link Lyrics#none}; null if {@code cancel} fired */
    @Nullable
    public Lyrics resolve(Track track, CancelToken cancel) {
        Lyrics plainFallback = null;
        for (LyricsSource source : local) {
            if (cancel.isCancelled()) return null;
            Lyrics found = parse(track, tryLoad(source, track, cancel));
            if (found == null) continue;
            if (found.synced()) return found;
            if (plainFallback == null) plainFallback = found;
        }
        if (cancel.isCancelled()) return null;
        Lyrics fromRemote = viaCache(track, cancel);
        if (cancel.isCancelled()) return null;
        if (fromRemote != null && (fromRemote.synced() || plainFallback == null)) return fromRemote;
        return plainFallback != null ? plainFallback : Lyrics.none(track.id());
    }

    @Nullable
    private Lyrics viaCache(Track track, CancelToken cancel) {
        long now = clock.getAsLong();
        LyricsCacheStore.Entry cached = cache.get(track.id());
        if (cached != null && cached.expiresAtMs() > now) {
            return cached.text() == null ? null : LrcParser.toLyrics(track.id(), cached.text(), cached.source());
        }
        if (!remoteEnabled.getAsBoolean()) return null;
        try {
            LyricsSource.Found found = remote.load(track, cancel);
            if (cancel.isCancelled()) return null;
            Lyrics lyrics = found == null ? null : LrcParser.toLyrics(track.id(), found.text(), found.source());
            cache.put(track.id(), lyrics == null
                    ? new LyricsCacheStore.Entry(null, null, now + NOT_FOUND_TTL_MS)
                    : new LyricsCacheStore.Entry(found.text(), found.source(), now + FOUND_TTL_MS));
            return lyrics;
        } catch (IOException e) {
            if (!cancel.isCancelled()) cache.put(track.id(), new LyricsCacheStore.Entry(null, null, now + ERROR_TTL_MS));
            return null;
        }
    }

    /** A broken local file must not stop the lookup: failure counts as "none here". */
    @Nullable
    private static LyricsSource.Found tryLoad(LyricsSource source, Track track, CancelToken cancel) {
        try {
            return source.load(track, cancel);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static Lyrics parse(Track track, @Nullable LyricsSource.Found found) {
        return found == null ? null : LrcParser.toLyrics(track.id(), found.text(), found.source());
    }
}
