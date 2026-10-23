package com.mhrsdev.musicplayer.ui.library;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import androidx.annotation.Nullable;
import java.util.Locale;

/** Bolds the first case-insensitive match of the query (DESIGN.md search match highlight). */
public final class Highlight {
    private Highlight() { }

    public static CharSequence apply(String text, @Nullable String query) {
        if (query == null || query.isBlank()) return text;
        int i = text.toLowerCase(Locale.ROOT).indexOf(query.trim().toLowerCase(Locale.ROOT));
        if (i < 0) return text;
        SpannableString s = new SpannableString(text);
        s.setSpan(new StyleSpan(Typeface.BOLD), i, i + query.trim().length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }
}
