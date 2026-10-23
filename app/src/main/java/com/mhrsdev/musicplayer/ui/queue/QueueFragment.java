package com.mhrsdev.musicplayer.ui.queue;

import android.graphics.Canvas;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.mhrsdev.musicplayer.MusicPlayerApp;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.databinding.QueueSheetBinding;
import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.domain.model.SleepTimer;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Queue bottom sheet (design v1, 07): sleep-timer chip, "Playing from X · N min left" chip, shuffle (+ Undo),
 * Now playing row, drag-to-reorder by handle, swipe end-to-start to remove (+ Undo snackbar).
 * Open with {@link #show(FragmentManager)} (T3's PlayerNavigator.openQueue should call it).
 */
public final class QueueFragment extends BottomSheetDialogFragment {
    public static final String TAG = "queue";

    private QueueSheetBinding b;
    private QueueViewModel vm;
    private QueueAdapter adapter;
    private ItemTouchHelper touchHelper;
    private boolean dragging;
    private int dragFrom = -1, dragTo = -1;
    @Nullable private QueueViewModel.UiState pending;

    public static void show(FragmentManager fm) {
        if (fm.findFragmentByTag(TAG) == null) new QueueFragment().show(fm, TAG);
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = QueueSheetBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override public void onStart() {
        super.onStart();
        View sheet = getDialog() == null ? null : getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) sheet.setBackground(null); // our own 32 dp sheet shape
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        AppContainer c = ((MusicPlayerApp) requireActivity().getApplication()).getContainer();
        vm = new ViewModelProvider(this, QueueViewModel.factory(c.player)).get(QueueViewModel.class);

        adapter = new QueueAdapter(new QueueAdapter.Actions() {
            @Override public void onPlay(int pos) { vm.play(pos); }
            @Override public void onStartDrag(RecyclerView.ViewHolder h) { touchHelper.startDrag(h); }
            @Override public void onMoveA11y(int from, int to) { vm.move(from, to); }
            @Override public void onRemove(int pos) { removeWithUndo(pos); }
        });
        b.queueList.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.queueList.setAdapter(adapter);
        touchHelper = new ItemTouchHelper(new TouchCallback());
        touchHelper.attachToRecyclerView(b.queueList);

        b.queueShuffle.setOnClickListener(v -> {
            vm.shuffle();
            snackbar(getString(R.string.queue_shuffled), vm::undoShuffle);
        });
        b.queueSleep.setOnClickListener(v -> showSleepTimerPicker());

        vm.getUi().observe(getViewLifecycleOwner(), this::render);
    }

    private void render(QueueViewModel.UiState ui) {
        if (dragging) { pending = ui; return; } // don't fight the user's finger; apply on drop
        Locale loc = getResources().getConfiguration().getLocales().get(0);
        NumberFormat nf = NumberFormat.getIntegerInstance(loc);
        adapter.submit(ui.current(), ui.upNext());
        b.queueEmpty.setVisibility(ui.current() == null ? View.VISIBLE : View.GONE);
        String left = nf.format(ui.minutesLeft());
        b.queuePlayingFrom.setText(ui.playingFrom() == null
                ? getString(R.string.queue_min_left, left)
                : getString(R.string.queue_playing_from, ui.playingFrom(), left));
        switch (ui.sleepKind()) {
            case COUNTDOWN:
                String m = nf.format(ui.sleepMinutes());
                b.queueSleep.setText(getString(R.string.queue_sleep_preset_short, m));
                b.queueSleep.setContentDescription(getString(R.string.player_sleep_timer_min, m));
                break;
            case END_OF_TRACK:
                b.queueSleep.setText(R.string.queue_sleep_chip_end);
                b.queueSleep.setContentDescription(getString(R.string.player_sleep_timer) + ", "
                        + getString(R.string.queue_sleep_chip_end));
                break;
            default:
                b.queueSleep.setText(null);
                b.queueSleep.setContentDescription(getString(R.string.player_sleep_timer));
        }
    }

    private void showSleepTimerPicker() {
        Locale loc = getResources().getConfiguration().getLocales().get(0);
        NumberFormat nf = NumberFormat.getIntegerInstance(loc);
        int[] presets = QueueViewModel.SLEEP_PRESETS_MIN;
        CharSequence[] items = new CharSequence[presets.length + 2];
        for (int i = 0; i < presets.length; i++) items[i] = getString(R.string.queue_sleep_preset, nf.format(presets[i]));
        items[presets.length] = getString(R.string.queue_sleep_end_of_track);
        items[presets.length + 1] = getString(R.string.queue_sleep_off);
        // The Settings "default" is pre-marked; a tap still applies immediately.
        int preselected = presets.length; // 0 minutes = end of track
        int defaultMinutes = ((MusicPlayerApp) requireActivity().getApplication()).getContainer().settings().sleepTimerMinutes().getValue();
        for (int i = 0; i < presets.length; i++) if (presets[i] == defaultMinutes) preselected = i;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.player_sleep_timer)
                .setSingleChoiceItems(items, preselected, (d, which) -> {
                    if (which < presets.length) vm.setSleepTimer(presets[which]);
                    else if (which == presets.length) vm.setSleepTimerEndOfTrack();
                    else vm.cancelSleepTimer();
                    d.dismiss();
                })
                .show();
    }

    private void removeWithUndo(int upNextPos) {
        QueueViewModel.Removed r = vm.remove(upNextPos);
        if (r != null) snackbar(getString(R.string.queue_removed, r.track().title()), () -> vm.undoRemove(r));
    }

    private void snackbar(String text, Runnable undo) {
        Snackbar.make(b.queueRoot, text, Snackbar.LENGTH_LONG)
                .setAction(R.string.queue_undo, v -> undo.run())
                .show();
    }

    private final class TouchCallback extends ItemTouchHelper.Callback {
        @Override public int getMovementFlags(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder h) {
            if (adapter.isCurrentRow(h.getBindingAdapterPosition())) return 0;
            int swipe = rv.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? ItemTouchHelper.RIGHT : ItemTouchHelper.LEFT;
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, swipe);
        }

        @Override public boolean isLongPressDragEnabled() { return true; }

        @Override public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from,
                                        @NonNull RecyclerView.ViewHolder to) {
            int f = from.getBindingAdapterPosition(), t = to.getBindingAdapterPosition();
            if (adapter.isCurrentRow(t)) return false;
            if (dragFrom < 0) dragFrom = f;
            dragTo = t;
            adapter.moveLocal(f, t);
            return true;
        }

        @Override public void onSwiped(@NonNull RecyclerView.ViewHolder h, int direction) {
            removeWithUndo(adapter.toUpNext(h.getBindingAdapterPosition()));
        }

        @Override public void onSelectedChanged(@Nullable RecyclerView.ViewHolder h, int state) {
            super.onSelectedChanged(h, state);
            if (state == ItemTouchHelper.ACTION_STATE_DRAG && h != null) {
                dragging = true;
                h.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                h.itemView.animate().scaleX(1.025f).scaleY(1.025f).translationZ(14 * getResources()
                        .getDisplayMetrics().density).setDuration(150).start();
            }
        }

        @Override public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder h) {
            super.clearView(rv, h);
            h.itemView.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(150).start();
            QueueAdapter.Holder qh = (QueueAdapter.Holder) h;
            qh.row.setTranslationX(0f);
            qh.removeBg.setVisibility(View.INVISIBLE);
            boolean wasDragging = dragging;
            dragging = false;
            if (wasDragging && dragFrom >= 0 && dragTo >= 0 && dragFrom != dragTo) {
                vm.move(adapter.toUpNext(dragFrom), adapter.toUpNext(dragTo));
            } else if (pending != null) {
                render(pending);
            }
            pending = null;
            dragFrom = dragTo = -1;
        }

        @Override public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder h,
                                          float dX, float dY, int state, boolean active) {
            if (state == ItemTouchHelper.ACTION_STATE_SWIPE) {
                QueueAdapter.Holder qh = (QueueAdapter.Holder) h;
                qh.removeBg.setVisibility(dX != 0f ? View.VISIBLE : View.INVISIBLE);
                qh.row.setTranslationX(dX); // the red "Remove" layer stays put underneath
                return;
            }
            super.onChildDraw(c, rv, h, dX, dY, state, active);
        }
    }

    @Override public void onDestroyView() {
        b = null;
        super.onDestroyView();
    }
}
