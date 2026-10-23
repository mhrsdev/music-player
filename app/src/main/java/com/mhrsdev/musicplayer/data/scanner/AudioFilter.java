package com.mhrsdev.musicplayer.data.scanner;

import java.util.Locale;
import java.util.Set;

/**
 * Which MediaStore audio rows are library music. Downloads often have IS_MUSIC=0 (no tags yet), so a normal audio file
 * extension counts as music too; ringtones, notifications, alarms and podcasts only with "include non-music audio".
 */
public final class AudioFilter {
    private static final Set<String> MUSIC_EXTENSIONS = Set.of("mp3", "m4a", "flac", "ogg", "opus", "wav", "aac");

    private final boolean includeNonMusic;
    private final long minDurationMs;

    public AudioFilter(boolean includeNonMusic, long minDurationMs) {
        this.includeNonMusic = includeNonMusic;
        this.minDurationMs = minDurationMs;
    }

    public boolean accepts(ScannedAudio a) {
        if (a.pending()) return false; // finished later: the generation bump rescans
        if (a.durationMs() <= 0 || a.durationMs() < minDurationMs) return false; // 0 = not indexed yet
        if (includeNonMusic) return true;
        if (a.special()) return false;
        return a.music() || hasMusicExtension(a.displayName(), a.path());
    }

    private static boolean hasMusicExtension(String displayName, String path) {
        String name = displayName != null ? displayName : path;
        if (name == null) return false;
        int dot = name.lastIndexOf('.');
        return dot >= 0 && MUSIC_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
