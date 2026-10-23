package com.mhrsdev.musicplayer.domain.model;

/** Outcome of one scan. {@code ok == false} means nothing was changed (e.g. permission missing). */
public record ScanResult(boolean ok, int added, int updated, int removed) {
    public static final ScanResult FAILED = new ScanResult(false, 0, 0, 0);

    public boolean changed() {
        return added + updated + removed > 0;
    }
}
