package com.mhrsdev.musicplayer.ui.nav;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** v0.3.1 (D29): what the opaque full player hides is not drawn, and comes back exactly as it was. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class PlayerCoverTest {
    @Test public void hidesOnlyWhatWasVisibleAndRestoresIt() {
        FrameLayout root = new FrameLayout(RuntimeEnvironment.getApplication());
        Drawable bg = new ColorDrawable(0xFF101010);
        root.setBackground(bg);
        View screens = new View(root.getContext()), scrim = new View(root.getContext()), dock = new View(root.getContext());
        scrim.setVisibility(View.GONE); // e.g. first-run flow: the shell keeps it gone
        PlayerCover cover = new PlayerCover(root, screens, scrim, dock);

        cover.setCovered(true);
        assertTrue(cover.isCovered());
        assertEquals(View.INVISIBLE, screens.getVisibility()); // keeps its size: no relayout under the player
        assertEquals(View.INVISIBLE, dock.getVisibility());
        assertEquals(View.GONE, scrim.getVisibility());
        assertNull("no root fill under the opaque player", root.getBackground());
        assertFalse("hidden views stop their loops (onVisibilityAggregated)", screens.isShown());

        cover.setCovered(true); // idempotent
        cover.setCovered(false);
        assertEquals(View.VISIBLE, screens.getVisibility());
        assertEquals(View.VISIBLE, dock.getVisibility());
        assertEquals(View.GONE, scrim.getVisibility());
        assertSame(bg, root.getBackground());
    }
}
