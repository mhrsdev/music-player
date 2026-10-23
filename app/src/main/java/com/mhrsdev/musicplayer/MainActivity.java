package com.mhrsdev.musicplayer;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
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
import com.mhrsdev.musicplayer.ui.home.HomeFragment;
import com.mhrsdev.musicplayer.ui.library.Art;
import com.mhrsdev.musicplayer.ui.library.CollectionFragment;
import com.mhrsdev.musicplayer.ui.library.LibraryFragment;
import com.mhrsdev.musicplayer.ui.library.TrackListFragment;
import com.mhrsdev.musicplayer.ui.nav.AudioPermission;
import com.mhrsdev.musicplayer.ui.nav.PermissionFragment;
import com.mhrsdev.musicplayer.ui.nav.PlayerNavigator;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator;
import com.mhrsdev.musicplayer.ui.nav.ShellViewModel;
import com.mhrsdev.musicplayer.ui.search.SearchFragment;
import com.mhrsdev.musicplayer.ui.settings.PrefsSettingsStore;
import com.mhrsdev.musicplayer.ui.settings.SettingsFragment;
import com.mhrsdev.musicplayer.ui.settings.ThemeApplier;

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

    private ActivityMainBinding binding;
    private ShellViewModel shell;
    private boolean permissionFlow;
    private OnBackPressedCallback backToHome;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeApplier.applyNightMode(new PrefsSettingsStore(this).getTheme());
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsets();

        shell = new ViewModelProvider(this,
                ShellViewModel.factory(((MusicPlayerApp) getApplication()).getContainer().player))
                .get(ShellViewModel.class);

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
            } else {
                permissionFlow = true;
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.screen_container, new PermissionFragment()).commit();
            }
        } else {
            permissionFlow = savedInstanceState.getBoolean(STATE_PERMISSION);
        }
        binding.chrome.setVisibility(permissionFlow ? View.GONE : View.VISIBLE);
        binding.bottomScrim.setVisibility(permissionFlow ? View.GONE : View.VISIBLE);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean(STATE_PERMISSION, permissionFlow);
    }

    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            binding.screenContainer.setPadding(bars.left, bars.top, bars.right, 0);
            int gap = Math.round(12 * getResources().getDisplayMetrics().density);
            binding.chrome.setPadding(bars.left, 0, bars.right, bars.bottom + gap);
            return insets;
        });
    }

    // ---- floating nav (temporary until T2's FloatingNavView) ----

    private void bindNav() {
        TextView[] tabs = {binding.nav.tabHome, binding.nav.tabSearch, binding.nav.tabLibrary, binding.nav.tabSettings};
        Tab[] values = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab t = values[i];
            tabs[i].setOnClickListener(v -> openTab(t));
        }
        shell.getTab().observe(this, selected -> {
            int on = getColor(R.color.scr_tertiary);
            int off = getColor(R.color.scr_on_surface_variant);
            for (int i = 0; i < tabs.length; i++) {
                boolean s = values[i] == selected;
                tabs[i].setSelected(s);
                tabs[i].setTextColor(s ? getColor(R.color.scr_on_surface) : off);
                tabs[i].setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(s ? on : off));
            }
            updateBackToHome();
        });
    }

    private void updateBackToHome() {
        if (backToHome == null) return;
        backToHome.setEnabled(!permissionFlow && shell.getTab().getValue() != Tab.HOME
                && getSupportFragmentManager().getBackStackEntryCount() == 0);
    }

    // ---- mini-player (temporary until T2's MiniPlayerView) ----

    private void bindMiniPlayer() {
        binding.mini.miniRoot.setOnClickListener(v -> openFullPlayer());
        binding.mini.miniPlay.setOnClickListener(v -> shell.togglePlayPause());
        shell.getPlayback().observe(this, this::renderMini);
    }

    private void renderMini(PlaybackState s) {
        Track t = s == null ? null : s.currentTrack();
        binding.mini.miniRoot.setVisibility(t == null ? View.GONE : View.VISIBLE);
        if (t == null) return;
        binding.mini.miniTitle.setText(t.title());
        binding.mini.miniArtist.setText(t.artistName());
        Art.bind(binding.mini.miniArt, t.artworkUri(), t.placeholderSeed(), 16, false);
        binding.mini.miniPlay.setImageResource(s.playing() ? R.drawable.nav_ic_pause : R.drawable.nav_ic_play);
        binding.mini.miniPlay.setContentDescription(getString(s.playing() ? R.string.nav_pause : R.string.nav_play));
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

    private void push(Fragment f) {
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out,
                        android.R.animator.fade_in, android.R.animator.fade_out)
                .replace(R.id.screen_container, f)
                .addToBackStack(null)
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
        binding.chrome.setVisibility(View.VISIBLE);
        binding.bottomScrim.setVisibility(View.VISIBLE);
        showTab(Tab.HOME);
    }

    /** Remembers that the first-run screen was passed, so it is not shown on every launch. */
    private static final class PermissionPrefs {
        static boolean skipped(android.content.Context c) {
            return c.getSharedPreferences("nav", MODE_PRIVATE).getBoolean("perm_done", false);
        }

        static void markSkipped(android.content.Context c) {
            c.getSharedPreferences("nav", MODE_PRIVATE).edit().putBoolean("perm_done", true).apply();
        }
    }
}
