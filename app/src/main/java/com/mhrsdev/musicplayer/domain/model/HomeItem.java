package com.mhrsdev.musicplayer.domain.model;

/** One entry of a home section: exactly one of track / album / playlist / artist / mix, as given by {@link #kind}. */
public record HomeItem(Kind kind, Track track, Album album, Playlist playlist, Artist artist, HomeMix mix) {

    public enum Kind { TRACK, ALBUM, PLAYLIST, ARTIST, MIX }

    public static HomeItem of(Track track) {
        return new HomeItem(Kind.TRACK, track, null, null, null, null);
    }

    public static HomeItem of(Album album) {
        return new HomeItem(Kind.ALBUM, null, album, null, null, null);
    }

    public static HomeItem of(Playlist playlist) {
        return new HomeItem(Kind.PLAYLIST, null, null, playlist, null, null);
    }

    public static HomeItem of(Artist artist) {
        return new HomeItem(Kind.ARTIST, null, null, null, artist, null);
    }

    public static HomeItem of(HomeMix mix) {
        return new HomeItem(Kind.MIX, null, null, null, null, mix);
    }
}
