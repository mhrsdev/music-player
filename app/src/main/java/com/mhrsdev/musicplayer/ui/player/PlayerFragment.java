package com.mhrsdev.musicplayer.ui.player;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import com.mhrsdev.musicplayer.MusicPlayerApp;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.databinding.PlayerFragmentBinding;
import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.domain.model.Lyrics;
import com.mhrsdev.musicplayer.domain.model.PlaybackState;
import com.mhrsdev.musicplayer.domain.model.RepeatMode;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.ui.nav.PlayerNavigator;
import com.mhrsdev.musicplayer.ui.player.lyrics.LyricsAdapter;
import com.mhrsdev.musicplayer.ui.player.lyrics.LyricsClock;
import com.mhrsdev.musicplayer.ui.player.lyrics.LyricsEffects;
import com.mhrsdev.musicplayer.ui.player.lyrics.LyricsOffsetStore;
import com.mhrsdev.musicplayer.ui.player.lyrics.LyricsTicker;
import com.mhrsdev.musicplayer.ui.player.lyrics.LyricsScrollPolicy;
import com.mhrsdev.musicplayer.ui.queue.QueueFragment;
import com.mhrsdev.musicplayer.ui.theme.MotionPolicy;
import com.mhrsdev.musicplayer.data.settings.SeekBarStyle;
import com.mhrsdev.musicplayer.ui.widget.BaseSeekView;
import com.mhrsdev.musicplayer.ui.widget.SeekBarView;
import com.mhrsdev.musicplayer.ui.widget.SeekStyles;

import java.util.Locale;

/**
 * Full player (design v1, 05/06/11/15): top row lyrics toggle · shuffle/repeat/queue pill group · heart; cover or
 * synced lyrics; title/artist; seek (any {@link SeekStyles} style from the "seek_bar_style" setting, swapped live); cookie
 * play/pause between rounded prev/next; output chip. All observation uses the view lifecycle, so position ticks and
 * animations stop when the player is not visible.
 */
public class PlayerFragment extends Fragment {

    private PlayerFragmentBinding b;
    private BaseSeekView seek;
    @Nullable private SeekBarStyle seekStyle;
    @Nullable private androidx.lifecycle.Observer<float[]> waveformObserver;
    private PlayerViewModel vm;
    private LyricsAdapter lyricsAdapter;
    private final LyricsScrollPolicy scrollPolicy = new LyricsScrollPolicy();
    private final LyricsClock lyricsClock = new LyricsClock();
    private LyricsTicker lyricsTicker;
    @Nullable private LyricsOffsetStore offsets;
    @Nullable private Lyrics shownLyrics;
    private PlayerPalette palette = PlayerPalette.forSeed(0);
    @Nullable private String paletteTrackId;
    private boolean scrubbing;
    private final Runnable resumeAutoScroll = this::onAutoScrollResume;
    @Nullable private OutputRoute outputRoute;
    private boolean light;
    private boolean btNameNeedsPermission;
    private static final String PREFS = "player_ui", K_BT_ASKED = "bt_name_asked";
    /** "Add lyrics file": the system document picker; the file is copied into app storage by the repository. */
    private final androidx.activity.result.ActivityResultLauncher<String[]> pickLyricsFile = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
            uri -> { if (uri != null && vm != null) vm.importLyricsFile(uri.toString()); });
    private final androidx.activity.result.ActivityResultLauncher<String> btPermission = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
            granted -> { if (outputRoute != null) outputRoute.refresh(); });

    public static PlayerFragment newInstance() { return new PlayerFragment(); }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = PlayerFragmentBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        AppContainer c = ((MusicPlayerApp) requireActivity().getApplication()).getContainer();
        vm = new ViewModelProvider(this, PlayerViewModel.factory(c.player, c.library, c.lyrics, c.waveform))
                .get(PlayerViewModel.class);

        int basePadLeft = b.playerContent.getPaddingLeft(), basePadRight = b.playerContent.getPaddingRight();
        ViewCompat.setOnApplyWindowInsetsListener(b.playerContent, (v, insets) -> {
            // Controls clear the bars and a side/top cutout (landscape punch hole); the backdrop stays full-bleed.
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(basePadLeft + bars.left, bars.top, basePadRight + bars.right, bars.bottom + dp(12));
            return insets;
        });

        installSeek(readSeekStyle(c));
        b.playerArtwork.setCornerRadiusDp(32);
        b.playerArtwork.setListener(this::onArtwork);
        b.playerStage.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> {
            int size = Math.min(r - l, bt - t) - dp(16);
            ViewGroup.LayoutParams lp = b.playerArtwork.getLayoutParams();
            if (size > 0 && lp.width != size) {
                lp.width = size;
                lp.height = size;
                v.post(() -> { if (b != null) b.playerArtwork.setLayoutParams(lp); });
            }
        });
        light = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                != android.content.res.Configuration.UI_MODE_NIGHT_YES;
        applyThemeColors();
        setupControls();
        setupLyrics();
        c.settings().performance().observe(getViewLifecycleOwner(), p -> applyPerformance());
        c.settings().lyricsTextSize().observe(getViewLifecycleOwner(),
                size -> lyricsAdapter.setTextSizeSp(LyricsEffects.textSp(size)));
        setupSwipeToCollapse();
        applyPalette(palette, null, false); // theme-correct accents before the first track arrives

        vm.getState().observe(getViewLifecycleOwner(), this::render);
        vm.getCurrentTrack().observe(getViewLifecycleOwner(), this::renderTrack);
        vm.getLiked().observe(getViewLifecycleOwner(), this::renderLiked);
        observeWaveformIfNeeded();
        // D25: switching the style in Settings (or the picker) while the player is open swaps the bar live.
        c.settings().seekBarStyle().observe(getViewLifecycleOwner(), st -> {
            if (st == null || st == seekStyle || b == null) return;
            installSeek(st);
            observeWaveformIfNeeded();
            applySeekColors();
            renderedOnce = false;
            render(vm.getState().getValue());
        });
        vm.getShowLyrics().observe(getViewLifecycleOwner(), this::renderStage);
        vm.getLyricsAvailability().observe(getViewLifecycleOwner(), this::renderLyricsButton);
        vm.getLyrics().observe(getViewLifecycleOwner(), this::renderLyrics);
        vm.getActiveLyricLine().observe(getViewLifecycleOwner(), this::renderActiveLine);

        if (savedInstanceState == null && MotionPolicy.transitionsEnabled(requireContext())) playEnterTransition();
        // Restored (rotation, process death): no enter fade runs, the player covers the shell from its first frame.
        if (savedInstanceState != null) view.post(() -> setCovering(true));
    }

    /**
     * The host's fade in/out (MainActivity's custom animations): the shell under the player stops drawing once the
     * fade-in ended and starts again the moment the fade-out begins (see {@code PlayerCover}). Same animator as the
     * default, only observed.
     */
    @Nullable @Override
    public android.animation.Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
        if (nextAnim == 0) return null;
        android.animation.Animator a;
        try {
            a = android.animation.AnimatorInflater.loadAnimator(requireContext(), nextAnim);
        } catch (RuntimeException e) {
            return null; // not an animator resource: let the fragment manager load it as before
        }
        if (enter) {
            a.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    setCovering(true);
                }
            });
        } else {
            setCovering(false);
        }
        return a;
    }

    /** Tells the host whether the opaque player fully hides the shell (only while this view exists and rests). */
    private void setCovering(boolean on) {
        Context ctx = getContext();
        if (ctx == null) return;
        if (on && (b == null || b.playerRoot.getTranslationY() != 0f || b.playerRoot.getAlpha() < 1f
                || isRemoving())) {
            return;
        }
        PlayerNavigator nav = PlayerNavigator.from(ctx);
        if (nav != null) nav.setPlayerCovering(on);
    }

    /**
     * Settings &gt; Performance changed (MotionPolicy already holds the new profile: the app-wide observer runs
     * first). Re-applies every effect of this screen at once, live; each one asks MotionPolicy, none reads settings.
     */
    private void applyPerformance() {
        if (b == null) return;
        applyLyricsEffects();
        gateLyricsTicker(vm.getLyrics().getValue());
        seek.refreshMotion();
        PlaybackState s = vm.getState().getValue();
        b.playerBackdrop.setAnimating(s != null && s.playing());
    }

    private void applyLyricsEffects() {
        Context ctx = requireContext();
        lyricsAdapter.setEffects(LyricsEffects.blurAllowed(ctx), MotionPolicy.decorativeMotionEnabled(ctx),
                MotionPolicy.lyricsWordsEnabled());
    }

    /** The 30 fps word-sweep loop runs only for shown, synced lyrics with the word sweep on. */
    private void gateLyricsTicker(@Nullable Lyrics l) {
        boolean showing = Boolean.TRUE.equals(vm.getShowLyrics().getValue());
        lyricsTicker.setEnabled(showing && l != null && l.synced() && !l.isEmpty() && MotionPolicy.lyricsWordsEnabled());
    }

    /** The "seek_bar_style" setting; Wavy when unset or unknown. */
    static SeekBarStyle readSeekStyle(AppContainer c) {
        SeekBarStyle s = c.settings().seekBarStyle().getValue();
        return s != null ? s : SeekBarStyle.WAVY;
    }

    /** Creates the bar for {@code style} (replacing the current one) and wires its seek callbacks. */
    private void installSeek(SeekBarStyle style) {
        seekStyle = style;
        b.playerSeekContainer.removeAllViews();
        seek = SeekStyles.create(requireContext(), style);
        b.playerSeekContainer.addView(seek, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        seek.setContentDescription(getString(R.string.seek_style_cd, getString(SeekStyles.nameRes(style))));
        scrubbing = false;
        seek.setOnSeekListener(new SeekBarView.OnSeekListener() {
            @Override public void onSeekStart(long pos) { scrubbing = true; }
            @Override public void onSeek(long pos) { renderTimes(pos, seek.getDuration()); }
            @Override public void onSeekEnd(long pos) {
                scrubbing = false;
                vm.seekTo(pos);
            }
        });
    }

    /** Styles that ignore the envelope don't make the repository extract it (CPU/battery). */
    private void observeWaveformIfNeeded() {
        boolean wants = seekStyle != null && SeekStyles.usesWaveform(seekStyle);
        if (wants && waveformObserver == null) {
            waveformObserver = w -> { if (seek != null) seek.setWaveform(w); };
            vm.getWaveform().observe(getViewLifecycleOwner(), waveformObserver);
        } else if (wants) {
            seek.setWaveform(vm.getWaveform().getValue());
        } else if (waveformObserver != null) {
            vm.getWaveform().removeObserver(waveformObserver);
            waveformObserver = null;
        }
    }

    /**
     * Shared-element-like entry from the mini-player, cheaply: the player cover starts exactly over the mini-player's
     * artwork (translation + scale of the same ArtworkView, no bitmap copy) and flies to its slot with one
     * ViewPropertyAnimator while the rest of the content rises and fades in.
     */
    private void playEnterTransition() {
        View content = b.playerContent;
        content.setAlpha(0f);
        content.setTranslationY(dp(72));
        content.animate().alpha(1f).translationY(0f).setDuration(380)
                .setInterpolator(new DecelerateInterpolator(2.2f)).start();
        View art = b.playerArtwork;
        art.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                art.getViewTreeObserver().removeOnPreDrawListener(this);
                if (b == null) return true;
                View mini = miniArtwork();
                int[] from = new int[2], to = new int[2];
                if (mini != null && mini.isShown() && mini.getWidth() > 0 && art.getWidth() > 0) {
                    mini.getLocationOnScreen(from);
                    art.getLocationOnScreen(to);
                    float scale = mini.getWidth() / (float) art.getWidth();
                    // content is translated by 72 dp at this point; undo it so the cover starts on the mini art
                    art.setPivotX(0f);
                    art.setPivotY(0f);
                    art.setScaleX(scale);
                    art.setScaleY(scale);
                    art.setTranslationX(from[0] - to[0]);
                    art.setTranslationY(from[1] - to[1]);
                    art.animate().scaleX(1f).scaleY(1f).translationX(0f).translationY(0f).setDuration(420)
                            .setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f))
                            .withEndAction(() -> { art.setPivotX(art.getWidth() / 2f); art.setPivotY(art.getHeight() / 2f); })
                            .start();
                } else {
                    art.setScaleX(0.86f);
                    art.setScaleY(0.86f);
                    art.animate().scaleX(1f).scaleY(1f).setDuration(450)
                            .setInterpolator(new DecelerateInterpolator(2f)).start();
                }
                return true;
            }
        });
    }

    @Nullable private View miniArtwork() {
        View mini = requireActivity().findViewById(R.id.mini);
        return mini instanceof com.mhrsdev.musicplayer.ui.widget.MiniPlayerView
                ? ((com.mhrsdev.musicplayer.ui.widget.MiniPlayerView) mini).getArtworkView() : null;
    }

    private void setupControls() {
        b.playerPlay.setOnLongClickListener(v -> { // long-press = stop (pause and back to the start)
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            vm.stop();
            android.widget.Toast.makeText(requireContext(), R.string.player_stopped, android.widget.Toast.LENGTH_SHORT).show();
            return true;
        });
        ViewCompat.replaceAccessibilityAction(b.playerPlay,
                androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK,
                getString(R.string.player_stop), null);
        b.playerPlay.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            vm.togglePlayPause();
        });
        b.playerNext.setOnClickListener(v -> vm.next());
        b.playerPrev.setOnClickListener(v -> vm.previous());
        b.playerShuffle.setOnClickListener(v -> vm.toggleShuffle());
        b.playerRepeat.setOnClickListener(v -> vm.cycleRepeat());
        b.playerLike.setOnClickListener(v -> {
            v.performHapticFeedback(android.os.Build.VERSION.SDK_INT >= 30
                    ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY);
            v.animate().scaleX(1.18f).scaleY(1.18f).setDuration(110)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(180).start()).start();
            vm.toggleLike();
        });
        b.playerLyricsToggle.setOnClickListener(v -> vm.toggleLyrics());
        b.playerMore.setOnClickListener(v -> {
            android.widget.PopupMenu menu = new android.widget.PopupMenu(requireContext(), v);
            boolean lyrics = Boolean.TRUE.equals(vm.getShowLyrics().getValue());
            LyricsAvailability av = vm.getLyricsAvailability().getValue();
            if (lyrics || av == LyricsAvailability.AVAILABLE) {
                menu.getMenu().add(0, 1, 0, lyrics ? R.string.player_show_cover : R.string.player_show_lyrics);
            } else if (av == LyricsAvailability.NONE) {
                menu.getMenu().add(0, 15, 0, R.string.lyrics_find); // empty state with Search again / file / manual
            }
            menu.getMenu().add(0, 2, 1, R.string.player_queue);
            menu.getMenu().add(0, 3, 2, R.string.player_sleep_timer);
            Track current = vm.getCurrentTrack().getValue();
            if (current != null) menu.getMenu().add(0, 4, 3, R.string.player_add_to_playlist);
            if (lyrics && shownLyrics != null && !shownLyrics.isEmpty()) {
                menu.getMenu().add(0, 11, 4, R.string.lyrics_text_larger);
                menu.getMenu().add(0, 12, 5, R.string.lyrics_text_smaller);
                if (shownLyrics.synced()) {
                    menu.getMenu().add(0, 13, 6, R.string.lyrics_earlier);
                    menu.getMenu().add(0, 14, 7, R.string.lyrics_later);
                }
            }
            if (current != null) menu.getMenu().add(0, 5, 4, R.string.player_share);
            if (soundFragmentClass() != null) menu.getMenu().add(0, 6, 5, R.string.player_sound);
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) vm.toggleLyrics();
                else if (item.getItemId() == 15) vm.openLyricsTools();
                else if (item.getItemId() == 11) stepLyricsSize(1);
                else if (item.getItemId() == 12) stepLyricsSize(-1);
                else if (item.getItemId() == 13) nudgeLyrics(-LyricsOffsetStore.STEP_MS);
                else if (item.getItemId() == 14) nudgeLyrics(LyricsOffsetStore.STEP_MS);
                else if (item.getItemId() == 4 && current != null)
                    com.mhrsdev.musicplayer.ui.playlist.AddToPlaylistSheet.show(getParentFragmentManager(), current);
                else if (item.getItemId() == 3) com.mhrsdev.musicplayer.ui.queue.SleepTimerSheet.show(getParentFragmentManager());
                else if (item.getItemId() == 5 && current != null) share(current);
                else if (item.getItemId() == 6) openSound();
                else b.playerQueue.performClick();
                return true;
            });
            menu.show();
        });
        b.playerQueue.setOnClickListener(v -> {
            PlayerNavigator nav = PlayerNavigator.from(requireContext());
            if (nav != null) nav.openQueue();
            else QueueFragment.show(getParentFragmentManager());
        });
        b.playerOutput.setOnClickListener(v -> { if (btNameNeedsPermission) explainBluetoothName(); });
    }

    private void setupLyrics() {
        offsets = new LyricsOffsetStore(requireContext());
        lyricsAdapter = new LyricsAdapter(line -> {
            scrollPolicy.resume();
            b.lyricsBackToCurrent.setVisibility(View.GONE);
            vm.seekTo(line.timeMs() + lyricsClock.offsetMs());
        }, lyricsClock);
        applyLyricsEffects();
        lyricsTicker = new LyricsTicker(b.lyricsList, lyricsClock, () -> renderActiveLine(null));
        b.lyricsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.lyricsList.setAdapter(lyricsAdapter);
        lyricsAdapter.setTextColor(light ? PlayerBackdropView.TEXT_ON_LIGHT : PlayerBackdropView.TEXT_ON_DARK);
        b.lyricsList.setItemAnimator(null);
        b.lyricsList.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> {
            int h = bt - t;
            if (h != ob - ot) v.post(() -> v.setPadding(0, (int) (h * LyricsEffects.ANCHOR), 0, (int) (h * 0.5f)));
        });
        b.lyricsList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    scrollPolicy.onUserScroll(SystemClock.uptimeMillis());
                    rv.removeCallbacks(resumeAutoScroll);
                    rv.postDelayed(resumeAutoScroll, LyricsScrollPolicy.PAUSE_MS);
                }
                if (newState != RecyclerView.SCROLL_STATE_SETTLING) updateBackChip(rv);
            }
        });
        b.lyricsBackToCurrent.setOnClickListener(v -> onAutoScrollResume());
        b.lyricsRetry.setOnClickListener(v -> searchAgain());
        b.lyricsSearchAgain.setOnClickListener(v -> searchAgain());
        b.lyricsAddFile.setOnClickListener(v -> {
            try {
                pickLyricsFile.launch(new String[]{"text/*", "application/xml", "application/ttml+xml",
                        "application/json", "application/octet-stream"});
            } catch (android.content.ActivityNotFoundException e) {
                android.widget.Toast.makeText(requireContext(), R.string.lyrics_add_file, android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        b.lyricsSearchManual.setOnClickListener(v -> {
            String[] q = vm.manualSearchPrefill();
            com.mhrsdev.musicplayer.ui.player.lyrics.LyricsSearchSheet.show(this, q[0], q[1]);
        });
    }

    private void searchAgain() {
        if (!vm.searchAgain()) {
            android.widget.Toast.makeText(requireContext(), R.string.lyrics_search_again_wait,
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    /** "Back to current line": only after a manual scroll, with 4+ lines, while the active line is off screen. */
    private void updateBackChip(RecyclerView rv) {
        if (b == null) return;
        LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
        boolean show = lm != null && !scrollPolicy.shouldAutoScroll(SystemClock.uptimeMillis())
                && LyricsScrollPolicy.showBackChip(lyricsAdapter.getItemCount(), lyricsAdapter.getActive(),
                        lm.findFirstCompletelyVisibleItemPosition(), lm.findLastCompletelyVisibleItemPosition());
        b.lyricsBackToCurrent.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /** Dark (05) or light (15) player: text/icon colors over the backdrop; the backdrop's guard keeps the contrast. */
    private void applyThemeColors() {
        int text = light ? PlayerBackdropView.TEXT_ON_LIGHT : PlayerBackdropView.TEXT_ON_DARK;
        int rgb = text & 0x00FFFFFF;
        // No root background: the backdrop below is opaque and full-bleed (a colour here was one more full-screen
        // fill under it on every drift / seek / lyrics frame).
        b.playerBackdrop.setLight(light);
        b.playerTitle.setTextColor(text);
        b.playerArtist.setTextColor(rgb | 0xB8000000);
        b.playerElapsed.setTextColor(rgb | 0xC7000000);
        b.playerRemaining.setTextColor(rgb | 0xC7000000);
        b.lyricsMessageTitle.setTextColor(text);
        b.lyricsMessageBody.setTextColor(rgb | 0xB8000000);
        b.lyricsBackToCurrent.setTextColor(text);
        b.lyricsRetry.setTextColor(text);
        b.lyricsSearchAgain.setTextColor(text);
        b.lyricsAddFile.setTextColor(text);
        b.lyricsSearchManual.setTextColor(text);
        b.playerOutput.setTextColor(text);
        androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(b.playerOutput, ColorStateList.valueOf(text));
        ColorStateList icon = ColorStateList.valueOf(text);
        for (ImageButton ib : new ImageButton[]{b.playerLyricsToggle, b.playerMore, b.playerPrev, b.playerNext}) {
            ib.setImageTintList(icon);
        }
        if (light) {
            b.playerHandle.getBackground().mutate().setTint(rgb | 0x52000000);
        }
    }

    @Override public void onStart() {
        super.onStart();
        // Output chip: an AudioDeviceCallback only while the player is visible (no background cost).
        outputRoute = new OutputRoute(requireContext());
        outputRoute.start(this::renderOutput);
    }

    @Override public void onStop() {
        if (b != null) b.playerBackdrop.setAnimating(false); // screen off / app in background: no drift
        if (lyricsTicker != null) lyricsTicker.stop();
        if (outputRoute != null) outputRoute.stop();
        outputRoute = null;
        super.onStop();
    }

    private void renderOutput(OutputRoute.Route r) {
        if (b == null) return;
        String label;
        int icon;
        switch (r.kind()) {
            case BLUETOOTH:
                label = r.name() != null ? r.name() : getString(R.string.player_output_bluetooth);
                icon = R.drawable.player_ic_bluetooth;
                break;
            case WIRED: label = getString(R.string.player_output_headphones); icon = R.drawable.player_ic_headphones; break;
            case USB: label = getString(R.string.player_output_usb); icon = R.drawable.player_ic_headphones; break;
            default: label = getString(R.string.player_output_device); icon = R.drawable.player_ic_speaker; break;
        }
        b.playerOutput.setText(label);
        b.playerOutput.setContentDescription(getString(R.string.player_output_cd, label));
        btNameNeedsPermission = r.nameNeedsPermission();
        b.playerOutput.setClickable(btNameNeedsPermission);
        if (btNameNeedsPermission) maybeOfferBluetoothName();
        b.playerOutput.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
    }

    private boolean isOnline() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                requireContext().getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        if (cm == null) return true;
        try {
            android.net.NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return nc != null && nc.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (SecurityException e) {
            return true;
        }
    }

    private void onAutoScrollResume() {
        if (b == null) return;
        scrollPolicy.resume();
        b.lyricsBackToCurrent.setVisibility(View.GONE);
        scrollToLine(lyricsAdapter.getActive(), true);
    }

    /** Swipe down on the handle/cover/background: the whole player tracks the finger; release past 22% or fling closes. */
    @SuppressWarnings("ClickableViewAccessibility")
    private void setupSwipeToCollapse() {
        final float[] downY = new float[1];
        final boolean[] tracking = new boolean[1];
        final android.view.VelocityTracker[] vt = new android.view.VelocityTracker[1];
        int slop = android.view.ViewConfiguration.get(requireContext()).getScaledTouchSlop();
        View.OnTouchListener l = (v, e) -> {
            View root = b.playerRoot;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY[0] = e.getRawY();
                    tracking[0] = false;
                    vt[0] = android.view.VelocityTracker.obtain();
                    vt[0].addMovement(e);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    if (vt[0] != null) vt[0].addMovement(e);
                    float dy = e.getRawY() - downY[0];
                    if (!tracking[0] && dy > slop) tracking[0] = true;
                    if (tracking[0]) {
                        setCovering(false); // the shell shows under the dragged player
                        float y = Math.max(0f, dy);
                        root.setTranslationY(y);
                        root.setAlpha(1f - 0.35f * Math.min(1f, y / root.getHeight()));
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    float vy = 0f;
                    if (vt[0] != null) {
                        vt[0].addMovement(e);
                        vt[0].computeCurrentVelocity(1000);
                        vy = vt[0].getYVelocity();
                        vt[0].recycle();
                        vt[0] = null;
                    }
                    if (!tracking[0]) {
                        if (e.getActionMasked() == MotionEvent.ACTION_UP && v != b.playerRoot && v != b.playerArtwork) v.performClick();
                        return true;
                    }
                    boolean close = root.getTranslationY() > root.getHeight() * 0.22f || vy > 1200;
                    if (close) {
                        boolean motion = PlayerPalette.motionAllowed(requireContext());
                        root.animate().translationY(root.getHeight()).alpha(0.6f).setDuration(motion ? 200 : 0)
                                .withEndAction(this::collapse).start();
                    } else {
                        root.animate().translationY(0f).alpha(1f).setDuration(220)
                                .setInterpolator(new DecelerateInterpolator(2f))
                                .withEndAction(() -> setCovering(true)).start();
                    }
                    return true;
                }
                default:
                    return false;
            }
        };
        android.view.GestureDetector cover = new android.view.GestureDetector(requireContext(), new CoverGestures());
        b.playerArtwork.setOnTouchListener((v, e) -> {
            cover.onTouchEvent(e);
            return l.onTouch(v, e); // vertical drag still collapses the player
        });
        b.playerHandle.setOnTouchListener(l);
        b.playerRoot.setOnTouchListener(l);
    }

    private void collapse() {
        // Runs at the end of the 200 ms close animation: Back / a tab tap may have removed the player meanwhile, and
        // requireContext() on a detached fragment throws (an app crash, not a no-op).
        if (!isAdded() || isRemoving()) return;
        PlayerNavigator nav = PlayerNavigator.from(requireContext());
        if (nav != null) nav.closeFullPlayer();
        else requireActivity().getOnBackPressedDispatcher().onBackPressed();
    }

    // ---------------------------------------------------------------- rendering

    /** Last values pushed to the views by {@link #render}: it runs at 4 Hz, so only changes may touch a view. */
    private boolean rPlaying, rBuffering, rSkips, rHasTrack, rShuffle;
    private RepeatMode rRepeat;
    private long rElapsedSec = -1, rDuration = -1;
    private boolean renderedOnce;

    private void render(@Nullable PlaybackState s) {
        if (s == null) s = PlaybackState.empty();
        boolean skips = !s.queue().isEmpty(); // D36: both skips whenever a queue exists; the controller handles the ends
        boolean hasTrack = s.currentTrack() != null;
        if (!renderedOnce || rPlaying != s.playing()) {
            b.playerPlay.setPlaying(s.playing(), true);
            b.playerPlay.setContentDescription(getString(s.playing() ? R.string.player_pause : R.string.player_play));
        }
        if (!renderedOnce || rBuffering != s.buffering()) b.playerPlay.setBuffering(s.buffering());
        seek.setPlaying(s.playing());
        seek.setDuration(s.durationMs());
        seek.setBuffered(s.durationMs());
        lyricsClock.update(s.positionMs(), s.playing());
        b.playerBackdrop.setAnimating(s.playing()); // drift only while this (visible) player plays
        if (lyricsTicker != null) {
            lyricsTicker.poke();
            if (!s.playing()) { renderActiveLine(null); lyricsTicker.invalidateActive(); } // exact paused frame
        }
        if (!scrubbing && !seek.isDragging()) {
            seek.setPosition(s.positionMs());
            long sec = s.positionMs() / 1000;
            if (sec != rElapsedSec || s.durationMs() != rDuration) { // the labels only change once a second
                rElapsedSec = sec;
                rDuration = s.durationMs();
                renderTimes(s.positionMs(), s.durationMs());
            }
        }
        updateTitleMarquee(s.playing());
        if (!renderedOnce || rSkips != skips) {
            for (View v : new View[]{b.playerPrev, b.playerNext}) {
                v.setEnabled(skips);
                v.setAlpha(skips ? 1f : 0.38f);
            }
        }
        if (!renderedOnce || rHasTrack != hasTrack) b.playerPlay.setEnabled(hasTrack);
        if (!renderedOnce || rShuffle != s.shuffleEnabled()) {
            setToggle(b.playerShuffle, s.shuffleEnabled());
            b.playerShuffle.setContentDescription(getString(s.shuffleEnabled()
                    ? R.string.player_shuffle_on : R.string.player_shuffle_off));
        }
        if (!renderedOnce || rRepeat != s.repeatMode()) {
            setToggle(b.playerRepeat, s.repeatMode() != RepeatMode.OFF);
            b.playerRepeat.setImageResource(s.repeatMode() == RepeatMode.ONE
                    ? R.drawable.player_ic_repeat_one : R.drawable.player_ic_repeat);
            b.playerRepeat.setContentDescription(getString(s.repeatMode() == RepeatMode.ONE ? R.string.player_repeat_one
                    : s.repeatMode() == RepeatMode.ALL ? R.string.player_repeat_all : R.string.player_repeat_off));
        }
        if (!renderedOnce) setToggle(b.playerQueue, false);
        renderedOnce = true;
        rPlaying = s.playing();
        rBuffering = s.buffering();
        rSkips = skips;
        rHasTrack = hasTrack;
        rShuffle = s.shuffleEnabled();
        rRepeat = s.repeatMode();
    }

    private void renderTimes(long pos, long dur) {
        Locale loc = getResources().getConfiguration().getLocales().get(0);
        b.playerElapsed.setText(PlayerFormat.time(pos, loc));
        b.playerRemaining.setText(PlayerFormat.remaining(pos, dur, loc));
    }

    private void setToggle(ImageButton btn, boolean on) {
        btn.setActivated(on);
        btn.setBackgroundTintList(on ? ColorStateList.valueOf(palette.primary) : null);
        btn.setImageTintList(ColorStateList.valueOf(on ? palette.onPrimary
                : light ? PlayerBackdropView.TEXT_ON_LIGHT : PlayerBackdropView.TEXT_ON_DARK));
    }

    private void renderTrack(@Nullable Track t) {
        if (t == null) {
            b.playerTitle.setText(R.string.player_nothing_playing);
            b.playerArtist.setText(null);
            return;
        }
        b.playerTitle.setText(t.title());
        b.playerArtist.setText(t.artistName());
        fitTitle();
        b.playerTitle.setContentDescription(null);
        b.playerTitleBlock.setContentDescription(getString(R.string.player_now_playing_cd, t.title(), t.artistName()));
        boolean changed = paletteTrackId != null && !paletteTrackId.equals(t.id());
        paletteTrackId = t.id();
        boolean artColors = new com.mhrsdev.musicplayer.ui.settings.PrefsSettingsStore(requireContext()).isArtworkColors();
        // "Colors from artwork" off -> lapis base scheme (DESIGN §2.2 off switch)
        applyPalette(artColors ? PlayerPalette.forSeed(t.placeholderSeed()) : PlayerPalette.fromColor(0xFF5F7FA6),
                null, changed);
        b.playerArtwork.bind(t); // may call onArtwork() with the real cover, which refines the palette
        if (changed && MotionPolicy.transitionsEnabled(requireContext())) {
            View art = b.playerArtwork;
            art.setAlpha(0.4f);
            art.setTranslationX(dp(24));
            art.animate().alpha(1f).translationX(0f).setDuration(320)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
        }
        render(vm.getState().getValue());
    }

    private void applySeekColors() {
        seek.setColors(light ? palette.glow : palette.primary, light ? 0x3D181C21 : 0x47FFFFFF);
    }

    private void applyPalette(PlayerPalette p, @Nullable android.graphics.Bitmap tinyArt, boolean animate) {
        palette = p;
        b.playerBackdrop.setContent(p, tinyArt, animate);
        b.playerPlay.setColors(light ? p.glow : p.primary, light ? 0xFFFFFFFF : p.onPrimary);
        // Light theme: the tone-40 role reads on the pale scrim; dark theme keeps the tone-80 primary.
        int accent = light ? p.glow : p.primary;
        applySeekColors();
        b.playerLike.setImageTintList(ColorStateList.valueOf(accent));
        renderedOnce = false; // the toggles' tint comes from the palette: repaint them once
        render(vm.getState().getValue());
    }

    /** Real artwork arrived (ArtworkView decodes off-thread, downsampled): derive colors + tiny blurred backdrop once. */
    private void onArtwork(@Nullable android.graphics.Bitmap cover) {
        if (b == null || cover == null) return;
        if (!new com.mhrsdev.musicplayer.ui.settings.PrefsSettingsStore(requireContext()).isArtworkColors()) return;
        android.graphics.Bitmap tiny = PlayerBackdropView.prepare(cover);
        if (tiny == null) return;
        int[] px = new int[tiny.getWidth() * tiny.getHeight()];
        tiny.getPixels(px, 0, tiny.getWidth(), 0, 0, tiny.getWidth(), tiny.getHeight());
        int seed = com.mhrsdev.musicplayer.ui.theme.ColorExtractor.extractSeed(px, px.length);
        applyPalette(PlayerPalette.fromColor(seed), tiny, true);
    }

    private void renderLiked(Boolean liked) {
        boolean on = Boolean.TRUE.equals(liked);
        boolean wasOn = Boolean.TRUE.equals(b.playerLike.getTag(R.id.mp_motion_state));
        b.playerLike.setTag(R.id.mp_motion_state, on);
        if (on && !wasOn && b.playerLike.isLaidOut()) com.mhrsdev.musicplayer.ui.motion.Motion.pop(b.playerLike);
        b.playerLike.setImageResource(on ? R.drawable.player_ic_favorite : R.drawable.player_ic_favorite_border);
        b.playerLike.setContentDescription(getString(on ? R.string.player_unlike : R.string.player_like));
    }

    private void renderStage(Boolean showLyrics) {
        boolean lyrics = Boolean.TRUE.equals(showLyrics);
        crossfade(b.playerArtwork, !lyrics);
        b.playerLyricsToggle.setContentDescription(getString(lyrics ? R.string.player_show_cover : R.string.player_show_lyrics));
        b.playerLyricsToggle.setActivated(lyrics);
        b.playerLyricsToggle.setAlpha(lyrics ? 1f : 0.82f);
        renderLyricsButton(vm.getLyricsAvailability().getValue());
        renderLyrics(vm.getLyrics().getValue());
    }

    /**
     * Lyrics button only when the track has lyrics. INVISIBLE (not GONE) so the centred pill group never jumps when
     * the next track's answer arrives; a short fade-in when it appears, nothing while checking.
     */
    private void renderLyricsButton(@Nullable LyricsAvailability a) {
        View v = b.playerLyricsToggle;
        boolean show = a == LyricsAvailability.AVAILABLE || Boolean.TRUE.equals(vm.getShowLyrics().getValue());
        if (show == (v.getVisibility() == View.VISIBLE)) return;
        v.animate().cancel();
        float target = Boolean.TRUE.equals(vm.getShowLyrics().getValue()) ? 1f : 0.82f;
        if (show) {
            v.setVisibility(View.VISIBLE);
            if (com.mhrsdev.musicplayer.ui.theme.MotionPolicy.decorativeMotionEnabled(requireContext())) {
                v.setAlpha(0f);
                v.animate().alpha(target).setDuration(180).start();
            } else {
                v.setAlpha(target);
            }
        } else {
            v.setVisibility(View.INVISIBLE);
        }
    }

    private void crossfade(View v, boolean show) {
        if (show == (v.getVisibility() == View.VISIBLE)) return;
        v.animate().cancel();
        if (show) {
            v.setAlpha(0f);
            v.setVisibility(View.VISIBLE);
            v.animate().alpha(1f).setDuration(200).start();
        } else {
            v.setVisibility(View.GONE);
        }
    }

    private void renderLyrics(@Nullable Lyrics l) {
        boolean showing = Boolean.TRUE.equals(vm.getShowLyrics().getValue());
        gateLyricsTicker(l);
        if (!showing) {
            b.lyricsList.setVisibility(View.GONE);
            b.lyricsMessage.setVisibility(View.GONE);
            b.lyricsBackToCurrent.setVisibility(View.GONE);
            return;
        }
        if (l == null || l.isEmpty()) {
            b.lyricsList.setVisibility(View.GONE);
            b.lyricsBackToCurrent.setVisibility(View.GONE);
            b.lyricsMessage.setVisibility(View.VISIBLE);
            renderLyricsMessage(LyricsEmptyState.of(l, isOnline()));
            return;
        }
        renderLyricsMessage(LyricsEmptyState.CONTENT);
        b.lyricsMessage.setVisibility(View.GONE);
        boolean appearing = b.lyricsList.getVisibility() != View.VISIBLE;
        if (appearing) crossfade(b.lyricsList, true);
        boolean fresh = b.lyricsList.getTag() != l;
        if (fresh) {
            b.lyricsList.setTag(l);
            shownLyrics = l;
            lyricsClock.setOffsetMs(offsets == null ? 0 : offsets.get(l.trackId()));
            lyricsAdapter.setLyrics(l);
            lyricsAdapter.setActive(activeLine());
            if (!l.synced()) b.lyricsList.scrollToPosition(0);
        }
        // Opening lyrics (or a new song's text) jumps straight to the current line instead of scrolling from the top.
        if ((appearing || fresh) && l.synced()) {
            b.lyricsList.post(() -> { if (b != null) scrollToLine(lyricsAdapter.getActive(), false); });
        }
    }

    /** Loading / not found / failed: title, hint and which action chips show (no fake one-line lyric). */
    private void renderLyricsMessage(LyricsEmptyState st) {
        b.lyricsRetry.setVisibility(st == LyricsEmptyState.FAILED || st == LyricsEmptyState.OFFLINE ? View.VISIBLE : View.GONE);
        b.lyricsSearchAgain.setVisibility(st == LyricsEmptyState.NOT_FOUND ? View.VISIBLE : View.GONE);
        boolean tools = st == LyricsEmptyState.NOT_FOUND || st == LyricsEmptyState.FAILED || st == LyricsEmptyState.OFFLINE;
        b.lyricsAddFile.setVisibility(tools ? View.VISIBLE : View.GONE);
        b.lyricsSearchManual.setVisibility(st == LyricsEmptyState.NOT_FOUND || st == LyricsEmptyState.FAILED
                ? View.VISIBLE : View.GONE);
        switch (st) {
            case LOADING -> { b.lyricsMessageTitle.setText(R.string.lyrics_loading); b.lyricsMessageBody.setText(null); }
            case OFFLINE -> {
                b.lyricsMessageTitle.setText(R.string.lyrics_offline);
                b.lyricsMessageBody.setText(R.string.lyrics_offline_hint);
            }
            case FAILED -> {
                b.lyricsMessageTitle.setText(R.string.lyrics_failed);
                b.lyricsMessageBody.setText(R.string.lyrics_failed_hint);
            }
            case NOT_FOUND -> {
                b.lyricsMessageTitle.setText(R.string.lyrics_not_found);
                b.lyricsMessageBody.setText(R.string.lyrics_not_found_hint);
            }
            default -> { }
        }
        b.lyricsMessageBody.setVisibility(st == LyricsEmptyState.LOADING ? View.GONE : View.VISIBLE);
    }

    /** Active line from the smooth clock (includes the per-track offset); the VM's 4 Hz index is only the trigger. */
    private int activeLine() {
        Lyrics l = shownLyrics;
        return l == null ? -1 : l.activeLineIndex(lyricsClock.positionMs());
    }

    private void renderActiveLine(@Nullable Integer ignored) {
        if (b == null || lyricsAdapter == null) return;
        int i = activeLine();
        if (i == lyricsAdapter.getActive()) return;
        lyricsAdapter.setActive(i);
        if (b.lyricsList.getVisibility() == View.VISIBLE && scrollPolicy.shouldAutoScroll(SystemClock.uptimeMillis())) {
            scrollToLine(i, true);
        }
    }

    /** Lyrics timing ±0.5 s for this track (remembered per track). */
    private void nudgeLyrics(long deltaMs) {
        Lyrics l = shownLyrics;
        if (l == null || offsets == null) return;
        long v = offsets.nudge(l.trackId(), deltaMs);
        lyricsClock.setOffsetMs(v);
        renderActiveLine(null);
        lyricsTicker.invalidateActive();
        android.widget.Toast.makeText(requireContext(), getString(R.string.lyrics_offset_now,
                LyricsOffsetStore.format(v, getResources().getConfiguration().getLocales().get(0))),
                android.widget.Toast.LENGTH_SHORT).show();
    }

    private void stepLyricsSize(int dir) {
        AppContainer c = ((MusicPlayerApp) requireActivity().getApplication()).getContainer();
        c.settings().setLyricsTextSize(LyricsEffects.step(c.settings().lyricsTextSize().getValue(), dir));
    }

    private void scrollToLine(int index, boolean smooth) {
        if (index < 0 || index >= lyricsAdapter.getItemCount()) return;
        RecyclerView.LayoutManager lm = b.lyricsList.getLayoutManager();
        if (lm == null) return;
        int anchor = (int) (b.lyricsList.getHeight() * LyricsEffects.ANCHOR);
        View target = lm.findViewByPosition(index);
        if (!smooth || target == null || !PlayerPalette.motionAllowed(requireContext())) {
            ((LinearLayoutManager) lm).scrollToPositionWithOffset(index, anchor - b.lyricsList.getPaddingTop());
            return;
        }
        // our spring curve (lyrics motion), one smooth scroll per line change
        int dy = target.getTop() - anchor;
        if (dy != 0) b.lyricsList.smoothScrollBy(0, dy, com.mhrsdev.musicplayer.ui.player.lyrics.LyricsMotion.SPRING, 620);
    }

    // ---------------------------------------------------------------- cover gestures, share, sound

    /** Tap = lyrics, double-tap left/right half = -10/+10 s (tape direction, also in RTL), horizontal fling = skip. */
    private class CoverGestures extends android.view.GestureDetector.SimpleOnGestureListener {
        @Override public boolean onDown(@NonNull MotionEvent e) { return true; }

        @Override public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
            vm.toggleLyrics();
            return true;
        }

        @Override public boolean onDoubleTap(@NonNull MotionEvent e) {
            boolean forward = e.getX() > b.playerArtwork.getWidth() / 2f;
            vm.seekBy(forward ? SEEK_STEP_MS : -SEEK_STEP_MS);
            b.playerArtwork.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            showSeekHint(forward);
            return true;
        }

        @Override public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float vx, float vy) {
            if (e1 == null || Math.abs(vx) < 2f * Math.abs(vy) || Math.abs(vx) < 600f) return false;
            if (Math.abs(e2.getRawX() - e1.getRawX()) < dp(48)) return false;
            b.playerArtwork.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (vx < 0) vm.next(); else vm.previous(); // tape direction: swipe left = next
            return true;
        }
    }

    static final long SEEK_STEP_MS = 10_000L;
    @Nullable private TextView seekHint;

    private void showSeekHint(boolean forward) {
        if (seekHint == null) {
            seekHint = new TextView(requireContext());
            seekHint.setBackgroundResource(R.drawable.player_chip_bg);
            seekHint.setTextColor(0xFFFFFFFF);
            seekHint.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
            seekHint.setTypeface(seekHint.getTypeface(), android.graphics.Typeface.BOLD);
            seekHint.setPadding(dp(16), dp(8), dp(16), dp(8));
            seekHint.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            b.playerStage.addView(seekHint, new android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        android.widget.FrameLayout.LayoutParams lp = (android.widget.FrameLayout.LayoutParams) seekHint.getLayoutParams();
        // absolute left/right: the seek direction is tape direction in every locale
        lp.gravity = android.view.Gravity.CENTER_VERTICAL | (forward ? android.view.Gravity.RIGHT : android.view.Gravity.LEFT);
        lp.leftMargin = lp.rightMargin = dp(28);
        seekHint.setLayoutParams(lp);
        seekHint.setText(forward ? R.string.player_hint_forward_10 : R.string.player_hint_back_10);
        seekHint.setTranslationZ(dp(24));
        seekHint.animate().cancel();
        seekHint.setAlpha(1f);
        seekHint.animate().alpha(0f).setStartDelay(450).setDuration(250).start();
    }

    private void share(Track t) {
        android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(android.content.Intent.EXTRA_TEXT, t.title() + " \u2014 " + t.artistName());
        startActivity(android.content.Intent.createChooser(send, getString(R.string.player_share_chooser)));
    }

    @Nullable static Class<?> soundFragmentClass() {
        try {
            return Class.forName("com.mhrsdev.musicplayer.ui.sound.SoundFragment");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /** Opens the Sound screen (equalizer, speed) by class name over everything; back closes it. */
    private void openSound() {
        Class<?> c = soundFragmentClass();
        if (c == null) return;
        try {
            Fragment f = (Fragment) c.getDeclaredConstructor().newInstance();
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .add(android.R.id.content, f, "sound").addToBackStack("sound").commit();
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            // no Sound screen in this build
        }
    }

    // ---------------------------------------------------------------- title fitting

    static final float TITLE_MAX_SP = 30f, TITLE_MIN_SP = 20f;

    /** Largest size in [min, max] (2 sp steps) at which {@code textWidthAt(size)} fits {@code avail}; min when none. */
    static float fitTextSize(float avail, float maxSp, float minSp, java.util.function.DoubleUnaryOperator widthAt) {
        for (float sp = maxSp; sp > minSp; sp -= 2f) if (widthAt.applyAsDouble(sp) <= avail) return sp;
        return minSp;
    }

    /** Once per bind: shrink long titles from 30 sp down to 20 sp, then ellipsize (no per-frame work). */
    private void fitTitle() {
        TextView t = b.playerTitle;
        if (t.getWidth() == 0) { // not laid out yet: once, on the next layout
            t.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override public void onLayoutChange(View v, int l, int tp, int r, int bt, int ol, int ot, int or, int ob) {
                    v.removeOnLayoutChangeListener(this);
                    if (b != null && v.getWidth() > 0) v.post(() -> { if (b != null) fitTitle(); });
                }
            });
            return;
        }
        CharSequence text = t.getText();
        float avail = t.getWidth() - t.getTotalPaddingLeft() - t.getTotalPaddingRight();
        android.text.TextPaint p = new android.text.TextPaint(t.getPaint());
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        float sp = fitTextSize(avail, TITLE_MAX_SP, TITLE_MIN_SP, size -> {
            p.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, (float) size, dm));
            return p.measureText(text, 0, text.length());
        });
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp);
        // An ellipsized RTL line inside an LTR layout (or the reverse) is clipped at its start when aligned to the
        // view start; such a line fills the width anyway, so align it to its own text start instead.
        p.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, sp, dm));
        alignOverflowing(t, p.measureText(text, 0, text.length()) > avail);
        CharSequence artist = b.playerArtist.getText();
        if (artist != null) alignOverflowing(b.playerArtist, b.playerArtist.getPaint().measureText(artist, 0, artist.length())
                > b.playerArtist.getWidth() - b.playerArtist.getTotalPaddingLeft() - b.playerArtist.getTotalPaddingRight());
        PlaybackState st = vm.getState().getValue();
        updateTitleMarquee(st != null && st.playing());
    }

    private static void alignOverflowing(TextView t, boolean overflows) {
        int want = overflows ? View.TEXT_ALIGNMENT_TEXT_START : View.TEXT_ALIGNMENT_VIEW_START;
        if (t.getTextAlignment() != want) t.setTextAlignment(want);
    }

    /** Ellipsis by default; one cheap marquee pass only while playing with motion allowed (not in battery saver). */
    private void updateTitleMarquee(boolean playing) {
        TextView t = b.playerTitle;
        boolean marquee = playing && PlayerPalette.motionAllowed(requireContext());
        android.text.TextUtils.TruncateAt want = marquee ? android.text.TextUtils.TruncateAt.MARQUEE
                : android.text.TextUtils.TruncateAt.END;
        if (t.getEllipsize() != want) t.setEllipsize(want);
        if (t.isSelected() != marquee) t.setSelected(marquee);
    }

    // ---------------------------------------------------------------- Bluetooth device name (API 31+)

    private android.content.SharedPreferences uiPrefs() {
        return requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** First Bluetooth output seen without the permission: one unobtrusive offer, never again automatically. */
    private void maybeOfferBluetoothName() {
        if (android.os.Build.VERSION.SDK_INT < 31 || uiPrefs().getBoolean(K_BT_ASKED, false)) return;
        uiPrefs().edit().putBoolean(K_BT_ASKED, true).apply();
        com.google.android.material.snackbar.Snackbar.make(b.playerRoot, R.string.player_bt_snack,
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .setAction(R.string.player_bt_allow, v -> btPermission.launch(android.Manifest.permission.BLUETOOTH_CONNECT))
                .show();
    }

    /** Tap on the generic "Bluetooth" chip: explain, then ask again (or open settings if the system no longer asks). */
    private void explainBluetoothName() {
        if (android.os.Build.VERSION.SDK_INT < 31) return;
        boolean systemWillAsk = !uiPrefs().getBoolean(K_BT_ASKED, false)
                || shouldShowRequestPermissionRationale(android.Manifest.permission.BLUETOOTH_CONNECT);
        uiPrefs().edit().putBoolean(K_BT_ASKED, true).apply();
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.player_bt_title)
                .setMessage(R.string.player_bt_rationale)
                .setNegativeButton(R.string.queue_cancel, null)
                .setPositiveButton(systemWillAsk ? R.string.player_bt_allow : R.string.player_bt_settings, (d, w) -> {
                    if (systemWillAsk) {
                        btPermission.launch(android.Manifest.permission.BLUETOOTH_CONNECT);
                    } else {
                        startActivity(new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.fromParts("package", requireContext().getPackageName(), null)));
                    }
                })
                .show();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override public void onDestroyView() {
        setCovering(false);
        b.lyricsList.removeCallbacks(resumeAutoScroll);
        if (lyricsTicker != null) lyricsTicker.stop();
        renderedOnce = false;
        rElapsedSec = -1;
        b = null;
        seek = null;
        seekStyle = null;
        waveformObserver = null;
        super.onDestroyView();
    }
}
