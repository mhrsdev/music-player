package com.mhrsdev.musicplayer.ui.search;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** The last few searches the person actually ran (IME search or a tapped result), newest first. Local only. */
public class RecentSearches {
    static final int MAX = 8;
    private static final String PREFS = "search", KEY = "recent";

    @Nullable private final SharedPreferences prefs;
    private List<String> items;

    public RecentSearches(Context context) {
        this(context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE));
    }

    /** {@code prefs == null}: in-memory only (tests). */
    RecentSearches(@Nullable SharedPreferences prefs) {
        this.prefs = prefs;
        String raw = prefs == null ? "" : prefs.getString(KEY, "");
        items = new ArrayList<>();
        for (String s : raw.split("\n")) if (!s.isBlank()) items.add(s);
    }

    public List<String> get() {
        return Collections.unmodifiableList(items);
    }

    public void add(String query) {
        items = push(items, query);
        save();
    }

    public void remove(String query) {
        items = new ArrayList<>(items);
        items.remove(query);
        save();
    }

    public void clear() {
        items = new ArrayList<>();
        save();
    }

    /** Adds {@code q} on top, drops case-insensitive duplicates, keeps {@link #MAX}. Pure for tests. */
    static List<String> push(List<String> list, String q) {
        String t = q == null ? "" : q.trim().replace('\n', ' ');
        List<String> out = new ArrayList<>(list);
        if (t.isEmpty()) return out;
        String key = t.toLowerCase(Locale.ROOT);
        out.removeIf(s -> s.toLowerCase(Locale.ROOT).equals(key));
        out.add(0, t);
        while (out.size() > MAX) out.remove(out.size() - 1);
        return out;
    }

    private void save() {
        if (prefs != null) prefs.edit().putString(KEY, String.join("\n", items)).apply();
    }
}
