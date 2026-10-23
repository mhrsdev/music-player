package com.mhrsdev.musicplayer.ui.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.view.View;
import com.mhrsdev.musicplayer.ui.nav.HomeGlow;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class HomeBackdropTest {

    @Test public void glowColorKeepsHueAndSoftensExtremes() {
        float[] hsv = new float[3];
        Color.colorToHSV(HomeBackdrop.glowColor(Color.rgb(255, 0, 0)) | 0xFF000000, hsv);
        assertEquals(0f, hsv[0], 1f);
        assertTrue(hsv[1] <= 0.71f);
        Color.colorToHSV(HomeBackdrop.glowColor(Color.rgb(40, 44, 40)) | 0xFF000000, hsv);
        assertTrue("grey covers still glow a little", hsv[1] >= 0.34f);
        assertEquals(0.85f, hsv[2], 0.01f);
    }

    @Test public void setTintCrossFadesOnceAndDedupes() {
        View glow = new View(RuntimeEnvironment.getApplication());
        glow.setId(com.mhrsdev.musicplayer.R.id.home_glow);
        glow.setBackground(HomeGlow.create(glow.getContext()));
        HomeGlow.setTint(glow, 0x3366CC, true);
        assertTrue(glow.getBackground() instanceof TransitionDrawable);
        Drawable bg = glow.getBackground();
        HomeGlow.setTint(glow, 0x3366CC, true); // same colour: no new fade
        assertSame(bg, glow.getBackground());
        HomeGlow.setTint(glow, 0x22AA55, false); // reduce motion: instant
        assertTrue(!(glow.getBackground() instanceof TransitionDrawable));
    }
}
