package com.mhrsdev.musicplayer.ui.library;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.databinding.U2SheetBinding;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.model.TrackDetails;
import com.mhrsdev.musicplayer.ui.nav.Screens;
import com.mhrsdev.musicplayer.ui.theme.Tokens;
import com.mhrsdev.musicplayer.ui.widget.ScriptFont;
import java.util.Collections;
import java.util.Locale;

/** Read-only "Track details": tags, file facts (from MediaStore, loaded on demand) and play count. */
public class TrackDetailsSheet extends BottomSheetDialogFragment {
    private static final String TAG = "track_details";
    @Nullable private Track track;
    @Nullable private U2SheetBinding b;

    public static void show(FragmentActivity a, Track track) {
        if (a.getSupportFragmentManager().findFragmentByTag(TAG) != null) return;
        TrackDetailsSheet s = new TrackDetailsSheet();
        s.track = track;
        s.show(a.getSupportFragmentManager(), TAG);
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = U2SheetBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        if (track == null) {
            dismissAllowingStateLoss();
            return;
        }
        Track t = track;
        b.sheetArt.setCornerRadiusDp(12);
        b.sheetArt.setCovers(t.artworkUri() == null ? Collections.emptyList() : Collections.singletonList(t.artworkUri()),
                t.placeholderSeed());
        ScriptFont.setText(b.sheetTitle, t.title());
        b.sheetSubtitle.setText(R.string.u2_details);
        Screens.container(this).libraryBrowser().loadDetails(t, this::render);
    }

    private void render(TrackDetails d) {
        if (b == null) return;
        Track t = d.track();
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        LinearLayout rows = b.sheetRows;
        rows.removeAllViews();
        row(rows, R.string.u2_detail_title, t.title());
        row(rows, R.string.u2_detail_artist, t.artistName());
        row(rows, R.string.u2_detail_album, t.albumTitle());
        row(rows, R.string.u2_detail_year, t.year() > 0 ? String.format(locale, "%d", t.year()) : null);
        row(rows, R.string.u2_detail_track_no, t.trackNumber() > 0 ? String.format(locale, "%d", t.trackNumber()) : null);
        row(rows, R.string.u2_detail_duration, Format.duration(t.durationMs(), locale));
        row(rows, R.string.u2_detail_bitrate, d.bitrateKbps() > 0 ? getString(R.string.u2_kbps, d.bitrateKbps()) : null);
        row(rows, R.string.u2_detail_format, format(d.mimeType()));
        row(rows, R.string.u2_detail_size, d.sizeBytes() > 0 ? Formatter.formatShortFileSize(requireContext(), d.sizeBytes()) : null);
        row(rows, R.string.u2_detail_path, d.path());
        row(rows, R.string.u2_detail_added, t.addedAtMs() > 0 ? DateUtils.formatDateTime(requireContext(), t.addedAtMs(),
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR) : null);
        row(rows, R.string.u2_detail_plays, String.format(locale, "%d", d.playCount()));
    }

    @Nullable private static String format(@Nullable String mime) {
        if (mime == null) return null;
        int slash = mime.indexOf('/');
        String sub = slash < 0 ? mime : mime.substring(slash + 1);
        if (sub.equals("mpeg")) sub = "mp3";
        return sub.toUpperCase(Locale.ROOT);
    }

    private void row(LinearLayout parent, int label, @Nullable String value) {
        if (value == null || value.isEmpty()) return;
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        int dp = Math.round(getResources().getDisplayMetrics().density);
        row.setPadding(24 * dp, 8 * dp, 24 * dp, 8 * dp);
        row.setFocusable(true); // label + value read as one item
        TextView l = new TextView(requireContext());
        l.setText(label);
        l.setTextSize(12);
        l.setTextColor(Tokens.onSurfaceVariant(requireContext()));
        l.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        TextView v = new TextView(requireContext());
        ScriptFont.setText(v, value);
        v.setTextSize(16);
        v.setTextColor(Tokens.onSurface(requireContext()));
        v.setTextIsSelectable(label == R.string.u2_detail_path);
        v.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        v.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        row.addView(l);
        row.addView(v);
        row.setContentDescription(getString(label) + ": " + value);
        parent.addView(row);
    }

    @Override public void onStart() {
        super.onStart();
        View v = getView();
        if (v != null && v.getParent() instanceof View parent) parent.setBackgroundColor(android.graphics.Color.TRANSPARENT);
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
