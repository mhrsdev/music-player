package com.mhrsdev.musicplayer.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.databinding.LibraryListHeaderBinding;
import com.mhrsdev.musicplayer.databinding.LibraryTrackListBinding;
import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.domain.model.HomeItem;
import com.mhrsdev.musicplayer.domain.model.HomeMix;
import com.mhrsdev.musicplayer.domain.model.HomeSection;
import com.mhrsdev.musicplayer.domain.model.HomeSectionStyle;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.ui.library.Art;
import com.mhrsdev.musicplayer.ui.library.Format;
import com.mhrsdev.musicplayer.ui.library.StateView;
import com.mhrsdev.musicplayer.ui.library.TrackAdapter;
import com.mhrsdev.musicplayer.ui.library.TrackMenu;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator;
import com.mhrsdev.musicplayer.ui.nav.Screens;
import com.mhrsdev.musicplayer.ui.widget.ScriptFont;
import java.util.ArrayList;
import java.util.List;

/**
 * "See all" for a Home section, or one mix / decade / genre / folder: a header plus either the full track list or a
 * grid of the section's cards. Reads the already-built feed (no extra query); pushed with {@link #open}.
 */
public final class SectionPageFragment extends Fragment implements TrackAdapter.Listener, HomeCardAdapter.Listener {
    private static final String ARG_SECTION = "section";
    private static final String ARG_MIX = "mix";

    private LibraryTrackListBinding b;
    private HomeViewModel vm;
    private String title = "";
    private List<Track> tracks = List.of();

    /** Section page ({@code mixId} null) or one mix's track list. */
    public static SectionPageFragment newInstance(@Nullable String sectionId, @Nullable String mixId) {
        SectionPageFragment f = new SectionPageFragment();
        Bundle a = new Bundle();
        a.putString(ARG_SECTION, sectionId);
        a.putString(ARG_MIX, mixId);
        f.setArguments(a);
        return f;
    }

    /** Pushes the page into the same container as {@code from} (back returns to it). */
    public static void open(Fragment from, @Nullable String sectionId, @Nullable String mixId) {
        from.getParentFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out,
                        android.R.animator.fade_in, android.R.animator.fade_out)
                .replace(from.getId(), newInstance(sectionId, mixId))
                .addToBackStack(null)
                .commit();
    }

    @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        b = LibraryTrackListBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        AppContainer c = Screens.container(this);
        vm = new ViewModelProvider(this, HomeViewModel.factory(c.homeFeed, c.player)).get(HomeViewModel.class);
        b.back.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        String sectionId = requireArguments().getString(ARG_SECTION);
        String mixId = requireArguments().getString(ARG_MIX);
        StateView.loading(b.state, R.string.home_loading);
        vm.getSections().observe(getViewLifecycleOwner(), sections -> {
            if (sections == null || b.list.getAdapter() != null) return; // built once: the page stays stable
            show(sections, sectionId, mixId);
        });
    }

    private void show(List<HomeSection> sections, @Nullable String sectionId, @Nullable String mixId) {
        HomeSection section = null;
        HomeMix mix = null;
        for (HomeSection s : sections) {
            if (s.id().equals(sectionId)) section = s;
            for (HomeItem i : s.items()) {
                if (mixId != null && i.kind() == HomeItem.Kind.MIX && i.mix().id().equals(mixId)) mix = i.mix();
            }
        }
        if (section == null && mix == null) {
            StateView.empty(b.state, getString(R.string.library_empty), null, null, null);
            return;
        }
        StateView.hide(b.state);
        Header header = new Header();
        if (mix != null) {
            title = SectionText.mixTitle(requireContext(), mix);
            tracks = mix.tracks();
            header.set(title, SectionText.mixSubtitle(requireContext(), mix), mix.artworkUri(), mix.placeholderSeed(), true);
            showTracks(header);
            return;
        }
        title = SectionText.title(requireContext(), section);
        List<Track> list = new ArrayList<>();
        for (HomeItem i : section.items()) if (i.kind() == HomeItem.Kind.TRACK) list.add(i.track());
        if (!list.isEmpty()) {
            tracks = list;
            header.set(title, SectionText.subtitle(requireContext(), section), list.get(0).artworkUri(),
                    list.get(0).placeholderSeed(), true);
            showTracks(header);
            return;
        }
        header.set(title, SectionText.subtitle(requireContext(), section), null, section.id().hashCode(), false);
        int mode = section.style() == HomeSectionStyle.AVATARS ? HomeCardAdapter.AVATAR : HomeCardAdapter.CARD;
        float d = getResources().getDisplayMetrics().density;
        int span = Math.max(2, Math.round(getResources().getDisplayMetrics().widthPixels / d / 180f));
        if (mode == HomeCardAdapter.AVATAR) span = Math.max(3, Math.round(getResources().getDisplayMetrics().widthPixels / d / 120f));
        GridLayoutManager glm = new GridLayoutManager(requireContext(), span);
        int full = span;
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) { return position == 0 ? full : 1; }
        });
        b.list.setLayoutManager(glm);
        b.list.setPadding(Math.round(12 * d), b.list.getPaddingTop(), Math.round(12 * d), b.list.getPaddingBottom());
        HomeCardAdapter cards = new HomeCardAdapter(mode, true, this);
        b.list.setAdapter(new ConcatAdapter(header, cards));
        cards.submitList(section.items());
    }

    private void showTracks(Header header) {
        TrackAdapter adapter = new TrackAdapter(this);
        b.list.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(requireContext()));
        b.list.setAdapter(new ConcatAdapter(header, adapter));
        adapter.submitList(tracks);
    }

    @Override public void onTrackClick(List<Track> list, int position) {
        vm.play(list, position, title);
    }

    @Override public void onTrackMore(View anchor, Track track) {
        TrackMenu.show(anchor, track);
    }

    @Override public void onItemClick(HomeItem item) {
        switch (item.kind()) {
            case ALBUM -> Screens.openTrackList(this, ScreenNavigator.TrackList.ALBUM, item.album().id());
            case PLAYLIST -> Screens.openTrackList(this, ScreenNavigator.TrackList.PLAYLIST, item.playlist().id());
            case ARTIST -> Screens.openTrackList(this, ScreenNavigator.TrackList.ARTIST, item.artist().id());
            case MIX -> open(this, null, item.mix().id());
            case TRACK -> vm.play(List.of(item.track()), 0, title);
        }
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    /** The page header row (title, count, play / shuffle when it lists tracks). */
    private final class Header extends RecyclerView.Adapter<Header.Holder> {
        private String title = "";
        @Nullable private String subtitle;
        @Nullable private String art;
        private int seed;
        private boolean playable;

        void set(String title, @Nullable String subtitle, @Nullable String art, int seed, boolean playable) {
            this.title = title;
            this.subtitle = subtitle;
            this.art = art;
            this.seed = seed;
            this.playable = playable;
            notifyDataSetChanged();
        }

        @Override public int getItemCount() { return 1; }

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LibraryListHeaderBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override public void onBindViewHolder(@NonNull Holder h, int position) {
            ScriptFont.setText(h.b.title, title);
            String sub = subtitle;
            if (playable) {
                long total = 0;
                for (Track t : tracks) total += t.durationMs();
                String songs = getResources().getQuantityString(R.plurals.library_songs, tracks.size(), tracks.size())
                        + " · " + Format.duration(total, getResources().getConfiguration().getLocales().get(0));
                sub = sub == null ? songs : sub + " · " + songs;
            }
            h.b.subtitle.setVisibility(sub == null ? View.GONE : View.VISIBLE);
            if (sub != null) ScriptFont.setText(h.b.subtitle, sub);
            Art.bind(h.b.art, art, seed, 32, false);
            h.b.play.setVisibility(playable ? View.VISIBLE : View.GONE);
            h.b.shuffle.setVisibility(playable ? View.VISIBLE : View.GONE);
            h.b.play.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.nav_ic_play, 0, 0, 0);
            h.b.shuffle.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.nav_ic_shuffle, 0, 0, 0);
            h.b.play.setOnClickListener(v -> vm.play(tracks, 0, SectionPageFragment.this.title));
            h.b.shuffle.setOnClickListener(v -> vm.playShuffled(tracks, SectionPageFragment.this.title));
        }

        final class Holder extends RecyclerView.ViewHolder {
            final LibraryListHeaderBinding b;

            Holder(LibraryListHeaderBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }
    }
}
