package com.mhrsdev.musicplayer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import com.mhrsdev.musicplayer.databinding.ActivityMainBinding;
import com.mhrsdev.musicplayer.domain.model.PlaybackState;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.playback.PlaybackService;
import com.mhrsdev.musicplayer.ui.home.HomeFragment;
import com.mhrsdev.musicplayer.ui.library.CollectionFragment;
import com.mhrsdev.musicplayer.ui.library.LibraryFragment;
import com.mhrsdev.musicplayer.ui.library.TrackListFragment;
import com.mhrsdev.musicplayer.ui.nav.AudioPermission;
import com.mhrsdev.musicplayer.ui.nav.BottomChrome;
import com.mhrsdev.musicplayer.ui.nav.HomeGlow;
import com.mhrsdev.musicplayer.ui.nav.PermissionFragment;
import com.mhrsdev.musicplayer.ui.nav.PlayerNavigator;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator;
import com.mhrsdev.musicplayer.ui.nav.ScrollToTop;
import com.mhrsdev.musicplayer.ui.nav.ShellViewModel;
import com.mhrsdev.musicplayer.ui.nav.ShortcutIntents;
import com.mhrsdev.musicplayer.ui.search.SearchFragment;
import com.mhrsdev.musicplayer.ui.settings.PrefsSettingsStore;
import com.mhrsdev.musicplayer.ui.settings.SettingsFragment;
import com.mhrsdev.musicplayer.ui.settings.SettingsStore;
import com.mhrsdev.musicplayer.ui.settings.ThemeApplier;
import com.mhrsdev.musicplayer.ui.theme.ThemeController;
import com.mhrsdev.musicplayer.ui.widget.FloatingNavView;
import com.mhrsdev.musicplayer.ui.widget.MiniPlayerView;

/**
 * Single-activity shell: top-level tabs in {@code screen_container}, detail screens pushed on the back stack,
 * the mini-player and floating nav at the bottom, and the full player (T4) layered on top in {@code player_container}.
 */
public class MainActivity extends AppCompatActivity
        implements PlayerNavigator, ScreenNavigator, PermissionFragment.Host {

    /** T4's screens, resolved by name so the shell compiles before (and independently of) them. */
    private static final String PLAYER_FRAGMENT = "com.mhrsdev.musicplayer.ui.player.PlayerFragment";
    private static final String QUEUE_FRAGMENT = "com.mhrsdev.musicplayer.ui.queue.QueueFragment";
    private static final String PLAYER_TAG = "player";
    private static final String QUEUE_TAG = "queue";
    private static final String STATE_PERMISSION = "permission_flow";
    private static final String STATE_TAB = "tab";

    private ActivityMainBinding binding;
    private ShellViewModel shell;
    private boolean permissionFlow;
    private OnBackPressedCallback backToHome;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        SettingsStore.Theme theme = new PrefsSettingsStore(this).getTheme();
        ThemeApplier.applyNightMode(theme);
        setTheme(ThemeApplier.themeRes(theme));
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarAppearance();
        applyInsets();

        shell = new ViewModelProvider(this,
                ShellViewModel.factory(((MusicPlayerApp) getApplication()).getContainer().player))
                .get(ShellViewModel.class);

        layoutDockForWidth();
        bindNav();
        bindMiniPlayer();

        backToHome = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() {
                openTab(Tab.HOME);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backToHome);
        getSupportFragmentManager().addOnBackStackChangedListener(this::updateBackToHome);

        if (savedInstanceState == null) {
            if (AudioPermission.granted(this) || PermissionPrefs.skipped(this)) {
                showTab(Tab.HOME);
                runShortcut(getIntent());
            } else {
                permissionFlow = true;
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.screen_container, new PermissionFragment()).commit();
            }
        } else {
            permissionFlow = savedInstanceState.getBoolean(STATE_PERMISSION);
            // The restored fragment survives process death, the ViewModel's tab does not.
            shell.selectTab(Tab.values()[savedInstanceState.getInt(STATE_TAB, 0)]);
        }
        binding.chrome.setVisibility(permissionFlow ? View.GONE : View.VISIBLE);
        binding.bottomScrim.setVisibility(permissionFlow ? View.GONE : View.VISIBLE);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!permissionFlow) runShortcut(intent);
    }

    /** Launcher shortcuts (Shuffle all / Liked songs / Search). */
    private void runShortcut(Intent intent) {
        ShortcutIntents.handle(this, intent, ((MusicPlayerApp) getApplication()).getContainer(), this);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean(STATE_PERMISSION, permissionFlow);
        out.putInt(STATE_TAB, shell.getTab().getValue().ordinal());
    }

    /**
     * Edge-to-edge: content (Home's glow) draws under transparent bars; icons follow the APP theme (which may differ
     * from the system one), so they stay readable on light and dark backgrounds.
     */
    private void applySystemBarAppearance() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        boolean night = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        androidx.core.view.WindowInsetsControllerCompat c =
                androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.setAppearanceLightStatusBars(!night);
        c.setAppearanceLightNavigationBars(!night);
    }

    private int insetBottom;
    private int dockPx = -1;

    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            binding.screenContainer.setPadding(bars.left, bars.top, bars.right, 0);
            insetBottom = bars.bottom;
            sizeGlow(bars.top);
            int gap = getResources().getDimensionPixelSize(R.dimen.mp_dock_margin_b);
            binding.chrome.setPadding(bars.left, 0, bars.right, bars.bottom + gap);
            updateDock();
            return insets;
        });
        sizeGlow(0);
        binding.bottomScrim.setBackground(new BottomChrome.Scrim(
                com.mhrsdev.musicplayer.ui.theme.Tokens.color(this, R.attr.mpBackground),
                getResources().getDimensionPixelSize(R.dimen.mp_dock_scrim_fade)));
        binding.chrome.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t != ob - ot) v.post(this::updateDock);
        });
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
            @Override public void onFragmentViewCreated(FragmentManager fm, Fragment f, View view, Bundle state) {
                if (dockPx >= 0) BottomChrome.apply(view, dockPx);
            }
        }, true);
    }

    /** Glow height: status-bar inset + ~45% of the window; its gradient is fully transparent at the end. */
    private void sizeGlow(int insetTop) {
        int h = insetTop + Math.round(getResources().getDisplayMetrics().heightPixels * 0.45f);
        android.view.ViewGroup.LayoutParams lp = binding.homeGlow.getLayoutParams();
        if (lp.height != h) {
            lp.height = h;
            binding.homeGlow.setLayoutParams(lp);
        }
        if (binding.homeGlow.getBackground() == null) binding.homeGlow.setBackground(HomeGlow.create(this));
    }

    /** Recomputes the space the dock takes and hands it to the scrim and every scrolling screen. */
    private void updateDock() {
        boolean shown = binding.chrome.getVisibility() == View.VISIBLE && binding.chrome.getHeight() > 0;
        int dock = shown ? binding.chrome.getHeight() : insetBottom;
        int px = dock + getResources().getDimensionPixelSize(R.dimen.mp_dock_content_clearance);
        int scrimH = shown ? dock + getResources().getDimensionPixelSize(R.dimen.mp_dock_scrim_fade) : 0;
        android.view.ViewGroup.LayoutParams lp = binding.bottomScrim.getLayoutParams();
        if (lp.height != scrimH && scrimH > 0) {
            lp.height = scrimH;
            binding.bottomScrim.setLayoutParams(lp);
        }
        if (px == dockPx) return;
        dockPx = px;
        for (Fragment f : getSupportFragmentManager().getFragments()) {
            if (f.getView() != null) BottomChrome.apply(f.getView(), px);
            for (Fragment child : f.getChildFragmentManager().getFragments()) {
                if (child.getView() != null) BottomChrome.apply(child.getView(), px);
            }
        }
    }

    /**
     * Wide windows (tablet, landscape, unfolded): mini-player and nav share one row so the dock costs half the height.
     * Same views and spacing tokens as on phones; only the arrangement changes.
     */
    private void layoutDockForWidth() {
        if (!com.mhrsdev.musicplayer.ui.nav.Adaptive.isWide(this)) return;
        int gap = getResources().getDimensionPixelSize(R.dimen.mp_dock_gap);
        int side = getResources().getDimensionPixelSize(R.dimen.mp_dock_margin_h);
        binding.chrome.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        binding.chrome.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.CENTER_HORIZONTAL);
        android.widget.LinearLayout.LayoutParams mini = (android.widget.LinearLayout.LayoutParams) binding.miniPlayerSlot.getLayoutParams();
        mini.width = 0;
        mini.weight = 1f;
        mini.bottomMargin = 0;
        // Set both relative margins: once one is relative, the XML left/right ones are ignored.
        mini.setMarginStart(side);
        mini.setMarginEnd(gap);
        binding.miniPlayerSlot.setLayoutParams(mini);
        android.widget.LinearLayout.LayoutParams nav = (android.widget.LinearLayout.LayoutParams) binding.navSlot.getLayoutParams();
        nav.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
        nav.setMarginStart(0);
        nav.setMarginEnd(side);
        binding.navSlot.setLayoutParams(nav);
        binding.miniPlayerSlot.setVisibility(View.GONE); // renderMini shows it with the mini-player
    }

    // ---- floating nav + mini-player (T2 widgets) ----

    private static final int[] NAV_IDS = {R.id.mp_nav_home, R.id.mp_nav_search, R.id.mp_nav_library, R.id.mp_nav_settings};

    private void bindNav() {
        binding.nav.addDefaultItems();
        binding.nav.setOnItemSelectedListener(new FloatingNavView.OnItemSelectedListener() {
            @Override public void onItemSelected(int id) {
                for (int i = 0; i < NAV_IDS.length; i++) if (NAV_IDS[i] == id) openTab(Tab.values()[i]);
            }

            @Override public void onItemReselected(int id) {
                // Reselecting a tab: first back to its root, then (already at the root) scroll to the top.
                if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    openTab(shell.getTab().getValue());
                } else {
                    Fragment current = getSupportFragmentManager().findFragmentById(R.id.screen_container);
                    if (current != null && current.getView() != null) ScrollToTop.scroll(current.getView());
                }
            }
        });
        shell.getTab().observe(this, selected -> {
            int id = NAV_IDS[selected.ordinal()];
            if (binding.nav.getSelectedItem() != id) binding.nav.setSelectedItem(id, true);
            updateBackToHome();
        });
    }

    private void updateBackToHome() {
        if (backToHome == null) return;
        boolean root = getSupportFragmentManager().getBackStackEntryCount() == 0;
        backToHome.setEnabled(!permissionFlow && shell.getTab().getValue() != Tab.HOME && root);
        binding.homeGlow.setVisibility(!permissionFlow && root && shell.getTab().getValue() == Tab.HOME
                ? View.VISIBLE : View.GONE);
    }

    private void bindMiniPlayer() {
        binding.mini.setListener(new MiniPlayerView.Listener() {
            @Override public void onOpen() { openFullPlayer(); }

            @Override public void onPlayPause() { shell.togglePlayPause(); }

            @Override public void onNext() { shell.next(); }

            @Override public void onPrevious() { shell.previous(); }

            @Override public void onDismiss() { shell.pause(); }
        });
        ThemeController.get(this).accent().observe(this, binding.mini::setAccent);
        shell.getPlayback().observe(this, this::renderMini);
    }

    @Nullable private String miniTrackId;

    private void renderMini(PlaybackState s) {
        Track t = s == null ? null : s.currentTrack();
        binding.mini.setVisibility(t == null ? View.GONE : View.VISIBLE);
        if (com.mhrsdev.musicplayer.ui.nav.Adaptive.isWide(this)) binding.miniPlayerSlot.setVisibility(binding.mini.getVisibility());
        if (t == null) return;
        if (!t.id().equals(miniTrackId)) {
            miniTrackId = t.id();
            binding.mini.bind(t);
        }
        if (s.playing()) askNotificationsOnce();
        binding.mini.setPlaying(s.playing());
        binding.mini.setBuffering(s.buffering());
        binding.mini.setProgress(s.positionMs(), s.durationMs());
    }

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startService(new Intent(this, PlaybackService.class)); // re-posts the dropped notification
            });

    /** Android 13+: the media notification needs it. Asked once, on the first play; playback works if denied. */
    private void askNotificationsOnce() {
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED || PermissionPrefs.notificationsAsked(this)) {
            return;
        }
        PermissionPrefs.markNotificationsAsked(this);
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    // ---- ScreenNavigator ----

    @Override public void openTab(Tab tab) {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }
        if (shell.getTab().getValue() == tab && fm.findFragmentById(R.id.screen_container) != null) return;
        showTab(tab);
    }

    private void showTab(Tab tab) {
        Fragment f = switch (tab) {
            case HOME -> new HomeFragment();
            case SEARCH -> new SearchFragment();
            case LIBRARY -> new LibraryFragment();
            case SETTINGS -> new SettingsFragment();
        };
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out)
                .replace(R.id.screen_container, f)
                .commit();
        shell.selectTab(tab);
    }

    @Override public void openTrackList(TrackList kind, @Nullable String id) {
        push(TrackListFragment.newInstance(kind, id));
    }

    @Override public void openCollection(Collection kind) {
        push(CollectionFragment.newInstance(kind));
    }

    @Override public void openFragment(Fragment f, String tag) {
        FragmentManager fm = getSupportFragmentManager();
        Fragment top = fm.findFragmentById(R.id.screen_container);
        if (top != null && tag.equals(top.getTag())) return;
        push(f, tag);
    }

    private void push(Fragment f) {
        push(f, null);
    }

    private void push(Fragment f, @Nullable String tag) {
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out,
                        android.R.animator.fade_in, android.R.animator.fade_out)
                .replace(R.id.screen_container, f, tag)
                .addToBackStack(tag)
                .commit();
    }

    // ---- PlayerNavigator ----

    @Override public void openFullPlayer() {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(PLAYER_TAG) != null) return;
        Fragment f = instantiate(PLAYER_FRAGMENT);
        if (f == null) {
            Toast.makeText(this, R.string.nav_player_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        fm.beginTransaction()
                .setReorderingAllowed(true)
                .setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out,
                        android.R.animator.fade_in, android.R.animator.fade_out)
                .add(R.id.player_container, f, PLAYER_TAG)
                .addToBackStack(PLAYER_TAG)
                .commit();
    }

    @Override public void openQueue() {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(QUEUE_TAG) != null) return;
        Fragment f = instantiate(QUEUE_FRAGMENT);
        if (f == null) return;
        if (f instanceof DialogFragment) {
            ((DialogFragment) f).show(fm, QUEUE_TAG);
        } else {
            fm.beginTransaction().setReorderingAllowed(true)
                    .add(R.id.player_container, f, QUEUE_TAG).addToBackStack(QUEUE_TAG).commit();
        }
    }

    @Override public void closeFullPlayer() {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(PLAYER_TAG) != null) {
            fm.popBackStack(PLAYER_TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }
    }

    @Nullable private Fragment instantiate(String className) {
        try {
            return getSupportFragmentManager().getFragmentFactory().instantiate(getClassLoader(), className);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---- PermissionFragment.Host ----

    @Override public void onPermissionFlowDone() {
        if (!permissionFlow) return;
        permissionFlow = false;
        PermissionPrefs.markSkipped(this);
        // "Choose folders" also ends the flow without the grant: only scan when access really exists.
        if (AudioPermission.granted(this)) ((MusicPlayerApp) getApplication()).getContainer().onAudioPermissionGranted();
        binding.chrome.setVisibility(View.VISIBLE);
        binding.bottomScrim.setVisibility(View.VISIBLE);
        showTab(Tab.HOME);
    }

    /** Remembers that the first-run screen was passed, so it is not shown on every launch. */
    private static final class PermissionPrefs {
        static boolean skipped(android.content.Context c) {
            return c.getSharedPreferences("nav", MODE_PRIVATE).getBoolean("perm_done", false);
        }

        static boolean notificationsAsked(android.content.Context c) {
            return c.getSharedPreferences("nav", MODE_PRIVATE).getBoolean("notif_asked", false);
        }

        static void markNotificationsAsked(android.content.Context c) {
            c.getSharedPreferences("nav", MODE_PRIVATE).edit().putBoolean("notif_asked", true).apply();
        }

        static void markSkipped(android.content.Context c) {
            c.getSharedPreferences("nav", MODE_PRIVATE).edit().putBoolean("perm_done", true).apply();
        }
    }
}
