package com.mhrsdev.musicplayer.ui.search;

import android.content.Context;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mhrsdev.musicplayer.domain.model.SearchResult;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.ui.widget.ScrollBench;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

/** SC1 (D39) search results scroll path (track hits with the match highlighted). */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w412dp-h915dp-night-xxhdpi")
public class SearchScrollBenchTest {

    @Test public void resultRows() throws Exception {
        Context c = ScrollBench.context();
        ScrollBench.installLoader(c);
        List<SearchResult> results = new ArrayList<>();
        for (Track t : ScrollBench.library()) results.add(SearchResult.of(t));
        RecyclerView rv = new RecyclerView(c);
        rv.setLayoutManager(new LinearLayoutManager(c));
        SearchResultAdapter a = new SearchResultAdapter(new SearchResultAdapter.Listener() {
            @Override public void onResultClick(SearchResult result) { }

            @Override public void onResultLongClick(android.view.View anchor, SearchResult result) { }
        });
        a.setQuery("me");
        rv.setAdapter(a);
        a.submitList(results);
        ScrollBench.settle(3);
        ScrollBench.layOut(rv, 800);
        ScrollBench.settle(10);
        int[] pos = new int[results.size()];
        for (int i = 0; i < pos.length; i++) pos[i] = i;
        ScrollBench.RowCost row = ScrollBench.rowCost("search row", rv, 0, pos, 400);
        System.out.println(row);
        org.junit.Assert.assertTrue(row.toString(), row.bindBytes() < 8 * 1024); // v0.3.2: 10.5 KB
        System.out.println(ScrollBench.scroll("search", rv, 300, 0, 40, null));
    }
}
