package com.mhrsdev.musicplayer.data.lyrics;

import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.io.IOException;

/** One place lyrics can come from. Called on a background thread only. */
public interface LyricsSource {

    /** Raw lyrics text (LRC or plain) plus a display label such as "Embedded", "LRC file", "LRCLIB". */
    record Found(String text, String source) { }

    /** @return the lyrics, or null when this source has none for the track; IOException = could not find out */
    @Nullable
    Found load(Track track, CancelToken cancel) throws IOException;
}
