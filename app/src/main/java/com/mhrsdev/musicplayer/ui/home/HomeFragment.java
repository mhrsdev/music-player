package com.mhrsdev.musicplayer.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.databinding.HomeFragmentBinding;
import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.domain.model.HomeItem;
import com.mhrsdev.musicplayer.domain.model.HomeSection;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.ui.library.StateView;
import com.mhrsdev.musicplayer.ui.nav.AudioPermission;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator;
import com.mhrsdev.musicplayer.ui.nav.Screens;
import java.util.List;

/** Home: Recently added hero carousel, Throwbacks carousel, Quick picks list (DESIGN.md 01). */
public final class HomeFragment extends Fragment implements HomeAdapter.Listener {
    private HomeFragmentBinding b;
    private HomeViewModel vm;
    private boolean scanning;
    @Nullable private List<HomeSection> sections;
    private final ActivityResultLauncher<String> requestAudio = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) Screens.container(this).onAudioPermissionGranted();
            });

    @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        b = HomeFragmentBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        AppContainer c = Screens.container(this);
        vm = new ViewModelProvider(this, HomeViewModel.factory(c.homeFeed, c.player)).get(HomeViewModel.class);
        HomeAdapter adapter = new HomeAdapter(this);
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(adapter);
        StateView.loading(b.state, R.string.home_loading);
        vm.getSections().observe(getViewLifecycleOwner(), s -> {
            sections = s;
            render(adapter);
        });
        // The first scan finishes after Home's first (empty) load: show "scanning", not "library is empty", meanwhile.
        c.libraryAdmin().isScanning().observe(getViewLifecycleOwner(), s -> {
            scanning = Boolean.TRUE.equals(s);
            render(adapter);
        });
    }

    private void render(HomeAdapter adapter) {
        if (sections == null || (sections.isEmpty() && scanning)) {
            StateView.loading(b.state, R.string.home_loading);
        } else if (sections.isEmpty()) {
            boolean granted = AudioPermission.granted(requireContext());
            StateView.empty(b.state, getString(R.string.home_empty_title), getString(R.string.home_empty_body),
                    getString(granted ? R.string.home_empty_rescan : R.string.nav_perm_allow), v -> {
                        if (granted) Screens.container(this).libraryAdmin().rescan(null);
                        else requestAudio.launch(AudioPermission.name());
                    });
        } else {
            StateView.hide(b.state);
        }
        adapter.submitList(sections == null ? List.of() : HomeAdapter.flatten(sections));
    }

    @Override public void onItemClick(HomeItem item) {
        switch (item.kind()) {
            case TRACK -> {
                List<HomeSection> all = vm.getSections().getValue();
                if (all == null) return;
                for (HomeSection s : all) {
                    if (s.items().contains(item)) {
                        vm.playFromSection(s, item.track());
                        return;
                    }
                }
            }
            case ALBUM -> Screens.openTrackList(this, ScreenNavigator.TrackList.ALBUM, item.album().id());
            case PLAYLIST -> Screens.openTrackList(this, ScreenNavigator.TrackList.PLAYLIST, item.playlist().id());
        }
    }

    @Override public void onTrackClick(HomeSection section, Track track) {
        vm.playFromSection(section, track);
    }

    @Override public void onSeeAll(HomeSection section) {
        Screens.openTrackList(this, ScreenNavigator.TrackList.RECENT, null);
    }

    @Override public void onShuffle(HomeSection section) {
        vm.shuffleSection(section);
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
