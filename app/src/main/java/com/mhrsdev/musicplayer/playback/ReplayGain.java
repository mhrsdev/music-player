package com.mhrsdev.musicplayer.playback;

import androidx.annotation.OptIn;
import androidx.media3.common.Metadata;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.metadata.id3.InternalFrame;
import androidx.media3.extractor.metadata.id3.TextInformationFrame;
import androidx.media3.extractor.metadata.vorbis.VorbisComment;
import com.mhrsdev.musicplayer.domain.model.ReplayGainMode;

/**
 * ReplayGain from the tags Media3 already parsed while loading the file (ID3 TXXX, Vorbis/FLAC comments, MP4
 * freeform atoms), so no second pass over the file. Gains are in dB, {@link Double#NaN} = tag missing.
 *
 * <p>shortcut: only attenuates (never above 0 dB), which cannot clip and needs no peak tags; quiet tracks are not
 * boosted. Upgrade: add the positive part to a LoudnessEnhancer limited by REPLAYGAIN_*_PEAK headroom.
 */
@OptIn(markerClass = UnstableApi.class)
final class ReplayGain {
    static final String TRACK_KEY = "REPLAYGAIN_TRACK_GAIN";
    static final String ALBUM_KEY = "REPLAYGAIN_ALBUM_GAIN";
    static final ReplayGain NONE = new ReplayGain(Double.NaN, Double.NaN);
    private static final double MIN_DB = -30.0;

    final double trackDb;
    final double albumDb;

    ReplayGain(double trackDb, double albumDb) {
        this.trackDb = trackDb;
        this.albumDb = albumDb;
    }

    /** "-6.48 dB", "+3.2", "-6,48 DB" -> dB; NaN when it is not a finite number. */
    static double parseDb(String text) {
        if (text == null) return Double.NaN;
        String t = text.trim().replace(',', '.');
        if (t.regionMatches(true, t.length() - 2, "dB", 0, 2)) t = t.substring(0, t.length() - 2).trim();
        try {
            double v = Double.parseDouble(t);
            return Double.isFinite(v) ? v : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** dB to apply for {@code mode}: the requested tag, else the other one, else 0; never above 0 (no clipping). */
    double gainDb(ReplayGainMode mode) {
        if (mode == ReplayGainMode.OFF) return 0.0;
        double wanted = mode == ReplayGainMode.TRACK ? trackDb : albumDb;
        double other = mode == ReplayGainMode.TRACK ? albumDb : trackDb;
        double db = !Double.isNaN(wanted) ? wanted : Double.isNaN(other) ? 0.0 : other;
        return Math.max(MIN_DB, Math.min(0.0, db));
    }

    /** Linear player volume for {@link #gainDb}. */
    float volume(ReplayGainMode mode) {
        return (float) Math.pow(10.0, gainDb(mode) / 20.0);
    }

    /** Gains of the first selected audio track that carries any. */
    static ReplayGain from(Tracks tracks) {
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != androidx.media3.common.C.TRACK_TYPE_AUDIO || !group.isSelected()) continue;
            for (int i = 0; i < group.length; i++) {
                Metadata metadata = group.getTrackFormat(i).metadata;
                ReplayGain found = metadata == null ? NONE : from(metadata);
                if (found != NONE) return found;
            }
        }
        return NONE;
    }

    static ReplayGain from(Metadata metadata) {
        double track = Double.NaN;
        double album = Double.NaN;
        for (int i = 0; i < metadata.length(); i++) {
            Metadata.Entry e = metadata.get(i);
            String key = null;
            String value = null;
            if (e instanceof TextInformationFrame && "TXXX".equals(((TextInformationFrame) e).id)) {
                TextInformationFrame f = (TextInformationFrame) e;
                key = f.description;
                value = f.values.isEmpty() ? null : f.values.get(0);
            } else if (e instanceof VorbisComment) {
                key = ((VorbisComment) e).key;
                value = ((VorbisComment) e).value;
            } else if (e instanceof InternalFrame) {
                key = ((InternalFrame) e).description;
                value = ((InternalFrame) e).text;
            }
            if (TRACK_KEY.equalsIgnoreCase(key)) track = parseDb(value);
            else if (ALBUM_KEY.equalsIgnoreCase(key)) album = parseDb(value);
        }
        return Double.isNaN(track) && Double.isNaN(album) ? NONE : new ReplayGain(track, album);
    }
}
