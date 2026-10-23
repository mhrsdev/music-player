package com.mhrsdev.musicplayer.ui.nav;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import androidx.recyclerview.widget.RecyclerView;
import org.robolectric.RuntimeEnvironment;
import com.mhrsdev.musicplayer.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Owner bug: list ends hidden behind the dock. Marked lists must get the real dock height, and follow changes. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class BottomChromeTest {

    @Test public void replacesThePlaceholderPaddingAndFollowsChanges() {
        Context c = RuntimeEnvironment.getApplication();
        int marker = c.getResources().getDimensionPixelSize(R.dimen.scr_bottom_chrome);
        FrameLayout root = new FrameLayout(c);
        RecyclerView list = new RecyclerView(c);
        list.setPadding(0, 8, 0, marker);
        LinearLayout other = new LinearLayout(c);
        other.setPadding(0, 0, 0, 5);
        root.addView(list);
        root.addView(other);

        BottomChrome.apply(root, 321);
        assertEquals(321, list.getPaddingBottom());
        assertEquals(8, list.getPaddingTop());
        assertFalse(list.getClipToPadding());
        assertEquals(5, other.getPaddingBottom());

        BottomChrome.apply(root, 150); // mini-player hidden: smaller dock
        assertEquals(150, list.getPaddingBottom());
    }
}
