package com.mhrsdev.musicplayer.ui.home;

import android.content.Context;
import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.domain.model.HomeMix;
import com.mhrsdev.musicplayer.domain.model.HomeSection;
import com.mhrsdev.musicplayer.domain.model.Track;

/** Localized copy for Home: the feed returns section ids + parameters, this maps them to resource strings. */
class SectionText {
    private SectionText() { }

    static String title(Context c, HomeSection s) {
        int res = switch (s.id()) {
            case "new-releases" -> R.string.home_sec_new_releases;
            case "continue" -> R.string.home_sec_continue;
            case "jump-back-in" -> R.string.home_sec_jump_back_in;
            case "mixes" -> R.string.home_sec_mixes;
            case "quick-picks" -> R.string.home_sec_quick_picks;
            case "playlists" -> R.string.home_sec_playlists;
            case "favorites" -> R.string.home_sec_favorites;
            case "top-month" -> R.string.home_sec_top_month;
            case "top-all-time" -> R.string.home_sec_top_all_time;
            case "forgotten" -> R.string.home_sec_forgotten;
            case "artists" -> R.string.home_sec_artists;
            case "unfinished" -> R.string.home_sec_unfinished;
            case "random-albums" -> R.string.home_sec_random;
            case "decades" -> R.string.home_sec_decades;
            case "genres" -> R.string.home_sec_genres;
            case "folders" -> R.string.home_sec_folders;
            case "stats" -> R.string.home_sec_stats;
            case "throwbacks" -> R.string.home_sec_throwbacks;
            case "on-this-day" -> 0;
            default -> -1;
        };
        if (res == 0) return c.getString(R.string.home_sec_on_this_day, number(c, s.arg()));
        return res > 0 ? c.getString(res) : s.title();
    }

    @Nullable static String subtitle(Context c, HomeSection s) {
        int res = switch (s.id()) {
            case "mixes" -> R.string.home_sub_mixes;
            case "forgotten" -> R.string.home_sub_forgotten;
            case "throwbacks" -> R.string.home_sub_throwbacks;
            case "random-albums" -> R.string.home_sub_random;
            default -> 0;
        };
        return res == 0 ? null : c.getString(res);
    }

    static String mixTitle(Context c, HomeMix m) {
        return switch (m.kind()) {
            case DAILY -> c.getString(R.string.home_mix_daily, number(c, m.arg()));
            case LATE_NIGHT -> c.getString(R.string.home_mix_late_night);
            case MORNING -> c.getString(R.string.home_mix_morning);
            case UPBEAT -> c.getString(R.string.home_mix_upbeat);
            case DECADE -> c.getString(R.string.home_mix_decade, number(c, m.arg()));
            case GENRE, FOLDER -> m.arg();
        };
    }

    /** "with A, B, C" for mixes; the song count for decade / genre / folder groups. */
    static String mixSubtitle(Context c, HomeMix m) {
        if (m.kind() == HomeMix.Kind.DECADE || m.kind() == HomeMix.Kind.GENRE || m.kind() == HomeMix.Kind.FOLDER) {
            return c.getResources().getQuantityString(R.plurals.library_songs, m.tracks().size(), m.tracks().size());
        }
        StringBuilder names = new StringBuilder();
        for (String n : m.artistNames()) {
            if (names.length() > 0) names.append(c.getString(R.string.home_list_separator));
            names.append(isolate(artist(c, n)));
        }
        return c.getString(R.string.home_mix_with, names);
    }

    /** Localized label for the scanner's "Unknown artist" placeholder. */
    static String artist(Context c, String name) {
        return Track.UNKNOWN_ARTIST.equals(name) ? c.getString(R.string.home_unknown_artist) : name;
    }

    static String album(Context c, String title) {
        return Track.UNKNOWN_ALBUM.equals(title) ? c.getString(R.string.home_unknown_album) : title;
    }

    /** First-strong isolate: a Latin name inside a Persian line (or the reverse) keeps its own order and the line's
     * ellipsis lands at the visual end of the line's (locale) direction. */
    static String isolate(String s) {
        return "\u2068" + s + "\u2069";
    }

    /** Digits in the UI locale (Persian digits in fa) for numeric args like a year or a mix number. */
    static String number(Context c, @Nullable String arg) {
        if (arg == null) return "";
        try {
            java.text.NumberFormat f = java.text.NumberFormat.getIntegerInstance(
                    c.getResources().getConfiguration().getLocales().get(0));
            f.setGroupingUsed(false);
            return f.format(Long.parseLong(arg));
        } catch (NumberFormatException e) {
            return arg;
        }
    }
}
