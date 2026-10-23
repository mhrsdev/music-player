package com.mhrsdev.musicplayer.ui.home;

import android.content.res.Resources;
import androidx.annotation.StringRes;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.domain.model.HomeSection;

/**
 * Home section titles come from the data layer in English; the UI shows them in the app language by section id.
 * Ids are stable; an unknown id falls back to the title the data layer gave.
 */
class HomeSectionTitles {
    private HomeSectionTitles() { }

    @StringRes
    static int titleRes(String id) {
        if (id == null) return 0;
        return switch (id) {
            case "new-releases", "recently-added" -> R.string.home_section_recent;
            case "throwbacks" -> R.string.home_section_throwbacks;
            case "playlists" -> R.string.home_section_playlists;
            case "quick-picks" -> R.string.home_section_quick_picks;
            case "continue", "continue-listening", "history" -> R.string.home_section_continue;
            case "most-played" -> R.string.home_section_most_played;
            case "liked" -> R.string.home_section_liked;
            default -> 0;
        };
    }

    static String title(Resources r, HomeSection s) {
        int res = titleRes(s.id());
        return res == 0 ? s.title() : r.getString(res);
    }
}
