package com.mhrsdev.musicplayer.ui.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.mhrsdev.musicplayer.domain.model.HomeSection;
import com.mhrsdev.musicplayer.domain.model.HomeSectionStyle;
import java.util.List;
import org.junit.Test;

/** Owner bug (real device, Persian UI): Home section titles were the data layer's English strings. */
public class HomeSectionTitlesTest {

    @Test public void everySectionTheDataLayerEmitsIsLocalized() {
        for (String id : new String[] {"new-releases", "throwbacks", "playlists", "quick-picks"}) {
            assertNotEquals(id, 0, HomeSectionTitles.titleRes(id));
        }
    }

    @Test public void unknownIdKeepsTheGivenTitle() {
        assertEquals(0, HomeSectionTitles.titleRes("something-new"));
        assertEquals(0, HomeSectionTitles.titleRes(null));
        HomeSection s = new HomeSection("something-new", "Given", null, HomeSectionStyle.LIST, List.of());
        assertEquals("Given", HomeSectionTitles.title(null, s));
    }
}
