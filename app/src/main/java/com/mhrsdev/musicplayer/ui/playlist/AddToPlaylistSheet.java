package com.mhrsdev.musicplayer.ui.playlist;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.color.MaterialColors;
import com.mhrsdev.musicplayer.MusicPlayerApp;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.domain.model.Playlist;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.domain.repository.LibraryRepository;
import com.mhrsdev.musicplayer.ui.widget.ArtworkView;

import java.util.List;

/**
 * "Add to playlist" sheet: lists the user's playlists (LibraryRepository), creates a new one inline, adds the track.
 * Callable from any screen via {@link #show(FragmentManager, Track)}.
 */
public final class AddToPlaylistSheet extends BottomSheetDialogFragment {
    public static final String TAG = "add_to_playlist";
    private static final String ARG_TRACK = "track_id", ARG_TITLE = "track_title";

    private LinearLayout list;
    private LibraryRepository library;

    public static void show(@NonNull FragmentManager fm, @NonNull Track track) {
        if (fm.findFragmentByTag(TAG) != null) return;
        AddToPlaylistSheet s = new AddToPlaylistSheet();
        Bundle a = new Bundle();
        a.putString(ARG_TRACK, track.id());
        a.putString(ARG_TITLE, track.title());
        s.setArguments(a);
        s.show(fm, TAG);
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        Context c = requireContext();
        library = ((MusicPlayerApp) requireActivity().getApplication()).getContainer().library;
        int onSurface = MaterialColors.getColor(c, com.google.android.material.R.attr.colorOnSurface, 0xFF000000);
        int onVariant = MaterialColors.getColor(c, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF444444);

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(24));

        TextView title = text(c, getString(R.string.playlist_add_title), 22, onSurface);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        androidx.core.view.ViewCompat.setAccessibilityHeading(title, true);
        root.addView(title);
        String trackTitle = requireArguments().getString(ARG_TITLE);
        if (!TextUtils.isEmpty(trackTitle)) {
            TextView sub = text(c, trackTitle, 14, onVariant);
            sub.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
            root.addView(sub);
        }

        // Inline "New playlist": name field + Create (IME done also creates).
        LinearLayout create = new LinearLayout(c);
        create.setOrientation(LinearLayout.HORIZONTAL);
        create.setGravity(Gravity.CENTER_VERTICAL);
        create.setPadding(0, dp(16), 0, dp(8));
        EditText name = new EditText(c);
        name.setHint(R.string.playlist_new_hint);
        name.setSingleLine(true);
        name.setImeOptions(EditorInfo.IME_ACTION_DONE);
        name.setTextColor(onSurface);
        name.setHintTextColor(onVariant);
        name.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        create.addView(name, new LinearLayout.LayoutParams(0, dp(56), 1f));
        Button ok = new Button(c, null, 0, com.google.android.material.R.style.Widget_Material3_Button_TonalButton);
        ok.setText(R.string.playlist_create);
        ok.setMinHeight(dp(48));
        create.addView(ok, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        Runnable doCreate = () -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) { name.setError(getString(R.string.playlist_name_required)); return; }
            library.createPlaylist(n, p -> { if (p != null) addAndClose(p); });
        };
        ok.setOnClickListener(v -> doCreate.run());
        name.setOnEditorActionListener((v, id, e) -> { doCreate.run(); return true; });
        root.addView(create);

        NestedScrollView scroll = new NestedScrollView(c);
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        library.getPlaylists().observe(getViewLifecycleOwner(), this::renderPlaylists);
    }

    private void renderPlaylists(@Nullable List<Playlist> playlists) {
        Context c = requireContext();
        list.removeAllViews();
        int onSurface = MaterialColors.getColor(c, com.google.android.material.R.attr.colorOnSurface, 0xFF000000);
        int onVariant = MaterialColors.getColor(c, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF444444);
        if (playlists == null || playlists.isEmpty()) {
            list.addView(text(c, getString(R.string.playlist_none), 15, onVariant));
            return;
        }
        TypedValue ripple = new TypedValue();
        c.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        for (Playlist p : playlists) {
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(64));
            row.setBackgroundResource(ripple.resourceId);
            ArtworkView art = new ArtworkView(c, null);
            art.setCornerRadiusDp(12);
            art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            art.setArtwork(p.artworkUris().isEmpty() ? null : p.artworkUris().get(0), p.placeholderSeed());
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(48), dp(48));
            alp.setMarginEnd(dp(16));
            row.addView(art, alp);
            LinearLayout texts = new LinearLayout(c);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView n = text(c, p.name(), 16, onSurface);
            n.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
            texts.addView(n);
            texts.addView(text(c, getResources().getQuantityString(R.plurals.playlist_track_count,
                    p.trackCount(), p.trackCount()), 13, onVariant));
            row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.setOnClickListener(v -> addAndClose(p));
            row.setContentDescription(getString(R.string.playlist_add_to, p.name()));
            list.addView(row);
        }
    }

    private void addAndClose(Playlist p) {
        String trackId = requireArguments().getString(ARG_TRACK);
        if (trackId == null) return;
        library.addToPlaylist(p.id(), trackId);
        Context c = getContext();
        if (c != null) Toast.makeText(c, getString(R.string.playlist_added, p.name()), Toast.LENGTH_SHORT).show();
        dismissAllowingStateLoss();
    }

    private TextView text(Context c, CharSequence s, int sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        return t;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
