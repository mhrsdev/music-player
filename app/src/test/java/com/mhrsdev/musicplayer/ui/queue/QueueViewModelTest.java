package com.mhrsdev.musicplayer.ui.queue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.mhrsdev.musicplayer.domain.model.SleepTimer;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.fake.FakePlayerController;
import com.mhrsdev.musicplayer.fake.SampleData;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;

public class QueueViewModelTest {
    @Rule public InstantTaskExecutorRule rule = new InstantTaskExecutorRule();

    private final List<Track> tracks = SampleData.TRACKS.subList(0, 6);

    private QueueViewModel vm(FakePlayerController c) {
        QueueViewModel vm = new QueueViewModel(c);
        vm.getUi().observeForever(x -> {});
        return vm;
    }

    @Test public void upNextStartsAfterCurrent() {
        FakePlayerController c = new FakePlayerController(tracks, 2);
        QueueViewModel vm = vm(c);
        QueueViewModel.UiState ui = vm.getUi().getValue();
        assertEquals(3, ui.upNext().size());
        assertEquals(tracks.get(3), ui.upNext().get(0));
        assertEquals(3, QueueViewModel.toQueueIndex(2, 0));
    }

    @Test public void moveMapsAdapterPositionsToQueueIndices() {
        FakePlayerController c = new FakePlayerController(tracks, 2);
        QueueViewModel vm = vm(c);
        vm.move(0, 2);
        List<Track> up = vm.getUi().getValue().upNext();
        assertEquals(tracks.get(4), up.get(0));
        assertEquals(tracks.get(3), up.get(2));
        assertEquals(tracks.get(2), c.getState().getValue().currentTrack());
    }

    @Test public void removeAndUndoRestoresPosition() {
        FakePlayerController c = new FakePlayerController(tracks, 1);
        QueueViewModel vm = vm(c);
        QueueViewModel.Removed r = vm.remove(1);
        assertNotNull(r);
        assertEquals(tracks.get(3), r.track());
        assertEquals(3, vm.getUi().getValue().upNext().size());
        vm.undoRemove(r);
        assertEquals(tracks.get(3), vm.getUi().getValue().upNext().get(1));
        assertNull(vm.remove(99));
    }

    @Test public void sleepTimerMapping() {
        FakePlayerController c = new FakePlayerController(tracks, 0);
        QueueViewModel vm = vm(c);
        assertEquals(SleepTimer.Kind.OFF, vm.getUi().getValue().sleepKind());
        vm.setSleepTimer(23);
        assertEquals(SleepTimer.Kind.COUNTDOWN, vm.getUi().getValue().sleepKind());
        assertEquals(23, vm.getUi().getValue().sleepMinutes());
        vm.setSleepTimerEndOfTrack();
        assertEquals(SleepTimer.Kind.END_OF_TRACK, vm.getUi().getValue().sleepKind());
        vm.cancelSleepTimer();
        assertEquals(SleepTimer.Kind.OFF, vm.getUi().getValue().sleepKind());
    }

    @Test public void shuffleUndo() {
        FakePlayerController c = new FakePlayerController(tracks, 0);
        QueueViewModel vm = vm(c);
        List<Track> before = vm.getUi().getValue().upNext();
        vm.shuffle();
        assertEquals(true, vm.getUi().getValue().canUndoShuffle());
        vm.undoShuffle();
        assertEquals(before, vm.getUi().getValue().upNext());
    }

    @Test public void minutesLeftCoversRestOfQueue() {
        FakePlayerController c = new FakePlayerController(tracks, 0);
        QueueViewModel vm = vm(c);
        long total = c.getState().getValue().remainingQueueMs();
        assertEquals((int) ((total + 59_999) / 60_000), vm.getUi().getValue().minutesLeft());
    }
}
