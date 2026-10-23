package com.mhrsdev.musicplayer.ui.library;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentManager;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.domain.repository.Callback;
import com.mhrsdev.musicplayer.ui.library.SortOrder.Field;
import java.util.List;

/** "Sort by" sheet: one row per field (current one checked) and the direction. */
public class SortSheet extends ActionSheet {
    private static final String TAG = "sort_sheet";
    @Nullable private SortOrder current;
    @Nullable private Callback<SortOrder> onPick;

    public static void show(FragmentManager fm, SortOrder current, Callback<SortOrder> onPick) {
        if (fm.findFragmentByTag(TAG) != null) return;
        SortSheet s = new SortSheet();
        s.current = current;
        s.onPick = onPick;
        s.show(fm, TAG);
    }

    @Override protected boolean hasState() {
        return current != null && onPick != null;
    }

    @StringRes public static int label(Field f) {
        return switch (f) {
            case DEFAULT -> R.string.u2_sort_default;
            case TITLE -> R.string.u2_sort_title;
            case ARTIST -> R.string.u2_sort_artist;
            case ALBUM -> R.string.u2_sort_album;
            case DATE_ADDED -> R.string.u2_sort_date_added;
            case DURATION -> R.string.u2_sort_duration;
            case MOST_PLAYED -> R.string.u2_sort_most_played;
        };
    }

    @Nullable @Override protected Header build(List<Row> rows) {
        SortOrder cur = current;
        Callback<SortOrder> cb = onPick;
        for (Field f : Field.values()) {
            // newest / longest / most played first is what people want when they pick those fields
            boolean naturalDesc = f == Field.DATE_ADDED || f == Field.DURATION || f == Field.MOST_PLAYED;
            rows.add(new Row(0, getString(label(f)), false, cur.field() == f,
                    () -> cb.onResult(new SortOrder(f, cur.field() == f ? cur.ascending() : !naturalDesc))));
        }
        rows.add(new Row(R.drawable.ic_mp_expand_down, getString(R.string.u2_sort_reverse), false, !cur.ascending(),
                () -> cb.onResult(new SortOrder(cur.field(), !cur.ascending()))));
        return new Header(null, 0, getString(R.string.u2_sort_by), null);
    }
}
