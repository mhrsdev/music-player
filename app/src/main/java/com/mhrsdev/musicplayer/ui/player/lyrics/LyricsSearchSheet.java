package com.mhrsdev.musicplayer.ui.player.lyrics;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.color.MaterialColors;
import com.mhrsdev.musicplayer.MusicPlayerApp;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.domain.model.LyricsCandidate;
import com.mhrsdev.musicplayer.ui.player.PlayerFormat;
import com.mhrsdev.musicplayer.ui.player.PlayerViewModel;

import java.util.Locale;

/**
 * "Search manually": artist + title (prefilled with the cleaned tags) → LRCLIB search → candidates; tapping one keeps
 * it as the track's lyrics. Shown in the player fragment's child fragment manager (it talks to the player's VM).
 */
public class LyricsSearchSheet extends BottomSheetDialogFragment {
    public static final String TAG = "lyrics_search";
    private static final String ARG_ARTIST = "artist", ARG_TITLE = "title";

    private LinearLayout results;
    private TextView status;
    private ProgressBar progress;
    private LyricsSearchViewModel vm;

    public static void show(@NonNull Fragment player, String artist, String title) {
        if (player.getChildFragmentManager().findFragmentByTag(TAG) != null) return;
        LyricsSearchSheet s = new LyricsSearchSheet();
        Bundle a = new Bundle();
        a.putString(ARG_ARTIST, artist);
        a.putString(ARG_TITLE, title);
        s.setArguments(a);
        s.show(player.getChildFragmentManager(), TAG);
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        Context c = requireContext();
        vm = new ViewModelProvider(this, LyricsSearchViewModel.factory(
                ((MusicPlayerApp) requireActivity().getApplication()).getContainer().lyrics))
                .get(LyricsSearchViewModel.class);
        int onSurface = MaterialColors.getColor(c, com.google.android.material.R.attr.colorOnSurface, 0xFF000000);
        int onVariant = MaterialColors.getColor(c, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF444444);

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(24));
        TextView heading = text(c, getString(R.string.lyrics_search_manually), 22, onSurface);
        heading.setTypeface(heading.getTypeface(), android.graphics.Typeface.BOLD);
        androidx.core.view.ViewCompat.setAccessibilityHeading(heading, true);
        root.addView(heading);

        EditText title = field(c, R.string.lyrics_field_title, requireArguments().getString(ARG_TITLE), onSurface, onVariant);
        EditText artist = field(c, R.string.lyrics_field_artist, requireArguments().getString(ARG_ARTIST), onSurface, onVariant);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        root.addView(artist, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        artist.setImeOptions(EditorInfo.IME_ACTION_SEARCH);

        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        Button go = new Button(c, null, 0, com.google.android.material.R.style.Widget_Material3_Button_TonalButton);
        go.setText(R.string.lyrics_search);
        go.setMinHeight(dp(48));
        row.addView(go, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        progress = new ProgressBar(c);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(24), dp(24));
        plp.setMarginStart(dp(16));
        row.addView(progress, plp);
        root.addView(row);

        status = text(c, null, 14, onVariant);
        root.addView(status);
        NestedScrollView scroll = new NestedScrollView(c);
        results = new LinearLayout(c);
        results.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(results);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(320)));

        Runnable doSearch = () -> vm.search(artist.getText().toString(), title.getText().toString());
        go.setOnClickListener(v -> doSearch.run());
        artist.setOnEditorActionListener((v, id, e) -> { doSearch.run(); return true; });
        vm.getState().observe(getViewLifecycleOwner(), s -> render(s, onSurface, onVariant));
        if (state == null && !TextUtils.isEmpty(requireArguments().getString(ARG_TITLE))) doSearch.run();
        return root;
    }

    private void render(LyricsSearchViewModel.State s, int onSurface, int onVariant) {
        progress.setVisibility(s.loading() ? View.VISIBLE : View.GONE);
        if (s.failed()) status.setText(R.string.lyrics_search_failed);
        else if (s.searched() && s.results().isEmpty() && !s.loading()) status.setText(R.string.lyrics_search_empty);
        else status.setText(null);
        status.setVisibility(TextUtils.isEmpty(status.getText()) ? View.GONE : View.VISIBLE);
        results.removeAllViews();
        Locale loc = getResources().getConfiguration().getLocales().get(0);
        for (LyricsCandidate cand : s.results()) {
            LinearLayout item = new LinearLayout(requireContext());
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(0, dp(10), 0, dp(10));
            item.setMinimumHeight(dp(56));
            item.setBackgroundResource(android.R.drawable.list_selector_background);
            TextView t = text(requireContext(), cand.title(), 16, onSurface);
            String meta = cand.artist() + " · " + PlayerFormat.time(cand.durationMs(), loc) + " · "
                    + getString(cand.synced() ? R.string.lyrics_synced : R.string.lyrics_plain);
            TextView m = text(requireContext(), meta, 13, onVariant);
            item.addView(t);
            item.addView(m);
            item.setContentDescription(cand.title() + ", " + meta);
            item.setOnClickListener(v -> {
                new ViewModelProvider(requireParentFragment()).get(PlayerViewModel.class).useLyricsCandidate(cand);
                dismiss();
            });
            results.addView(item);
        }
    }

    private EditText field(Context c, int hint, @Nullable String value, int fg, int hintColor) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setText(value);
        e.setTextColor(fg);
        e.setHintTextColor(hintColor);
        e.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        e.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        return e;
    }

    private static TextView text(Context c, @Nullable String s, int sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
