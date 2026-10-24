package com.mhrsdev.musicplayer.data.lyrics;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * A lyrics file next to the audio file with the same base name. Word-synced formats first ({@code .ttml}, {@code .elrc}
 * = enhanced LRC or JSON), then {@code .lrc}, a {@code .xml} that is TTML, then {@code .txt}.
 */
public class SidecarLyricsSource implements LyricsSource {
    private static final String[] EXTENSIONS = {".ttml", ".elrc", ".lrc", ".LRC", ".xml", ".txt"};
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
                String text = LyricsTextDecoder.decode(buf);
                if (ext.equals(".xml") && !TtmlLyricsParser.looksLikeTtml(text)) continue; // some other XML
                return new Found(text, ext.equals(".ttml") || ext.equals(".xml") ? "TTML file" : "LRC file");
            }
        }
        return null;
    }
}
