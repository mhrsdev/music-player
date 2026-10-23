package com.mhrsdev.musicplayer.domain.model;

/** One entry of a home section: exactly one of track / album / playlist, as given by {@link #kind}. */
public record HomeItem(Kind kind, Track track, Album album, Playlist playlist) {

    public enum Kind { TRACK, ALBUM, PLAYLIST }

    public static HomeItem of(Track track) {
        return new HomeItem(Kind.TRACK, track, null, null);
    }

    public static HomeItem of(Album album) {
        return new HomeItem(Kind.ALBUM, null, album, null);
    }

    public static HomeItem of(Playlist playlist) {
        return new HomeItem(Kind.PLAYLIST, null, null, playlist);
    }
}
