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
    private HomeBackdrop backdrop;
    @Nullable private String backdropTrackId;
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
            if (backdropTrackId == null && backdrop != null) tintFromHeroStart();
        });
        // The first scan finishes after Home's first (empty) load: show "scanning", not "library is empty", meanwhile.
        c.libraryAdmin().isScanning().observe(getViewLifecycleOwner(), s -> {
            scanning = Boolean.TRUE.equals(s);
            render(adapter);
        });
        // Continue card progress: only the one row rebinds, quantized to 2 % steps (no per-tick layout).
        backdrop = new HomeBackdrop(requireActivity(), c.ioExecutor);
        c.player.getState().observe(getViewLifecycleOwner(), st -> {
            if (st == null || st.currentTrack() == null) {
                adapter.setNowPlaying(null, -1f);
                if (backdropTrackId == null) tintFromHeroStart();
            } else {
                Track now = st.currentTrack();
                if (!now.id().equals(backdropTrackId)) { // track changed: the playing cover wins again
                    backdropTrackId = now.id();
                    backdrop.show(now.artworkUri(), now.placeholderSeed());
                }
                long dur = st.durationMs() > 0 ? st.durationMs() : st.currentTrack().durationMs();
                adapter.setNowPlaying(st.currentTrack().id(), dur > 0 ? Math.min(1f, st.positionMs() / (float) dur) : 0f);
            }
        });
        installPullToRefresh();
    }

    /**
     * Pull-to-refresh without a new dependency: a downward drag while the list is at its top reshuffles the discovery
     * seed (random picks change, section order stays for the day). One haptic tick, no animation loop.
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private void installPullToRefresh() {
        float threshold = 96 * getResources().getDisplayMetrics().density;
        float[] startY = {Float.NaN};
        b.list.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN ->
                        startY[0] = b.list.canScrollVertically(-1) ? Float.NaN : e.getY();
                case android.view.MotionEvent.ACTION_MOVE -> {
                    if (!Float.isNaN(startY[0]) && e.getY() - startY[0] > threshold) {
                        startY[0] = Float.NaN;
                        refresh();
                    }
                }
                default -> startY[0] = Float.NaN;
            }
            return false;
        });
    }

    private void refresh() {
        vm.refresh();
        b.list.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
        b.list.announceForAccessibility(getString(R.string.home_refreshed));
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
                        vm.playFromSection(s, item.track(), SectionText.title(requireContext(), s));
                        return;
                    }
                }
            }
            case ALBUM -> Screens.openTrackList(this, ScreenNavigator.TrackList.ALBUM, item.album().id());
            case PLAYLIST -> Screens.openTrackList(this, ScreenNavigator.TrackList.PLAYLIST, item.playlist().id());
            case ARTIST -> Screens.openTrackList(this, ScreenNavigator.TrackList.ARTIST, item.artist().id());
            case MIX -> SectionPageFragment.open(this, null, item.mix().id());
        }
    }

    @Override public void onTrackClick(HomeSection section, Track track) {
        vm.playFromSection(section, track, SectionText.title(requireContext(), section));
    }

    @Override public void onSeeAll(HomeSection section) {
        if ("new-releases".equals(section.id())) {
            Screens.openTrackList(this, ScreenNavigator.TrackList.RECENT, null);
        } else {
            SectionPageFragment.open(this, section.id(), null);
        }
    }

    @Override public void onShuffle(HomeSection section) {
        vm.shuffleSection(section, SectionText.title(requireContext(), section));
    }

    @Override public void onHeroFocused(HomeItem item) {
        if (backdrop == null) return;
        switch (item.kind()) {
            case ALBUM -> backdrop.show(item.album().artworkUri(), item.album().placeholderSeed());
            case TRACK -> backdrop.show(item.track().artworkUri(), item.track().placeholderSeed());
            case PLAYLIST -> backdrop.show(item.playlist().artworkUris().isEmpty() ? null
                    : item.playlist().artworkUris().get(0), item.playlist().placeholderSeed());
            default -> { }
        }
    }

    /** Nothing playing: the first hero card (when there is one) tints the glow. */
    private void tintFromHeroStart() {
        if (sections == null) return;
        for (HomeSection s : sections) {
            if (s.style() == com.mhrsdev.musicplayer.domain.model.HomeSectionStyle.HERO_CAROUSEL && !s.items().isEmpty()) {
                onHeroFocused(s.items().get(0));
                return;
            }
        }
    }

    @Override public void onContinue(Track track) {
        vm.continueListening(track, getString(R.string.home_sec_continue));
    }

    @Override public void onShuffleLibrary() {
        playOnce(Screens.container(this).library.getAllTracks(), getString(R.string.home_shuffle_library));
    }

    /** Plays one of the random album picks (shuffled) and deals a new hand of picks. */
    @Override public void onSurprise() {
        List<HomeSection> all = vm.getSections().getValue();
        if (all == null) return;
        for (HomeSection s : all) {
            if (!"random-albums".equals(s.id()) || s.items().isEmpty()) continue;
            HomeItem pick = s.items().get(new java.util.Random().nextInt(s.items().size()));
            if (pick.kind() == HomeItem.Kind.ALBUM) {
                playOnce(Screens.container(this).library.getAlbumTracks(pick.album().id()), pick.album().title());
            }
        }
        vm.refresh();
    }

    /** Reads a library LiveData once (first non-null value), then shuffles it into the player. */
    private void playOnce(androidx.lifecycle.LiveData<List<Track>> source, String playingFrom) {
        source.observe(getViewLifecycleOwner(), new androidx.lifecycle.Observer<>() {
            @Override public void onChanged(List<Track> tracks) {
                if (tracks == null) return;
                source.removeObserver(this);
                vm.playShuffled(tracks, playingFrom);
            }
        });
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
