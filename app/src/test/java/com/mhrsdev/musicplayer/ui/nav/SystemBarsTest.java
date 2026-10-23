package com.mhrsdev.musicplayer.ui.nav;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.res.Resources;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Friend's phone: the dock sat on the physical edge because the bottom inset was reported as 0. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SystemBarsTest {
    private final Resources res = RuntimeEnvironment.getApplication().getResources();

    @Test public void zeroInsetsNeverGoBelowTheFloor() {
        WindowInsetsCompat none = new WindowInsetsCompat.Builder().build();
        assertTrue(SystemBars.dockBottomInset(none, res, 40) >= 40);
    }

    @Test public void usesTheLargestReportedBottomInset() {
        WindowInsetsCompat in = new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 30))
                .setInsets(WindowInsetsCompat.Type.mandatorySystemGestures(), Insets.of(0, 0, 0, 70))
                .build();
        assertEquals(70, SystemBars.dockBottomInset(in, res, 10));
        assertEquals(100, SystemBars.dockBottomInset(in, res, 100));
    }
}
