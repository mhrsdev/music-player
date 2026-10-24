package com.mhrsdev.musicplayer.ui.nav;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import com.mhrsdev.musicplayer.FakeRenderApp;
import com.mhrsdev.musicplayer.MainActivity;
import com.mhrsdev.musicplayer.R;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowLooper;

/**
 * PF-A's PlayerCover in the real shell (fake data): the screen under the opaque player is hidden only while the player
 * rests; dragging shows it, a short drag snaps back and covers again, a long drag closes and restores everything;
 * Back during the close animation must neither crash nor leave the shell hidden.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(application = FakeRenderApp.class, sdk = 35, qualifiers = "w411dp-h891dp-xxhdpi")
public class PlayerCoverFlowTest {
    private ActivityController<MainActivity> ctl;

    @Test public void coverFollowsThePlayer() throws Exception {
        android.app.Application app = RuntimeEnvironment.getApplication();
        org.robolectric.shadows.ShadowApplication sa = Shadow.extract(app);
        sa.grantPermissions(android.Manifest.permission.READ_MEDIA_AUDIO);
        app.getSharedPreferences("nav", android.content.Context.MODE_PRIVATE).edit().putBoolean("perm_done", true).commit();
        ctl = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a = ctl.get();
        frames(10);
        View shell = a.findViewById(R.id.screen_container);

        a.openFullPlayer();
        frames(60);
        View root = a.findViewById(R.id.player_root);
        assertNotNull(root);
        assertEquals("covered at rest (alpha " + root.getAlpha() + ", ty " + root.getTranslationY() + ")",
                View.INVISIBLE, shell.getVisibility());

        drag(root, 300, 700);
        frames(40);
        assertEquals("short drag snaps back and covers again", View.INVISIBLE, shell.getVisibility());

        drag(root, 300, 2400);
        frames(40);
        assertNull("long drag closes", a.getSupportFragmentManager().findFragmentByTag("player"));
        assertEquals(View.VISIBLE, shell.getVisibility());
        assertEquals(View.VISIBLE, a.findViewById(R.id.chrome).getVisibility());
        assertNotNull(a.findViewById(R.id.root).getBackground());

        a.openFullPlayer();
        frames(60);
        drag(a.findViewById(R.id.player_root), 300, 2400);
        a.getOnBackPressedDispatcher().onBackPressed(); // during the 200 ms close animation
        frames(40);
        assertNull(a.getSupportFragmentManager().findFragmentByTag("player"));
        assertEquals("shell left hidden", View.VISIBLE, shell.getVisibility());

        a.openFullPlayer();
        frames(60);
        a.openTab(ScreenNavigator.Tab.LIBRARY); // a tab tap pops the player without a drag
        frames(40);
        assertEquals(View.VISIBLE, shell.getVisibility());
    }

    private static void drag(View v, float fromY, float toY) {
        long t = SystemClock.uptimeMillis();
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 200, fromY, 0));
        for (int i = 1; i <= 10; i++) {
            v.dispatchTouchEvent(MotionEvent.obtain(t, t + i * 100, MotionEvent.ACTION_MOVE, 200,
                    fromY + (toY - fromY) * i / 10f, 0));
        }
        v.dispatchTouchEvent(MotionEvent.obtain(t, t + 1100, MotionEvent.ACTION_UP, 200, toY, 0));
    }

    private void frames(int n) {
        for (int i = 0; i < n; i++) ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS);
        ((ShadowLooper) Shadow.extract(Looper.getMainLooper())).idleFor(Duration.ofMillis(1));
    }
}
