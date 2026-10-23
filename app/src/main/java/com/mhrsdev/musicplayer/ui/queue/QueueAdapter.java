package com.mhrsdev.musicplayer.ui.queue;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.ui.player.PlayerFormat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Row 0 is the current track ("Now playing", highlighted, fixed); rows 1.. are Up next. The adapter keeps a local copy
 * that is reordered live while dragging; the move is committed to the ViewModel on drop.
 */
public final class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.Holder> {
    public interface Actions {
        void onPlay(int upNextPos);
        void onStartDrag(RecyclerView.ViewHolder h);
        void onMoveA11y(int fromUpNext, int toUpNext);
        void onRemove(int upNextPos);
    }

    @Nullable private Track current;
    private final List<Track> upNext = new ArrayList<>();
    private final Actions actions;

    QueueAdapter(Actions actions) { this.actions = actions; }

    @SuppressLint("NotifyDataSetChanged")
    void submit(@Nullable Track current, List<Track> next) {
        this.current = current;
        upNext.clear();
        upNext.addAll(next);
        notifyDataSetChanged();
    }

    int upNextCount() { return upNext.size(); }

    /** Live reorder during a drag (adapter positions). */
    void moveLocal(int from, int to) {
        Collections.swap(upNext, toUpNext(from), toUpNext(to));
        notifyItemMoved(from, to);
    }

    boolean isCurrentRow(int adapterPos) { return current != null && adapterPos == 0; }

    private int offset() { return current == null ? 0 : 1; }

    int toUpNext(int adapterPos) { return adapterPos - offset(); }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup p, int viewType) {
        return new Holder(LayoutInflater.from(p.getContext()).inflate(R.layout.queue_item, p, false));
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        boolean isCurrent = isCurrentRow(position);
        Track t = isCurrent ? current : upNext.get(toUpNext(position));
        Locale loc = h.itemView.getResources().getConfiguration().getLocales().get(0);
        h.title.setText(t.title());
        h.artist.setText(isCurrent ? h.itemView.getContext().getString(R.string.queue_now_playing) : t.artistName());
        h.duration.setText(PlayerFormat.time(t.durationMs(), loc));
        h.art.bind(t);
        h.row.setActivated(isCurrent);
        h.title.setTextColor(isCurrent ? 0xFFD4E3FF : 0xFFE2E2E9);
        h.drag.setVisibility(isCurrent ? View.INVISIBLE : View.VISIBLE);
        h.drag.setContentDescription(h.itemView.getContext().getString(R.string.queue_reorder, t.title()));
        h.row.setTranslationX(0f);
        h.removeBg.setVisibility(View.INVISIBLE);
        h.row.setOnClickListener(isCurrent ? null : v -> {
            int p = h.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION) actions.onPlay(toUpNext(p));
        });
        h.drag.setOnTouchListener(isCurrent ? null : (v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) actions.onStartDrag(h);
            return false;
        });
        bindA11y(h, isCurrent);
    }

    /** Accessible alternative to drag and swipe (DESIGN §8): Move up / Move down / Remove custom actions. */
    private void bindA11y(Holder h, boolean isCurrent) {
        View v = h.row;
        ViewCompat.removeAccessibilityAction(v, R.id.queue_a11y_up);
        ViewCompat.removeAccessibilityAction(v, R.id.queue_a11y_down);
        ViewCompat.removeAccessibilityAction(v, R.id.queue_a11y_remove);
        if (isCurrent) return;
        android.content.Context c = v.getContext();
        ViewCompat.replaceAccessibilityAction(v, new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                R.id.queue_a11y_up, c.getString(R.string.queue_move_up)), c.getString(R.string.queue_move_up), (view, args) -> {
            int p = toUpNext(h.getBindingAdapterPosition());
            if (p > 0) actions.onMoveA11y(p, p - 1);
            return true;
        });
        ViewCompat.replaceAccessibilityAction(v, new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                R.id.queue_a11y_down, c.getString(R.string.queue_move_down)), c.getString(R.string.queue_move_down), (view, args) -> {
            int p = toUpNext(h.getBindingAdapterPosition());
            if (p >= 0 && p < upNext.size() - 1) actions.onMoveA11y(p, p + 1);
            return true;
        });
        ViewCompat.replaceAccessibilityAction(v, new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                R.id.queue_a11y_remove, c.getString(R.string.queue_remove)), c.getString(R.string.queue_remove), (view, args) -> {
            int p = toUpNext(h.getBindingAdapterPosition());
            if (p >= 0) actions.onRemove(p);
            return true;
        });
    }

    @Override public int getItemCount() { return upNext.size() + offset(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final View row, drag;
        final TextView title, artist, duration, removeBg;
        final com.mhrsdev.musicplayer.ui.widget.ArtworkView art;
        Holder(View v) {
            super(v);
            row = v.findViewById(R.id.queue_row);
            drag = v.findViewById(R.id.queue_drag);
            title = v.findViewById(R.id.queue_title);
            artist = v.findViewById(R.id.queue_artist);
            duration = v.findViewById(R.id.queue_duration);
            removeBg = v.findViewById(R.id.queue_remove_bg);
            art = v.findViewById(R.id.queue_art);
            art.setCornerRadiusDp(12);
        }
    }
}
