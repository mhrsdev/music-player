package com.mhrsdev.musicplayer.ui.widget;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.ViewGroup;
import androidx.fragment.app.FragmentActivity;
import com.mhrsdev.musicplayer.data.settings.PerformanceProfile;
import com.mhrsdev.musicplayer.data.settings.SeekBarStyle;
import com.mhrsdev.musicplayer.ui.theme.MotionPolicy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Every moving seek style (B2's FrameSeekView family) obeys Settings &gt; Performance "Moving seek bar" live, through
 * MotionPolicy: off stops its FrameDriver frames at once, on starts them again without re-creating the view.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-night-xhdpi")
public class PerformanceSeekFramesTest {

    @Before public void motionAllowedBySystem() {
        // Like B2PlayerSeekRenderTest: the system animator scale and battery saver must allow motion first.
        android.provider.Settings.Global.putFloat(RuntimeEnvironment.getApplication().getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        MotionPolicy.setBatterySaverOverride(false);
    }

    @After public void reset() {
        MotionPolicy.setPerformance(PerformanceProfile.FULL);
        MotionPolicy.setBatterySaverOverride(null);
    }

    @Test public void movingSeekBarSwitchStartsAndStopsEveryFrameStyleLive() {
        MotionPolicy.setPerformance(PerformanceProfile.FULL);
        FragmentActivity a = Robolectric.buildActivity(WidgetGalleryActivity.class).setup().get();
        ViewGroup content = a.findViewById(android.R.id.content);
        int checked = 0;
        for (SeekBarStyle style : SeekBarStyle.values()) {
            BaseSeekView v = SeekStyles.create(a, style);
            // Styles that loop while playing (Pulse dots, Liquid). Others only spring briefly after a seek.
            if (!SeekStyles.animates(style) || !(v instanceof FrameSeekView f)) continue;
            content.addView(f, new ViewGroup.LayoutParams(720, 112));
            f.setDuration(200_000);
            f.setPlaying(true);
            assertTrue(style + " animates with Full", f.isAnimating());

            MotionPolicy.setPerformance(PerformanceProfile.FULL.withSeekMotion(false));
            f.refreshMotion(); // what PlayerFragment does when the setting changes
            assertFalse(style + " still with Moving seek bar off", f.isAnimating());

            MotionPolicy.setPerformance(PerformanceProfile.LIGHT.withSeekMotion(true));
            f.refreshMotion();
            assertTrue(style + " moves again, live", f.isAnimating());

            MotionPolicy.setPerformance(PerformanceProfile.LIGHT);
            f.refreshMotion();
            assertFalse(style + " still under Light", f.isAnimating());

            MotionPolicy.setPerformance(PerformanceProfile.FULL);
            content.removeView(f);
            checked++;
        }
        assertTrue("found the moving styles, got " + checked, checked >= 2);
    }
}
