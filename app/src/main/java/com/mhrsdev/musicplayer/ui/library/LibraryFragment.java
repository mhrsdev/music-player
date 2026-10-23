package com.mhrsdev.musicplayer.ui.library;

import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.databinding.LibraryFragmentBinding;
import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator.Collection;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator.TrackList;
import com.mhrsdev.musicplayer.ui.nav.Screens;
import com.mhrsdev.musicplayer.ui.widget.ArtworkView;
import java.util.ArrayList;
import java.util.List;

/** Library hub (DESIGN.md 03). */
public final class LibraryFragment extends Fragment {
    private LibraryFragmentBinding b;

    @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        b = LibraryFragmentBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        AppContainer c = Screens.container(this);
        LibraryViewModel vm = new ViewModelProvider(this, LibraryViewModel.factory(c.library, c.player))
                .get(LibraryViewModel.class);
        Resources r = getResources();
        String likedLabel = getString(R.string.library_liked);

        b.likedCard.setOnClickListener(v -> Screens.openTrackList(this, TrackList.LIKED, null));
        b.likedPlay.setOnClickListener(v -> vm.playLiked(likedLabel));
        vm.getLiked().observe(getViewLifecycleOwner(), liked -> {
            int n = liked == null ? 0 : liked.size();
            b.likedSub.setText(n == 0 ? getString(R.string.library_liked_empty)
                    : r.getQuantityString(R.plurals.library_songs, n, n) + " · "
                    + Format.duration(LibraryViewModel.totalMs(liked), r.getConfiguration().getLocales().get(0)));
            b.likedPlay.setEnabled(n > 0);
            b.likedPlay.setAlpha(n > 0 ? 1f : 0.38f);
            List<String> uris = new ArrayList<>(3);
            List<Integer> seeds = new ArrayList<>(3);
            for (int i = 0; i < Math.min(3, n); i++) {
                uris.add(liked.get(i).artworkUri());
                seeds.add(liked.get(i).placeholderSeed());
            }
            b.likedBg.setCovers(uris, seeds);
        });

        b.hubAlbums.setOnClickListener(v -> Screens.openCollection(this, Collection.ALBUMS));
        b.hubPlaylists.setOnClickListener(v -> Screens.openCollection(this, Collection.PLAYLISTS));
        b.hubArtists.setOnClickListener(v -> Screens.openCollection(this, Collection.ARTISTS));
        b.hubHistory.setOnClickListener(v -> Screens.openTrackList(this, TrackList.HISTORY, null));

        vm.getAlbums().observe(getViewLifecycleOwner(), list -> {
            int n = list.size();
            b.hubAlbumsSub.setText(r.getQuantityString(R.plurals.library_albums_count, n, n));
            ArtworkView[] peeks = {b.hubAlbumsPeek, b.hubAlbumsPeek2, b.hubAlbumsPeek3};
            for (int i = 0; i < peeks.length; i++) {
                peeks[i].setVisibility(i < n ? View.VISIBLE : View.GONE);
                if (i < n) Art.bind(peeks[i], list.get(i).artworkUri(), list.get(i).placeholderSeed(), 8, false);
            }
        });
        vm.getPlaylists().observe(getViewLifecycleOwner(), list -> {
            int n = list.size();
            b.hubPlaylistsSub.setText(r.getQuantityString(R.plurals.library_playlists_count, n, n));
            ArtworkView[] peeks = {b.hubPlaylistsPeek, b.hubPlaylistsPeek2, b.hubPlaylistsPeek3};
            for (int i = 0; i < peeks.length; i++) {
                peeks[i].setVisibility(i < n ? View.VISIBLE : View.GONE);
                if (i < n) {
                    List<String> art = list.get(i).artworkUris();
                    Art.bind(peeks[i], art.isEmpty() ? null : art.get(0), list.get(i).placeholderSeed(), 8, false);
                }
            }
        });
        vm.getArtists().observe(getViewLifecycleOwner(), list -> {
            int n = list.size();
            b.hubArtistsSub.setText(r.getQuantityString(R.plurals.library_artists_count, n, n));
            ArtworkView[] peeks = {b.hubArtistsPeek, b.hubArtistsPeek2, b.hubArtistsPeek3};
            for (int i = 0; i < peeks.length; i++) {
                peeks[i].setVisibility(i < n ? View.VISIBLE : View.GONE);
                if (i < n) Art.artist(peeks[i], list.get(i).imageUri(), list.get(i).name(), list.get(i).placeholderSeed());
            }
        });
        vm.getHistory().observe(getViewLifecycleOwner(), list -> b.hubHistorySub.setText(list.isEmpty()
                ? getString(R.string.library_history_empty) : list.get(0).title()));

        CardAdapter recent = new CardAdapter((list, pos) ->
                vm.play(list, pos, getString(R.string.library_recently_saved)));
        b.recent.setLayoutManager(new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
        b.recent.setAdapter(recent);
        vm.getRecentlySaved().observe(getViewLifecycleOwner(), recent::submitList);
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
