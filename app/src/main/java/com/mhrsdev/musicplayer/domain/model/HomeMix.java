package com.mhrsdev.musicplayer.domain.model;

import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A playable collection the Home feed builds locally (a "Daily mix", a decade, a genre, a folder ...). The feed never
 * carries display text: the UI turns {@link #kind} + {@link #arg} into a localized title.
 *
 * @param id stable id, unique within the feed (e.g. {@code daily-1}, {@code decade-1990}, {@code genre-rock})
 * @param arg kind-specific parameter: mix number, decade start year, genre name, folder name; may be empty
 * @param artistNames up to three leading artists, for a "with X, Y" subtitle
 */
public record HomeMix(
        String id,
        Kind kind,
        String arg,
        List<Track> tracks,
        List<String> artistNames,
        @Nullable String artworkUri,
        int placeholderSeed) {

    public enum Kind { DAILY, LATE_NIGHT, MORNING, UPBEAT, DECADE, GENRE, FOLDER }

    public HomeMix {
        tracks = Collections.unmodifiableList(new ArrayList<>(tracks));
        artistNames = Collections.unmodifiableList(new ArrayList<>(artistNames));
    }
}
