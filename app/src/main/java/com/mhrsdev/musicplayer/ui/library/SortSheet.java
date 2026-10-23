package com.mhrsdev.musicplayer.ui.library;

import android.os.Bundle;
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
    /** Fragment-result key; the result bundle carries the picked order under {@link #RESULT_ORDER}. */
    public static final String RESULT_KEY = "u2_sort_result";
    private static final String RESULT_ORDER = "order";
    private static final String ARG_CURRENT = "current";

    /** The pick is delivered as a fragment result on {@code fm} (see {@link #listen}). */
    public static void show(FragmentManager fm, SortOrder current) {
        if (fm.findFragmentByTag(TAG) != null) return;
        SortSheet s = new SortSheet();
        Bundle args = new Bundle();
        args.putString(ARG_CURRENT, current.encode());
        s.setArguments(args);
        s.show(fm, TAG);
    }

    /** Registers the receiver of picks for as long as {@code owner} lives (survives sheet recreation). */
    public static void listen(FragmentManager fm, androidx.lifecycle.LifecycleOwner owner, Callback<SortOrder> onPick) {
        fm.setFragmentResultListener(RESULT_KEY, owner,
                (key, result) -> onPick.onResult(SortOrder.decode(result.getString(RESULT_ORDER))));
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
        SortOrder cur = SortOrder.decode(requireArguments().getString(ARG_CURRENT));
        FragmentManager fm = getParentFragmentManager();
        Callback<SortOrder> cb = picked -> {
            Bundle result = new Bundle();
            result.putString(RESULT_ORDER, picked.encode());
            fm.setFragmentResult(RESULT_KEY, result);
        };
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
