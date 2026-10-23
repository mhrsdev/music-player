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
        vm = new ViewModelProvider(this, QueueViewModel.factory(c.player, c.library)).get(QueueViewModel.class);

        adapter = new QueueAdapter(new QueueAdapter.Actions() {
            @Override public void onPlay(int pos) { vm.play(pos); }
            @Override public void onStartDrag(RecyclerView.ViewHolder h) { touchHelper.startDrag(h); }
            @Override public void onMoveA11y(int from, int to) { vm.move(from, to); }
            @Override public void onRemove(int pos) { removeWithUndo(pos); }
            @Override public void onCurrent() { dismissAllowingStateLoss(); }
        });
        b.queueList.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.queueList.setAdapter(adapter);
        touchHelper = new ItemTouchHelper(new TouchCallback());
        touchHelper.attachToRecyclerView(b.queueList);

        b.queueShuffle.setOnClickListener(v -> {
            vm.shuffle();
            snackbar(getString(R.string.queue_shuffled), vm::undoShuffle);
        });
        b.queueSleep.setOnClickListener(v -> SleepTimerSheet.show(getParentFragmentManager()));
        b.queueMore.setOnClickListener(this::showMenu);

        vm.getUi().observe(getViewLifecycleOwner(), this::render);
    }

    private void render(QueueViewModel.UiState ui) {
        if (dragging) { pending = ui; return; } // don't fight the user's finger; apply on drop
        Locale loc = getResources().getConfiguration().getLocales().get(0);
        NumberFormat nf = NumberFormat.getIntegerInstance(loc);
        adapter.submit(ui.current(), ui.upNext());
        b.queueEmpty.setVisibility(ui.current() == null ? View.VISIBLE : View.GONE);
        int mins = ui.minutesLeft();
        String left = nf.format(mins), hh = nf.format(mins / 60), mm = nf.format(mins % 60);
        boolean hours = mins >= 60;
        b.queuePlayingFrom.setText(ui.playingFrom() == null
                ? (hours ? getString(R.string.queue_left_h, hh, mm) : getString(R.string.queue_min_left, left))
                : hours ? getString(R.string.queue_playing_from_h, ui.playingFrom(), hh, mm)
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

    private void showMenu(View anchor) {
        android.widget.PopupMenu menu = new android.widget.PopupMenu(requireContext(), anchor);
        boolean hasNext = adapter.upNextCount() > 0;
        menu.getMenu().add(0, 1, 0, R.string.queue_jump_current);
        menu.getMenu().add(0, 2, 1, R.string.queue_save_playlist).setEnabled(adapter.getItemCount() > 0);
        menu.getMenu().add(0, 3, 2, R.string.queue_dedupe).setEnabled(hasNext);
        menu.getMenu().add(0, 4, 3, R.string.queue_clear).setEnabled(hasNext);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: b.queueList.smoothScrollToPosition(0); break;
                case 2: askPlaylistName(); break;
                case 3: {
                    java.util.List<QueueViewModel.Removed> r = vm.removeDuplicates();
                    if (r.isEmpty()) snackbar(getString(R.string.queue_no_duplicates), null);
                    else snackbar(getString(R.string.queue_deduped,
                            NumberFormat.getIntegerInstance(locale()).format(r.size())), () -> vm.undoRemoveAll(r));
                    break;
                }
                default: {
                    java.util.List<QueueViewModel.Removed> r = vm.clearUpNext();
                    if (!r.isEmpty()) snackbar(getString(R.string.queue_cleared), () -> vm.undoRemoveAll(r));
                }
            }
            return true;
        });
        menu.show();
    }

    private void askPlaylistName() {
        android.widget.EditText name = new android.widget.EditText(requireContext());
        name.setSingleLine(true);
        name.setText(R.string.queue_save_playlist_default);
        name.selectAll();
        name.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        android.widget.FrameLayout box = new android.widget.FrameLayout(requireContext());
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(name);
        android.content.Context app = requireContext().getApplicationContext();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.queue_save_playlist)
                .setView(box)
                .setNegativeButton(R.string.queue_cancel, null)
                .setPositiveButton(R.string.queue_save, (d, w) -> vm.saveAsPlaylist(name.getText().toString(), p ->
                        android.widget.Toast.makeText(app, p == null ? app.getString(R.string.queue_save_failed)
                                : app.getString(R.string.queue_saved, p.name()), android.widget.Toast.LENGTH_SHORT).show()))
                .show();
    }

    private Locale locale() { return getResources().getConfiguration().getLocales().get(0); }

    private void removeWithUndo(int upNextPos) {
        QueueViewModel.Removed r = vm.remove(upNextPos);
        if (r != null) snackbar(getString(R.string.queue_removed, r.track().title()), () -> vm.undoRemove(r));
    }

    private void snackbar(String text, @Nullable Runnable undo) {
        Snackbar s = Snackbar.make(b.queueRoot, text, Snackbar.LENGTH_LONG);
        if (undo != null) s.setAction(R.string.queue_undo, v -> undo.run());
        s.show();
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
