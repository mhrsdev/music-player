package com.mhrsdev.musicplayer.ui.search;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.mhrsdev.musicplayer.ui.library.StateView;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator.TrackList;
import com.mhrsdev.musicplayer.ui.nav.Screens;

/** Search (DESIGN.md 02): pill field, filter chips, results with match highlight, no-results state. */
public final class SearchFragment extends Fragment implements SearchResultAdapter.Listener {
    private SearchFragmentBinding b;
    private SearchViewModel vm;

    @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        b = SearchFragmentBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        AppContainer c = Screens.container(this);
        vm = new ViewModelProvider(this, SearchViewModel.factory(c.library, c.player)).get(SearchViewModel.class);

        SearchResultAdapter adapter = new SearchResultAdapter(this);
        b.results.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.results.setAdapter(adapter);

        b.query.setText(vm.getQuery().getValue());
        b.query.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c1, int a) { }

            @Override public void onTextChanged(CharSequence s, int st, int bf, int c1) { }

            @Override public void afterTextChanged(Editable s) {
                vm.setQuery(s.toString());
            }
        });
        b.clear.setOnClickListener(v -> b.query.setText(""));

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
        vm.getMode().observe(getViewLifecycleOwner(), mode -> {
            switch (mode) {
                case PROMPT -> StateView.empty(b.state, getString(R.string.search_prompt), null, null, null);
                case NO_RESULTS -> StateView.empty(b.state,
                        getString(R.string.search_no_results, vm.getQuery().getValue()), null,
                        vm.getFilter().getValue() == SearchFilter.ALL ? null : getString(R.string.search_try_all),
                        v -> vm.setFilter(SearchFilter.ALL));
                case RESULTS -> StateView.hide(b.state);
            }
        });
    }

    @Override public void onResultClick(SearchResult r) {
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
