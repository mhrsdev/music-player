package com.mhrsdev.musicplayer.data.lyrics;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Lyrics stored inside the audio file: ID3v2 USLT/SYLT (MP3), Vorbis comments (FLAC), {@code \u00A9lyr} (M4A/MP4).
 * Ogg Vorbis/Opus comments are not read (would need an Ogg page walker). Only the tag area is read, never the audio.
 */
public final class EmbeddedLyricsSource implements LyricsSource {
    private static final String LABEL = "Embedded";
    private final AudioFileLocator locator;

    public EmbeddedLyricsSource(AudioFileLocator locator) {
        this.locator = locator;
    }

    @Nullable
    @Override public Found load(Track track, CancelToken cancel) throws IOException {
        File file = locator.locate(track);
        if (file == null || !file.isFile()) return null;
        try (RandomAccessFile f = new RandomAccessFile(file, "r")) {
            return read(f);
        }
    }

    @Nullable
    static Found read(RandomAccessFile f) throws IOException {
        byte[] head = new byte[12];
        f.seek(0);
        int n = f.read(head);
        String text = null;
        if (n >= 4 && head[0] == 'I' && head[1] == 'D' && head[2] == '3') text = Id3LyricsReader.read(f);
        else if (n >= 4 && head[0] == 'f' && head[1] == 'L' && head[2] == 'a' && head[3] == 'C') {
            text = FlacLyricsReader.read(f);
        } else if (n >= 12 && head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p') {
            text = Mp4LyricsReader.read(f);
        }
        return text == null ? null : new Found(text, LABEL);
    }
}
