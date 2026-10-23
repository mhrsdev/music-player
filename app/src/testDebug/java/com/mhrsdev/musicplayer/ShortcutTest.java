package com.mhrsdev.musicplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import com.mhrsdev.musicplayer.ui.nav.ScreenNavigator;
import com.mhrsdev.musicplayer.ui.nav.ShortcutIntents;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/** Static launcher shortcuts reach the right screen / action (fake data). */
@RunWith(RobolectricTestRunner.class)
@Config(application = FakeRenderApp.class, sdk = 35)
public class ShortcutTest {
    private MainActivity launch(String action) {
        RuntimeEnvironment.getApplication().getSharedPreferences("nav", 0).edit().putBoolean("perm_done", true).commit();
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), MainActivity.class).setAction(action);
        MainActivity a = Robolectric.buildActivity(MainActivity.class, intent).setup().get();
        for (int i = 0; i < 4; i++) {
            ShadowLooper.idleMainLooper();
            try { Thread.sleep(40); } catch (InterruptedException e) { throw new RuntimeException(e); }
        }
        ShadowLooper.idleMainLooper();
        return a;
    }

    @Test public void searchOpensTheSearchTab() {
        MainActivity a = launch(ShortcutIntents.ACTION_SEARCH);
        assertEquals(ScreenNavigator.Tab.SEARCH, a.getSupportFragmentManager().findFragmentById(R.id.screen_container) == null
                ? null : ScreenNavigator.Tab.SEARCH);
        assertTrue(a.getSupportFragmentManager().findFragmentById(R.id.screen_container)
                instanceof com.mhrsdev.musicplayer.ui.search.SearchFragment);
    }

    @Test public void likedOpensTheLikedList() {
        MainActivity a = launch(ShortcutIntents.ACTION_LIKED);
        assertTrue(a.getSupportFragmentManager().findFragmentById(R.id.screen_container)
                instanceof com.mhrsdev.musicplayer.ui.library.TrackListFragment);
    }

    @Test public void shuffleAllStartsPlayback() {
        MainActivity a = launch(ShortcutIntents.ACTION_SHUFFLE_ALL);
        var state = ((MusicPlayerApp) a.getApplication()).getContainer().player.getState().getValue();
        assertEquals(a.getString(R.string.shortcut_shuffle_playing_from), state.playingFrom());
    }
}
