package com.mhrsdev.musicplayer.ui.search;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.databinding.SearchFragmentBinding;
import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.domain.model.SearchFilter;
import com.mhrsdev.musicplayer.domain.model.SearchResult;
import com.mhrsdev.musicplayer.databinding.SettingsRowBinding;
import com.mhrsdev.musicplayer.ui.library.StateView;
import com.mhrsdev.musicplayer.ui.library.TrackMenu;
import com.mhrsdev.musicplayer.ui.nav.Adaptive;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator.TrackList;
import com.mhrsdev.musicplayer.ui.nav.Screens;
import java.util.List;

/** Search (DESIGN.md 02): pill field, filter chips, results with match highlight, no-results state. */
public class SearchFragment extends Fragment implements SearchResultAdapter.Listener {
    private SearchFragmentBinding b;
    private SearchViewModel vm;

    @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        b = SearchFragmentBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        AppContainer c = Screens.container(this);
        vm = new ViewModelProvider(this, SearchViewModel.factory(c.library, c.player, new RecentSearches(requireContext())))
                .get(SearchViewModel.class);
        Adaptive.limitWidth(b.getRoot(), Adaptive.CONTENT_MAX_DP);

        SearchResultAdapter adapter = new SearchResultAdapter(this);
        b.results.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.results.setHasFixedSize(true); // match_parent: a keystroke's new results relayout the list only
        b.results.setAdapter(adapter);

        b.query.setText(vm.getQuery().getValue());
        b.query.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c1, int a) { }

            @Override public void onTextChanged(CharSequence s, int st, int bf, int c1) { }

            @Override public void afterTextChanged(Editable s) {
                vm.setQuery(s.toString());
            }
        });
        b.query.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId != EditorInfo.IME_ACTION_SEARCH && !enter) return false;
            vm.commitQuery();
            hideKeyboard();
            return true;
        });
        b.clear.setOnClickListener(v -> {
            b.query.setText("");
            showKeyboard();
        });
        b.recentClear.setOnClickListener(v -> vm.clearRecent());
        vm.getRecent().observe(getViewLifecycleOwner(), list -> {
            renderRecent(list);
            renderMode(vm.getMode().getValue());
        });
        vm.isLibraryEmpty().observe(getViewLifecycleOwner(), e -> renderMode(vm.getMode().getValue()));
        // Opening Search from the nav: put the cursor in the field (keyboard only when the person taps it).
        if (state == null && vm.getQuery().getValue().isEmpty()) b.query.requestFocus();

        TextView[] chips = {b.chipAll, b.chipSongs, b.chipAlbums, b.chipArtists, b.chipPlaylists};
        SearchFilter[] filters = SearchFilter.values();
        for (int i = 0; i < chips.length; i++) {
            SearchFilter f = filters[i];
            chips[i].setOnClickListener(v -> vm.setFilter(f));
        }
        vm.getFilter().observe(getViewLifecycleOwner(), f -> {
            for (int i = 0; i < chips.length; i++) chips[i].setSelected(filters[i] == f);
        });

        vm.getQuery().observe(getViewLifecycleOwner(), q -> {
            b.clear.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
            adapter.setQuery(q);
        });
        vm.getResults().observe(getViewLifecycleOwner(), adapter::submitList);
        vm.getMode().observe(getViewLifecycleOwner(), this::renderMode);
    }

    /** DESIGN.md 13: each state says what happened and offers the next useful move. */
    private void renderMode(@Nullable SearchViewModel.Mode mode) {
        if (b == null || mode == null) return;
        List<String> recent = vm.getRecent().getValue();
        boolean showRecent = mode == SearchViewModel.Mode.PROMPT && recent != null && !recent.isEmpty();
        b.recentScroll.setVisibility(showRecent ? View.VISIBLE : View.GONE);
        b.results.setVisibility(mode == SearchViewModel.Mode.RESULTS ? View.VISIBLE : View.INVISIBLE);
        if (Boolean.TRUE.equals(vm.isLibraryEmpty().getValue()) && mode != SearchViewModel.Mode.RESULTS) {
            b.recentScroll.setVisibility(View.GONE);
            StateView.empty(b.state, getString(R.string.search_empty_library), getString(R.string.search_empty_library_body),
                    getString(R.string.search_open_settings), v -> {
                        ScreenNavigator nav = ScreenNavigator.from(requireContext());
                        if (nav != null) nav.openTab(ScreenNavigator.Tab.SETTINGS);
                    });
            setStateIcon(R.drawable.nav_perm_art, false);
            return;
        }
        switch (mode) {
            case PROMPT -> {
                if (showRecent) {
                    StateView.hide(b.state);
                } else {
                    StateView.empty(b.state, getString(R.string.search_prompt), getString(R.string.search_prompt_body),
                            null, null);
                    setStateIcon(R.drawable.nav_perm_art, false);
                }
            }
            case NO_RESULTS -> {
                boolean filtered = vm.getFilter().getValue() != SearchFilter.ALL;
                StateView.empty(b.state, getString(R.string.search_no_results, vm.getQuery().getValue()),
                        getString(filtered ? R.string.search_no_results_body_filtered : R.string.search_no_results_body),
                        filtered ? getString(R.string.search_try_all) : null,
                        v -> vm.setFilter(SearchFilter.ALL));
                setStateIcon(R.drawable.search_ic_no_results, true);
            }
            case RESULTS -> StateView.hide(b.state);
        }
    }

    /** The no-results glyph sits on a rounded tile (DESIGN.md 13); the art icons are already full tiles. */
    private void setStateIcon(int res, boolean tile) {
        b.state.stateIcon.setImageResource(res);
        int pad = tile ? Math.round(24 * getResources().getDisplayMetrics().density) : 0;
        b.state.stateIcon.setPadding(pad, pad, pad, pad);
        b.state.stateIcon.setBackgroundResource(tile ? R.drawable.search_state_tile : 0);
    }

    private void renderRecent(List<String> list) {
        // Keep the header (child 0), rebuild the rows.
        while (b.recentList.getChildCount() > 1) b.recentList.removeViewAt(1);
        for (String q : list) {
            SettingsRowBinding r = SettingsRowBinding.inflate(getLayoutInflater(), b.recentList, true);
            r.rowIcon.setImageResource(R.drawable.search_ic_history);
            r.rowTitle.setText(q);
            r.getRoot().setMinimumHeight(Math.round(56 * getResources().getDisplayMetrics().density));
            r.getRoot().setOnClickListener(v -> {
                b.query.setText(q);
                b.query.setSelection(q.length());
                vm.commitQuery();
                hideKeyboard();
            });
            r.rowEnd.setVisibility(View.VISIBLE);
            r.rowEnd.setImageResource(R.drawable.search_ic_close);
            r.rowEnd.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            r.rowEnd.setContentDescription(getString(R.string.search_recent_remove, q));
            r.rowEnd.setOnClickListener(v -> vm.removeRecent(q));
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = requireContext().getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(b.query.getWindowToken(), 0);
        b.query.clearFocus();
    }

    private void showKeyboard() {
        b.query.requestFocus();
        InputMethodManager imm = requireContext().getSystemService(InputMethodManager.class);
        if (imm != null) imm.showSoftInput(b.query, InputMethodManager.SHOW_IMPLICIT);
    }

    @Override public void onResultLongClick(View anchor, SearchResult r) {
        if (r.kind() == SearchResult.Kind.TRACK) {
            anchor.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            TrackMenu.show(anchor, r.track());
        }
    }

    @Override public void onResultClick(SearchResult r) {
        vm.commitQuery();
        switch (r.kind()) {
            case TRACK -> vm.playTrack(r.track());
            case ALBUM -> Screens.openTrackList(this, TrackList.ALBUM, r.album().id());
            case ARTIST -> Screens.openTrackList(this, TrackList.ARTIST, r.artist().id());
            case PLAYLIST -> Screens.openTrackList(this, TrackList.PLAYLIST, r.playlist().id());
        }
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
