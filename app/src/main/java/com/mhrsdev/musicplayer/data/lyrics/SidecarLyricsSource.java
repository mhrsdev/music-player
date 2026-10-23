package com.mhrsdev.musicplayer.data.lyrics;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/** A {@code .lrc} (then {@code .txt}) file next to the audio file with the same base name. */
public final class SidecarLyricsSource implements LyricsSource {
    private static final String[] EXTENSIONS = {".lrc", ".LRC", ".txt"};
    private static final int MAX_BYTES = 512 * 1024;
    private final AudioFileLocator locator;

    public SidecarLyricsSource(AudioFileLocator locator) {
        this.locator = locator;
    }

    @Nullable
    @Override public Found load(Track track, CancelToken cancel) throws IOException {
        File audio = locator.locate(track);
        if (audio == null) return null;
        String name = audio.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        for (String ext : EXTENSIONS) {
            File candidate = new File(audio.getParentFile(), base + ext);
            if (!candidate.isFile()) continue;
            try (RandomAccessFile f = new RandomAccessFile(candidate, "r")) {
                byte[] buf = new byte[(int) Math.min(f.length(), MAX_BYTES)];
                f.readFully(buf);
                return new Found(LyricsTextDecoder.decode(buf), "LRC file");
            }
        }
        return null;
    }
}
