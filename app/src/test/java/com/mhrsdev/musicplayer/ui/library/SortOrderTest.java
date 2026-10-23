package com.mhrsdev.musicplayer.ui.library;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.mhrsdev.musicplayer.domain.model.Track;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.Test;

public class SortOrderTest {
    private static Track t(String id, String title, long duration, long added) {
        return new Track(id, title, "a", "Artist", "al", "Album", duration, null, 1, null, false, false, 0, 0, added);
    }

    private final List<Track> list = List.of(t("1", "beta", 300, 1), t("2", "Alpha", 100, 3), t("3", "9 lives", 200, 2));

    private static List<String> ids(List<Track> tracks) {
        List<String> out = new ArrayList<>();
        for (Track x : tracks) out.add(x.id());
        return out;
    }

    @Test public void defaultKeepsSourceOrder() {
        assertSame(list, SortOrder.DEFAULT.apply(list, null, Locale.US));
        assertEquals(List.of("3", "2", "1"), ids(new SortOrder(SortOrder.Field.DEFAULT, false).apply(list, null, Locale.US)));
    }

    @Test public void titleIsCaseInsensitiveAndDescWorks() {
        assertEquals(List.of("3", "2", "1"), ids(new SortOrder(SortOrder.Field.TITLE, true).apply(list, null, Locale.US)));
        assertEquals(List.of("1", "2", "3"), ids(new SortOrder(SortOrder.Field.TITLE, false).apply(list, null, Locale.US)));
    }

    @Test public void numericAndPlayCountOrders() {
        assertEquals(List.of("2", "3", "1"), ids(new SortOrder(SortOrder.Field.DURATION, true).apply(list, null, Locale.US)));
        assertEquals(List.of("2", "3", "1"), ids(new SortOrder(SortOrder.Field.DATE_ADDED, false).apply(list, null, Locale.US)));
        assertEquals(List.of("3", "1", "2"),
                ids(new SortOrder(SortOrder.Field.MOST_PLAYED, false).apply(list, Map.of("3", 5, "1", 2), Locale.US)));
    }

    @Test public void sectionsAndEncoding() {
        assertEquals("#", SortOrder.sectionOf("9 lives", Locale.US));
        assertEquals("B", SortOrder.sectionOf("beta", Locale.US));
        assertEquals("س", SortOrder.sectionOf("سهراب", Locale.US));
        List<TrackListViewModel.Section> s = TrackListViewModel.sections(
                new SortOrder(SortOrder.Field.TITLE, true).apply(list, null, Locale.US), SortOrder.Field.TITLE, Locale.US);
        assertEquals(3, s.size());
        SortOrder o = new SortOrder(SortOrder.Field.ARTIST, false);
        assertEquals(o, SortOrder.decode(o.encode()));
        assertEquals(SortOrder.DEFAULT, SortOrder.decode("garbage"));
    }
}
