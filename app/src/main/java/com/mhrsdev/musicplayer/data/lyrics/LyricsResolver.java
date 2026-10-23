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
    /** The online step of the current resolve failed (IOException: offline, HTTP error, timeout). Lyrics thread only. */
    private boolean lastError;

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
        return resolve(track, cancel, false);
    }

    /**
     * @param fresh "Search again": skip the cached online answer (also a negative one) and ask LRCLIB once more
     *              (still only when online lyrics are enabled)
     */
    @Nullable
    public Lyrics resolve(Track track, CancelToken cancel, boolean fresh) {
        lastError = false;
        Lyrics plainFallback = null;
        for (LyricsSource source : local) {
            if (cancel.isCancelled()) return null;
            Lyrics found = parse(track, tryLoad(source, track, cancel));
            if (found == null) continue;
            if (found.synced()) return found;
            if (plainFallback == null) plainFallback = found;
        }
        if (cancel.isCancelled()) return null;
        Lyrics fromRemote = viaCache(track, cancel, fresh);
        if (cancel.isCancelled()) return null;
        if (fromRemote != null && (fromRemote.synced() || plainFallback == null)) return fromRemote;
        if (plainFallback != null) return plainFallback;
        return lastError ? Lyrics.failed(track.id()) : Lyrics.none(track.id());
    }

    /**
     * Online answers are cached per CLEANED artist/title/duration ({@link #cacheKey}), so the same song in two files
     * (or with a site watermark in its tags) shares one lookup, including "not found".
     */
    @Nullable
    private Lyrics viaCache(Track track, CancelToken cancel, boolean fresh) {
        long now = clock.getAsLong();
        String key = cacheKey(track);
        if (key == null) return null; // no usable title: never ask LRCLIB
        LyricsCacheStore.Entry cached = fresh ? null : cache.get(key);
        if (cached != null && cached.expiresAtMs() > now) {
            return cached.text() == null ? null : parse(track, new LyricsSource.Found(cached.text(), cached.source()));
        }
        if (!remoteEnabled.getAsBoolean()) return null;
        try {
            LyricsSource.Found found = remote.load(track, cancel);
            if (cancel.isCancelled()) return null;
            Lyrics lyrics = parse(track, found);
            cache.put(key, lyrics == null
                    ? new LyricsCacheStore.Entry(null, null, now + NOT_FOUND_TTL_MS)
                    : new LyricsCacheStore.Entry(found.text(), found.source(), now + FOUND_TTL_MS));
            return lyrics;
        } catch (IOException e) {
            if (!cancel.isCancelled()) cache.put(key, new LyricsCacheStore.Entry(null, null, now + ERROR_TTL_MS));
            lastError = !cancel.isCancelled();
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

    /** Parses and drops junk (site watermark, advert, title-only...): junk counts as "nothing from this source". */
    @Nullable
    private static Lyrics parse(Track track, @Nullable LyricsSource.Found found) {
        if (found == null) return null;
        Lyrics l = LyricsParser.toLyrics(track.id(), found.text(), found.source());
        if (UserLyricsStore.LABEL.equals(found.source())) return l == null || l.isEmpty() ? null : l; // user's choice
        Lyrics clean = LyricsJunkFilter.clean(l, track.artistName(), track.title());
        return clean == null || clean.isEmpty() ? null : clean;
    }

    /** Cache key of the online lookup: cleaned artist + title + duration (2 s buckets); null without a title. */
    @Nullable
    static String cacheKey(Track track) {
        String title = LyricsQueryNormalizer.key(LyricsQueryNormalizer.title(track.title()));
        if (title.isEmpty()) return null;
        String artist = LyricsQueryNormalizer.key(LyricsQueryNormalizer.artist(track.artistName()));
        return "q:" + artist + "\u0001" + title + "\u0001" + Math.round(track.durationMs() / 2000.0);
    }
}
