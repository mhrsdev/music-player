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
import com.mhrsdev.musicplayer.ui.player.lyrics.LyricsScrollPolicy;
import com.mhrsdev.musicplayer.ui.queue.QueueFragment;
import com.mhrsdev.musicplayer.ui.widget.BarsSeekView;
import com.mhrsdev.musicplayer.ui.widget.BaseSeekView;
import com.mhrsdev.musicplayer.ui.widget.SeekBarView;
import com.mhrsdev.musicplayer.ui.widget.WaveformSeekView;
import com.mhrsdev.musicplayer.ui.widget.WavySeekView;

import java.util.Locale;

/**
 * Full player (design v1, 05/06/11/15): top row lyrics toggle · shuffle/repeat/queue pill group · heart; cover or
 * synced lyrics; title/artist; seek (Wavy default / Waveform / Bars from the "seek_bar_style" setting); cookie
 * play/pause between rounded prev/next; output chip. All observation uses the view lifecycle, so position ticks and
 * animations stop when the player is not visible.
 */
public final class PlayerFragment extends Fragment {

    /** Seek-bar styles (D4): Wavy default, Waveform, Bars (voice-memo). */
    public enum SeekStyle { WAVY, WAVEFORM, BARS }

    private PlayerFragmentBinding b;
    private BaseSeekView seek;
    private PlayerViewModel vm;
    private LyricsAdapter lyricsAdapter;
    private final LyricsScrollPolicy scrollPolicy = new LyricsScrollPolicy();
    private PlayerPalette palette = PlayerPalette.forSeed(0);
    @Nullable private String paletteTrackId;
    private boolean scrubbing;
    private final Runnable resumeAutoScroll = this::onAutoScrollResume;
    @Nullable private OutputRoute outputRoute;
    private boolean light;

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

        ViewCompat.setOnApplyWindowInsetsListener(b.playerContent, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), bars.bottom + dp(12));
            return insets;
        });

        seek = createSeekView(readSeekStyle(requireContext()));
        b.playerSeekContainer.addView(seek, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        seek.setContentDescription(getString(R.string.player_seek));
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
        setupSwipeToCollapse();
        applyPalette(palette, null, false); // theme-correct accents before the first track arrives

        vm.getState().observe(getViewLifecycleOwner(), this::render);
        vm.getCurrentTrack().observe(getViewLifecycleOwner(), this::renderTrack);
        vm.getLiked().observe(getViewLifecycleOwner(), this::renderLiked);
        // Wavy ignores the envelope: don't extract it (CPU/battery) unless the chosen style draws it.
        if (!(seek instanceof WavySeekView)) vm.getWaveform().observe(getViewLifecycleOwner(), w -> seek.setWaveform(w));
        vm.getShowLyrics().observe(getViewLifecycleOwner(), this::renderStage);
        vm.getLyrics().observe(getViewLifecycleOwner(), this::renderLyrics);
        vm.getActiveLyricLine().observe(getViewLifecycleOwner(), this::renderActiveLine);

        if (savedInstanceState == null && PlayerPalette.motionAllowed(requireContext())) playEnterTransition();
    }

    /** Reads the Settings screen's choice (T3's SettingsStore, file "settings"); Wavy when unset or unknown. */
    static SeekStyle readSeekStyle(Context ctx) {
        try {
            return SeekStyle.valueOf(new com.mhrsdev.musicplayer.ui.settings.PrefsSettingsStore(ctx).getSeekStyle().name());
        } catch (RuntimeException e) {
            return SeekStyle.WAVY;
        }
    }

    private BaseSeekView createSeekView(SeekStyle style) {
        Context c = requireContext();
        switch (style) {
            case WAVEFORM: return new WaveformSeekView(c);
            case BARS: return new BarsSeekView(c);
            default: return new WavySeekView(c);
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
            menu.getMenu().add(0, 1, 0, lyrics ? R.string.player_show_cover : R.string.player_show_lyrics);
            menu.getMenu().add(0, 2, 1, R.string.player_queue);
            menu.getMenu().add(0, 3, 2, R.string.player_sleep_timer);
            Track current = vm.getCurrentTrack().getValue();
            if (current != null) menu.getMenu().add(0, 4, 3, R.string.player_add_to_playlist);
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) vm.toggleLyrics();
                else if (item.getItemId() == 4 && current != null)
                    com.mhrsdev.musicplayer.ui.playlist.AddToPlaylistSheet.show(getParentFragmentManager(), current);
                else b.playerQueue.performClick(); // sleep timer lives in the queue sheet (design v1, 07)
                return true;
            });
            menu.show();
        });
        b.playerQueue.setOnClickListener(v -> {
            PlayerNavigator nav = PlayerNavigator.from(requireContext());
            if (nav != null) nav.openQueue();
            else QueueFragment.show(getParentFragmentManager());
        });
        seek.setOnSeekListener(new SeekBarView.OnSeekListener() {
            @Override public void onSeekStart(long pos) { scrubbing = true; }
            @Override public void onSeek(long pos) { renderTimes(pos, seek.getDuration()); }
            @Override public void onSeekEnd(long pos) {
                scrubbing = false;
                vm.seekTo(pos);
            }
        });
    }

    private void setupLyrics() {
        lyricsAdapter = new LyricsAdapter(line -> {
            scrollPolicy.resume();
            b.lyricsBackToCurrent.setVisibility(View.GONE);
            vm.seekTo(line.timeMs());
        });
        b.lyricsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.lyricsList.setAdapter(lyricsAdapter);
        lyricsAdapter.setTextColor(light ? PlayerBackdropView.TEXT_ON_LIGHT : PlayerBackdropView.TEXT_ON_DARK);
        b.lyricsList.setItemAnimator(null);
        b.lyricsList.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> {
            int h = bt - t;
            if (h != ob - ot) v.post(() -> v.setPadding(0, (int) (h * 0.4f), 0, (int) (h * 0.5f)));
        });
        b.lyricsList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    scrollPolicy.onUserScroll(SystemClock.uptimeMillis());
                    b.lyricsBackToCurrent.setVisibility(View.VISIBLE);
                    rv.removeCallbacks(resumeAutoScroll);
                    rv.postDelayed(resumeAutoScroll, LyricsScrollPolicy.PAUSE_MS);
                }
            }
        });
        b.lyricsBackToCurrent.setOnClickListener(v -> onAutoScrollResume());
        b.lyricsRetry.setOnClickListener(v -> {
            b.lyricsRetry.setVisibility(View.GONE);
            b.lyricsMessageTitle.setText(R.string.lyrics_loading);
            b.lyricsMessageBody.setText(null);
            vm.retryLyrics();
        });
    }

    /** Dark (05) or light (15) player: text/icon colors over the backdrop; the backdrop's guard keeps the contrast. */
    private void applyThemeColors() {
        int text = light ? PlayerBackdropView.TEXT_ON_LIGHT : PlayerBackdropView.TEXT_ON_DARK;
        int rgb = text & 0x00FFFFFF;
        b.playerRoot.setBackgroundColor(light ? 0xFFF7F9FF : 0xFF0F1216);
        b.playerBackdrop.setLight(light);
        b.playerTitle.setTextColor(text);
        b.playerArtist.setTextColor(rgb | 0xB8000000);
        b.playerElapsed.setTextColor(rgb | 0xC7000000);
        b.playerRemaining.setTextColor(rgb | 0xC7000000);
        b.lyricsMessageTitle.setTextColor(text);
        b.lyricsMessageBody.setTextColor(rgb | 0xB8000000);
        b.lyricsBackToCurrent.setTextColor(text);
        b.lyricsRetry.setTextColor(text);
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
                        if (e.getActionMasked() == MotionEvent.ACTION_UP && v != b.playerRoot) v.performClick();
                        return true;
                    }
                    boolean close = root.getTranslationY() > root.getHeight() * 0.22f || vy > 1200;
                    if (close) {
                        boolean motion = PlayerPalette.motionAllowed(requireContext());
                        root.animate().translationY(root.getHeight()).alpha(0.6f).setDuration(motion ? 200 : 0)
                                .withEndAction(this::collapse).start();
                    } else {
                        root.animate().translationY(0f).alpha(1f).setDuration(220)
                                .setInterpolator(new DecelerateInterpolator(2f)).start();
                    }
                    return true;
                }
                default:
                    return false;
            }
        };
        b.playerArtwork.setOnTouchListener(l);
        b.playerHandle.setOnTouchListener(l);
        b.playerRoot.setOnTouchListener(l);
    }

    private void collapse() {
        PlayerNavigator nav = PlayerNavigator.from(requireContext());
        if (nav != null) nav.closeFullPlayer();
        else requireActivity().getOnBackPressedDispatcher().onBackPressed();
    }

    // ---------------------------------------------------------------- rendering

    private void render(@Nullable PlaybackState s) {
        if (s == null) s = PlaybackState.empty();
        b.playerPlay.setPlaying(s.playing(), true);
        b.playerPlay.setBuffering(s.buffering());
        b.playerPlay.setContentDescription(getString(s.playing() ? R.string.player_pause : R.string.player_play));
        seek.setPlaying(s.playing());
        seek.setDuration(s.durationMs());
        seek.setBuffered(s.durationMs());
        if (!scrubbing && !seek.isDragging()) {
            seek.setPosition(s.positionMs());
            renderTimes(s.positionMs(), s.durationMs());
        }
        b.playerNext.setEnabled(s.hasNext() || s.repeatMode() == RepeatMode.ALL);
        b.playerNext.setAlpha(b.playerNext.isEnabled() ? 1f : 0.38f);
        b.playerPlay.setEnabled(s.currentTrack() != null);

        setToggle(b.playerShuffle, s.shuffleEnabled());
        b.playerShuffle.setContentDescription(getString(s.shuffleEnabled()
                ? R.string.player_shuffle_on : R.string.player_shuffle_off));
        setToggle(b.playerRepeat, s.repeatMode() != RepeatMode.OFF);
        b.playerRepeat.setImageResource(s.repeatMode() == RepeatMode.ONE
                ? R.drawable.player_ic_repeat_one : R.drawable.player_ic_repeat);
        b.playerRepeat.setContentDescription(getString(s.repeatMode() == RepeatMode.ONE ? R.string.player_repeat_one
                : s.repeatMode() == RepeatMode.ALL ? R.string.player_repeat_all : R.string.player_repeat_off));
        setToggle(b.playerQueue, false);
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
        b.playerTitle.setSelected(true); // one marquee pass for long titles
        b.playerArtist.setText(t.artistName());
        boolean changed = paletteTrackId != null && !paletteTrackId.equals(t.id());
        paletteTrackId = t.id();
        boolean artColors = new com.mhrsdev.musicplayer.ui.settings.PrefsSettingsStore(requireContext()).isArtworkColors();
        // "Colors from artwork" off -> lapis base scheme (DESIGN §2.2 off switch)
        applyPalette(artColors ? PlayerPalette.forSeed(t.placeholderSeed()) : PlayerPalette.fromColor(0xFF5F7FA6),
                null, changed);
        b.playerArtwork.bind(t); // may call onArtwork() with the real cover, which refines the palette
        if (changed && PlayerPalette.motionAllowed(requireContext())) {
            View art = b.playerArtwork;
            art.setAlpha(0.4f);
            art.setTranslationX(dp(24));
            art.animate().alpha(1f).translationX(0f).setDuration(320)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
        }
        render(vm.getState().getValue());
    }

    private void applyPalette(PlayerPalette p, @Nullable android.graphics.Bitmap tinyArt, boolean animate) {
        palette = p;
        b.playerBackdrop.setContent(p, tinyArt, animate);
        b.playerPlay.setColors(light ? p.glow : p.primary, light ? 0xFFFFFFFF : p.onPrimary);
        // Light theme: the tone-40 role reads on the pale scrim; dark theme keeps the tone-80 primary.
        int accent = light ? p.glow : p.primary;
        seek.setColors(accent, light ? 0x3D181C21 : 0x47FFFFFF);
        b.playerLike.setImageTintList(ColorStateList.valueOf(accent));
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
        b.playerLike.setImageResource(on ? R.drawable.player_ic_favorite : R.drawable.player_ic_favorite_border);
        b.playerLike.setContentDescription(getString(on ? R.string.player_unlike : R.string.player_like));
    }

    private void renderStage(Boolean showLyrics) {
        boolean lyrics = Boolean.TRUE.equals(showLyrics);
        crossfade(b.playerArtwork, !lyrics);
        b.playerLyricsToggle.setContentDescription(getString(lyrics ? R.string.player_show_cover : R.string.player_show_lyrics));
        b.playerLyricsToggle.setActivated(lyrics);
        b.playerLyricsToggle.setAlpha(lyrics ? 1f : 0.82f);
        renderLyrics(vm.getLyrics().getValue());
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
        if (!showing) {
            b.lyricsList.setVisibility(View.GONE);
            b.lyricsMessage.setVisibility(View.GONE);
            b.lyricsBackToCurrent.setVisibility(View.GONE);
            return;
        }
        if (l == null || l.isEmpty()) {
            b.lyricsList.setVisibility(View.GONE);
            b.lyricsMessage.setVisibility(View.VISIBLE);
            boolean offline = PlayerViewModel.showOfflineRetry(l, isOnline());
            b.lyricsRetry.setVisibility(offline ? View.VISIBLE : View.GONE);
            b.lyricsMessageTitle.setText(l == null ? R.string.lyrics_loading
                    : offline ? R.string.lyrics_offline : R.string.lyrics_none);
            b.lyricsMessageBody.setText(l == null ? null
                    : getString(offline ? R.string.lyrics_offline_hint : R.string.lyrics_none_hint));
            return;
        }
        b.lyricsRetry.setVisibility(View.GONE);
        b.lyricsMessage.setVisibility(View.GONE);
        if (b.lyricsList.getVisibility() != View.VISIBLE) crossfade(b.lyricsList, true);
        if (b.lyricsList.getTag() != l) {
            b.lyricsList.setTag(l);
            lyricsAdapter.setLyrics(l);
            Integer active = vm.getActiveLyricLine().getValue();
            renderActiveLine(active);
            if (!l.synced()) b.lyricsList.scrollToPosition(0);
        }
    }

    private void renderActiveLine(@Nullable Integer index) {
        int i = index == null ? -1 : index;
        lyricsAdapter.setActive(i);
        if (b.lyricsList.getVisibility() == View.VISIBLE && scrollPolicy.shouldAutoScroll(SystemClock.uptimeMillis())) {
            scrollToLine(i, true);
        }
    }

    private void scrollToLine(int index, boolean smooth) {
        if (index < 0 || index >= lyricsAdapter.getItemCount()) return;
        RecyclerView.LayoutManager lm = b.lyricsList.getLayoutManager();
        if (lm == null) return;
        if (!smooth || !PlayerPalette.motionAllowed(requireContext())) {
            ((LinearLayoutManager) lm).scrollToPositionWithOffset(index, (int) (b.lyricsList.getHeight() * 0.4f)
                    - b.lyricsList.getPaddingTop());
            return;
        }
        LinearSmoothScroller s = new LinearSmoothScroller(requireContext()) {
            @Override public int calculateDtToFit(int vs, int ve, int bs, int be, int pref) {
                return (bs + (int) ((be - bs) * 0.4f)) - (vs + (ve - vs) / 2);
            }
            @Override protected float calculateSpeedPerPixel(DisplayMetrics dm) { return 90f / dm.densityDpi; }
        };
        s.setTargetPosition(index);
        lm.startSmoothScroll(s);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override public void onDestroyView() {
        b.lyricsList.removeCallbacks(resumeAutoScroll);
        b = null;
        seek = null;
        super.onDestroyView();
    }
}
