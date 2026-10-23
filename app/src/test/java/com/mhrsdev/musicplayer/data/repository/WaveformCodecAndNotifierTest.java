package com.mhrsdev.musicplayer.data.repository;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class WaveformCodecAndNotifierTest {
    @Test public void codecRoundTripsWithinOneStepAndClamps() {
        float[] in = {0f, 0.25f, 0.5f, 1f, -3f, 7f};
        float[] out = WaveformCodec.decode(WaveformCodec.encode(in));
        assertArrayEquals(new float[] {0f, 0.25f, 0.5f, 1f, 0f, 1f}, out, 1f / 255f);
        assertEquals(0, WaveformCodec.encode(new float[0]).length);
    }

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
