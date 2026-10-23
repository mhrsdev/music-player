package com.mhrsdev.musicplayer.ui.search;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class RecentSearchesTest {

    @Test public void newestFirstWithoutDuplicatesAndCapped() {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < RecentSearches.MAX + 3; i++) l = RecentSearches.push(l, "q" + i);
        assertEquals(RecentSearches.MAX, l.size());
        assertEquals("q" + (RecentSearches.MAX + 2), l.get(0));
        l = RecentSearches.push(l, "  Q5 ");
        assertEquals("Q5", l.get(0));
        assertEquals(1, l.stream().filter(s -> s.equalsIgnoreCase("q5")).count());
        assertEquals(l, RecentSearches.push(l, "   "));
    }

    @Test public void inMemoryStoreAddRemoveClear() {
        RecentSearches r = new RecentSearches((android.content.SharedPreferences) null);
        r.add("neffex");
        r.add("kasra");
        assertEquals(List.of("kasra", "neffex"), r.get());
        r.remove("kasra");
        assertEquals(List.of("neffex"), r.get());
        r.clear();
        assertEquals(List.of(), r.get());
    }
}
