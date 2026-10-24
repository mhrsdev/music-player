package com.mhrsdev.musicplayer.appwidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.app.Application;
import android.content.Intent;
import android.widget.RemoteViews;
import com.mhrsdev.musicplayer.FakeRenderApp;
import com.mhrsdev.musicplayer.domain.model.NowPlaying;
import com.mhrsdev.musicplayer.domain.repository.PlayerController;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(application = FakeRenderApp.class, sdk = 35)
public class WidgetLogicTest {
    @Test public void progressIsClamped() {
        assertEquals(0.25f, new NowPlaying("t", "a", null, true, 25, 100).progress(), 0f);
        assertEquals(1f, new NowPlaying("t", "a", null, true, 500, 100).progress(), 0f);
        assertEquals(0f, new NowPlaying("t", "a", null, true, 5, 0).progress(), 0f);
    }

    @Test public void sampleSizeKeepsAtLeastTheTargetEdge() {
        assertEquals(1, WidgetArt.sampleSize(300, 300, 420));
        assertEquals(4, WidgetArt.sampleSize(1800, 1800, 420));
    }

    @Test public void playPauseButtonReachesThePlayer() {
        Application app = RuntimeEnvironment.getApplication();
        PlayerController player = ((com.mhrsdev.musicplayer.MusicPlayerApp) app).getContainer().player;
        boolean before = player.getState().getValue().playing();
        new WidgetActionReceiver().onReceive(app, new Intent(WidgetActionReceiver.ACTION_PLAY_PAUSE));
        ShadowLooper.idleMainLooper();
        assertNotEquals(before, player.getState().getValue().playing());
    }

    @Test public void layoutsBuildForEverySizeAndState() {
        Application app = RuntimeEnvironment.getApplication();
        NowPlaying np = new NowPlaying("t", "a", null, false, 0, 1000);
        for (WidgetKind kind : WidgetKind.values()) {
            RemoteViews idle = WidgetRenderer.build(app, kind, null, null, true, 200, 100);
            RemoteViews playing = WidgetRenderer.build(app, kind, np, null, false, 200, 100);
            assertEquals(kind.layout, idle.getLayoutId());
            assertEquals(kind.layout, playing.getLayoutId());
        }
    }

    /** X1: a launcher-reported tiny / zero cell or a huge one must not reach createBitmap with bad sizes. */
    @Test public void coverBuildsForOddCellSizes() {
        Application app = RuntimeEnvironment.getApplication();
        NowPlaying np = new NowPlaying("t", "a", null, true, 10, 1000);
        android.graphics.Bitmap art = android.graphics.Bitmap.createBitmap(3000, 3000, android.graphics.Bitmap.Config.RGB_565);
        for (WidgetKind kind : WidgetKind.values()) {
            for (int[] size : new int[][] {{1, 1}, {8, 8}, {16, 16}, {2000, 2000}}) {
                assertEquals(kind.layout, WidgetRenderer.build(app, kind, np, art, true, size[0], size[1]).getLayoutId());
            }
        }
    }
}
