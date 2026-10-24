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
public class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.Holder> {
    public interface Actions {
        void onPlay(int upNextPos);
        void onStartDrag(RecyclerView.ViewHolder h);
        void onMoveA11y(int fromUpNext, int toUpNext);
        void onRemove(int upNextPos);
        /** Tap on the "Now playing" row: back to the player. */
        void onCurrent();
    }

    @Nullable private Track current;
    private final List<Track> upNext = new ArrayList<>();
    private final Actions actions;

    QueueAdapter(Actions actions) { this.actions = actions; }

    @SuppressLint("NotifyDataSetChanged")
    void submit(@Nullable Track current, List<Track> next) {
        // The sheet re-renders when only its "N min left" label changed (once a minute while playing): do not
        // rebind and re-lay out every row for that.
        if (java.util.Objects.equals(current, this.current) && next.equals(upNext)) return;
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
        Holder h = new Holder(LayoutInflater.from(p.getContext()).inflate(R.layout.queue_item, p, false));
        wire(h);
        addA11yActions(h);
        return h;
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        boolean isCurrent = isCurrentRow(position);
        Track t = isCurrent ? current : upNext.get(toUpNext(position));
        Locale loc = h.itemView.getResources().getConfiguration().getLocales().get(0);
        h.title.setText(t.title());
        h.artist.setText(isCurrent ? h.nowPlaying : t.artistName());
        h.duration.setText(PlayerFormat.time(t.durationMs(), loc));
        h.art.bind(t);
        h.row.setActivated(isCurrent);
        h.title.setTextColor(isCurrent ? h.onContainer : h.onSurface);
        h.artist.setTextColor(isCurrent ? h.onContainer : h.onSurfaceVariant);
        h.drag.setVisibility(isCurrent ? View.INVISIBLE : View.VISIBLE);
        h.boundTitle = t.title();
        h.row.setTranslationX(0f);
        h.removeBg.setVisibility(View.INVISIBLE);
        h.setCurrent(isCurrent);
    }

    /**
     * Listeners, accessibility actions and labels are made once per holder and read the bound row when used; the
     * TalkBack strings are formatted on demand. They were rebuilt on every bind (three actions with their lambdas,
     * three getString, two theme colour look-ups and two NumberFormats per row: ~60 KB per bind on the JVM).
     */
    @SuppressLint("ClickableViewAccessibility")
    private void wire(Holder h) {
        h.row.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p == RecyclerView.NO_POSITION) return;
            if (isCurrentRow(p)) actions.onCurrent(); else actions.onPlay(toUpNext(p));
        });
        h.drag.setOnTouchListener((v, e) -> {
            if (h.current) return false;
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) actions.onStartDrag(h);
            return false;
        });
        h.drag.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                if (h.boundTitle != null) info.setContentDescription(host.getContext().getString(R.string.queue_reorder, h.boundTitle));
            }
        });
        h.row.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                if (h.current && h.boundTitle != null) {
                    info.setContentDescription(host.getContext().getString(R.string.queue_now_playing_cd, h.boundTitle));
                }
            }
        });
    }

    /** Accessible alternative to drag and swipe (DESIGN §8): Move up / Move down / Remove custom actions. */
    private void addA11yActions(Holder h) {
        View v = h.row;
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

    class Holder extends RecyclerView.ViewHolder {
        final View row, drag;
        final TextView title, artist, duration, removeBg;
        final com.mhrsdev.musicplayer.ui.widget.ArtworkView art;
        final int onContainer, onSurface, onSurfaceVariant;
        final String nowPlaying;
        @Nullable String boundTitle;
        /** The "Now playing" row: no drag, no move / remove actions. */
        boolean current;

        Holder(View v) {
            super(v);
            android.content.Context c = v.getContext();
            onContainer = com.mhrsdev.musicplayer.ui.theme.Tokens.color(c, R.attr.mpOnPrimaryContainer);
            onSurface = com.mhrsdev.musicplayer.ui.theme.Tokens.color(c, R.attr.mpOnSurface);
            onSurfaceVariant = com.mhrsdev.musicplayer.ui.theme.Tokens.color(c, R.attr.mpOnSurfaceVariant);
            nowPlaying = c.getString(R.string.queue_now_playing);
            row = v.findViewById(R.id.queue_row);
            drag = v.findViewById(R.id.queue_drag);
            title = v.findViewById(R.id.queue_title);
            artist = v.findViewById(R.id.queue_artist);
            duration = v.findViewById(R.id.queue_duration);
            removeBg = v.findViewById(R.id.queue_remove_bg);
            art = v.findViewById(R.id.queue_art);
            art.setCornerRadiusDp(12);
        }

        void setCurrent(boolean isCurrent) {
            if (current == isCurrent) return;
            current = isCurrent;
            if (isCurrent) {
                ViewCompat.removeAccessibilityAction(row, R.id.queue_a11y_up);
                ViewCompat.removeAccessibilityAction(row, R.id.queue_a11y_down);
                ViewCompat.removeAccessibilityAction(row, R.id.queue_a11y_remove);
            } else {
                addA11yActions(this);
            }
        }
    }
}
