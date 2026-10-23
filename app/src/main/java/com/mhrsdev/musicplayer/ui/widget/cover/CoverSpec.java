package com.mhrsdev.musicplayer.ui.widget.cover;

import androidx.annotation.Nullable;

import com.mhrsdev.musicplayer.domain.model.Album;
import com.mhrsdev.musicplayer.domain.model.Artist;
import com.mhrsdev.musicplayer.domain.model.Track;

import java.util.Set;

/**
 * What a generated cover is made from. Every track of one album (and the album itself) maps to the same spec, so they
 * share one cover; a track without a real album (untagged download) gets its own. Pure and JVM-testable.
 *
 * @param identity the normalized identity the seed is hashed from (also the cache key prefix)
 * @param seed     64-bit seed for {@link CoverRandom}
 * @param title    big line (album or track title, or the artist's initial for {@link Kind#ARTIST}); may be empty
 * @param subtitle small line (artist), null when unknown
 */
public record CoverSpec(Kind kind, String identity, long seed, String title, @Nullable String subtitle) {

    public enum Kind { ALBUM, SINGLE, ARTIST, SEED }

    /**
     * Album titles that are really folder names (MediaStore uses the folder when a file has no album tag), or our own
     * "unknown" fallbacks: every untagged file in Download would otherwise share one cover.
     */
    private static final Set<String> NOT_AN_ALBUM = Set.of("", "unknown album", "<unknown>", "unknown", "download",
            "downloads", "music", "audio", "audios", "sounds", "media", "recordings", "voice recorder", "ringtones",
            "notifications", "alarms", "bluetooth", "telegram", "telegram audio", "whatsapp audio", "0", "emulated",
            "sdcard", "storage", "آلبوم ناشناخته", "ناشناخته");
    private static final Set<String> NOT_AN_ARTIST = Set.of("", "unknown artist", "<unknown>", "unknown",
            "various artists", "هنرمند ناشناخته", "ناشناخته");

    /** Album-level spec when the track has a real album, otherwise a single of its own. */
    public static CoverSpec forTrack(Track t) {
        return forTrack(t.albumId(), t.albumTitle(), t.title(), t.artistName());
    }

    /** {@link #forTrack(Track)} from its parts (for consumers that only carry these, e.g. the widget). */
    public static CoverSpec forTrack(@Nullable String albumId, @Nullable String albumTitle, String title,
                                     @Nullable String artist) {
        if (isRealAlbum(albumTitle)) return forAlbum(albumId, albumTitle, artist);
        return single(title, artist);
    }

    public static CoverSpec forAlbum(Album a) {
        return isRealAlbum(a.title()) ? forAlbum(a.id(), a.title(), a.artistName()) : single(a.title(), a.artistName());
    }

    /** Album identity: its id plus its normalized title (ids are per device; the title keeps unrelated ids apart). */
    public static CoverSpec forAlbum(@Nullable String albumId, String title, @Nullable String artist) {
        String id = "album:" + (albumId == null || albumId.isEmpty() ? CoverRandom.normalize(artist) : albumId)
                + '|' + CoverRandom.normalize(title);
        return new CoverSpec(Kind.ALBUM, id, CoverRandom.hash(id), title.trim(), artistLine(artist));
    }

    public static CoverSpec single(String title, @Nullable String artist) {
        String id = "single:" + CoverRandom.normalize(title) + '|' + CoverRandom.normalize(artist);
        return new CoverSpec(Kind.SINGLE, id, CoverRandom.hash(id), title == null ? "" : title.trim(), artistLine(artist));
    }

    /** Artist avatar: the scene of the artist's name with a large initial (circle-cropped by the view). */
    public static CoverSpec forArtist(String name) {
        String n = name == null ? "" : name.trim();
        String id = "artist:" + CoverRandom.normalize(n);
        String initial = n.isEmpty() ? "" : n.substring(0, n.offsetByCodePoints(0, 1)).toUpperCase(java.util.Locale.ROOT);
        return new CoverSpec(Kind.ARTIST, id, CoverRandom.hash(id), initial, null);
    }

    public static CoverSpec forArtist(Artist a) {
        return forArtist(a.name());
    }

    /** A playlist without covers: its name on a scene of its own. */
    public static CoverSpec forPlaylist(String playlistId, String name) {
        String id = "playlist:" + playlistId;
        return new CoverSpec(Kind.ALBUM, id, CoverRandom.hash(id), name == null ? "" : name.trim(), null);
    }

    /** Text-less spec from an int seed only (call sites that know nothing but {@code placeholderSeed}). */
    public static CoverSpec fromSeed(int seed) {
        String id = "seed:" + seed;
        return new CoverSpec(Kind.SEED, id, CoverRandom.mix(seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L), "", null);
    }

    public static boolean isRealAlbum(@Nullable String albumTitle) {
        return albumTitle != null && !NOT_AN_ALBUM.contains(CoverRandom.normalize(albumTitle));
    }

    @Nullable private static String artistLine(@Nullable String artist) {
        if (artist == null || NOT_AN_ARTIST.contains(CoverRandom.normalize(artist))) return null;
        return artist.trim();
    }

    /** Scene family, 0 until {@link CoverScenes#COUNT}. */
    public int scene() {
        return (int) Long.remainderUnsigned(CoverRandom.mix(seed ^ 0x5CE7E5L), CoverScenes.COUNT);
    }

    /** Palette index into {@link CoverPalettes#ALL}. */
    public int palette() {
        return (int) Long.remainderUnsigned(CoverRandom.mix(seed ^ 0xBA1E77EL), CoverPalettes.ALL.size());
    }

    /** The same cover without title/artist lines (rows, mini-player: text would be too small to read). */
    public CoverSpec textless() {
        return kind == Kind.ARTIST || title.isEmpty() && subtitle == null ? this
                : new CoverSpec(kind, identity, seed, "", null);
    }

    /** Cache key: identity plus the text actually drawn (a renamed album gets a fresh cover). */
    public String cacheKey() {
        return "gen:" + identity + '|' + title + '|' + (subtitle == null ? "" : subtitle);
    }
}
