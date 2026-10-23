package com.mhrsdev.musicplayer.domain.model;

/** One search hit: exactly one of track / album / artist / playlist, as given by {@link #kind}. */
public record SearchResult(Kind kind, Track track, Album album, Artist artist, Playlist playlist) {

    public enum Kind { TRACK, ALBUM, ARTIST, PLAYLIST }

    public static SearchResult of(Track track) {
        return new SearchResult(Kind.TRACK, track, null, null, null);
    }

    public static SearchResult of(Album album) {
        return new SearchResult(Kind.ALBUM, null, album, null, null);
    }

    public static SearchResult of(Artist artist) {
        return new SearchResult(Kind.ARTIST, null, null, artist, null);
    }

    public static SearchResult of(Playlist playlist) {
        return new SearchResult(Kind.PLAYLIST, null, null, null, playlist);
    }
}
