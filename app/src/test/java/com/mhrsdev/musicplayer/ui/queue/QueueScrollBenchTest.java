package com.mhrsdev.musicplayer.ui.queue;

import android.content.Context;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.ui.widget.ScrollBench;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.lang.reflect.Proxy;
import java.util.List;

/** SC1 (D39) queue sheet scroll path with the owner-shaped library as the queue. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w412dp-h915dp-night-xxhdpi")
public class QueueScrollBenchTest {

    @Test public void queueRows() throws Exception {
        Context c = ScrollBench.context();
        ScrollBench.installLoader(c);
        List<Track> lib = ScrollBench.library();
        RecyclerView rv = new RecyclerView(c);
        rv.setLayoutManager(new LinearLayoutManager(c));
        QueueAdapter a = new QueueAdapter((QueueAdapter.Actions) Proxy.newProxyInstance(QueueAdapter.class.getClassLoader(),
                new Class<?>[] {QueueAdapter.Actions.class}, (p, m, args) -> null));
        rv.setAdapter(a);
        a.submit(lib.get(0), lib.subList(1, lib.size()));
        ScrollBench.layOut(rv, 800);
        ScrollBench.settle(10);
        int[] pos = new int[a.getItemCount() - 1];
        for (int i = 0; i < pos.length; i++) pos[i] = i + 1;
        ScrollBench.RowCost row = ScrollBench.rowCost("queue row", rv, 0, pos, 400);
        System.out.println(row);
        org.junit.Assert.assertTrue(row.toString(), row.bindBytes() < 16 * 1024); // v0.3.2: 60.7 KB per bind
        System.out.println(ScrollBench.scroll("queue", rv, 300, 0, 40, null));
    }
}
