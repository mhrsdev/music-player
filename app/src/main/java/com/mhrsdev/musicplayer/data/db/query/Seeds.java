package com.mhrsdev.musicplayer.data.db.query;

/**
 * Stable placeholder-gradient seeds. Derived from the MediaStore ids (never stored), so a track, its album and its
 * artist always get the same colors across runs. {@code String.hashCode} is specified, hence stable.
 */
public final class Seeds {
    public static int of(String key) {
        int h = key.hashCode();
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        return h;
    }

    private Seeds() { }
}
