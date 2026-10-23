package com.mhrsdev.musicplayer.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class ChangeNotifierTest {
    @Test public void notifierStampsAndDispatchesByTopic() {
        ChangeNotifier n = new ChangeNotifier();
        long likesBefore = n.stamp(ChangeNotifier.LIKES);
        long tracksBefore = n.stamp(ChangeNotifier.TRACKS);
        AtomicInteger calls = new AtomicInteger();
        n.add(topics -> calls.incrementAndGet());
        n.notifyChanged(ChangeNotifier.LIKES);
        assertNotEquals(likesBefore, n.stamp(ChangeNotifier.LIKES));
        assertEquals(n.stamp(ChangeNotifier.LIKES) + n.stamp(ChangeNotifier.HISTORY),
                n.stamp(ChangeNotifier.LIKES | ChangeNotifier.HISTORY));
        assertEquals(tracksBefore, n.stamp(ChangeNotifier.TRACKS));
        assertEquals(1, calls.get());
    }
}
