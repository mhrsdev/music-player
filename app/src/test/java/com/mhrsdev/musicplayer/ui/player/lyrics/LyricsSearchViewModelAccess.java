package com.mhrsdev.musicplayer.ui.player.lyrics;

import com.mhrsdev.musicplayer.domain.repository.LyricsRepository;

/** Test access to the package-private constructor. */
public class LyricsSearchViewModelAccess {
    private LyricsSearchViewModelAccess() { }

    public static LyricsSearchViewModel create(LyricsRepository repo) {
        return new LyricsSearchViewModel(repo);
    }
}
