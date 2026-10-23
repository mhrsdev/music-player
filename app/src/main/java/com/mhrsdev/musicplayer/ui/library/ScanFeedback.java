package com.mhrsdev.musicplayer.ui.library;

import android.content.Context;
import android.widget.Toast;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.domain.model.ScanResult;

/** The one-line answer to a manual rescan (empty-state button, pull-to-refresh): "N new songs added" / "Library up to date". */
public class ScanFeedback {
    private ScanFeedback() { }

    public static void show(Context context, ScanResult r) {
        String text = !r.ok() ? context.getString(R.string.scan_failed)
                : r.added() > 0 ? context.getResources().getQuantityString(R.plurals.scan_added, r.added(), r.added())
                : context.getString(R.string.scan_up_to_date);
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }
}
