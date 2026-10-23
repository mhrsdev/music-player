package com.mhrsdev.musicplayer.ui.library;

import android.content.Context;
import android.content.SharedPreferences;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator.TrackList;

/** Remembers the chosen {@link SortOrder} per kind of list (own small prefs file, read once per screen). */
public final class SortPrefs {
    private static final String FILE = "library_sort";
    private final SharedPreferences prefs;

    public SortPrefs(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public SortOrder get(TrackList kind) {
        return SortOrder.decode(prefs.getString(kind.name(), null));
    }

    public void put(TrackList kind, SortOrder order) {
        prefs.edit().putString(kind.name(), order.encode()).apply();
    }

    /** Lists whose natural order is meaningful to the user and that can be re-sorted. */
    public static boolean sortable(TrackList kind) {
        return kind != TrackList.PLAYLIST && kind != TrackList.HISTORY;
    }
}
