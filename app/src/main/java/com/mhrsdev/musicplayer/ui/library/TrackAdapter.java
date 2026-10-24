package com.mhrsdev.musicplayer.ui.library;

import com.mhrsdev.musicplayer.ui.widget.cover.CoverSpec;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.databinding.LibraryRowTrackBinding;
import com.mhrsdev.musicplayer.domain.model.Track;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Track rows for the library lists (art 52 dp / title / artist / duration / overflow). Built for 10k-row lists:
 * DiffUtil runs on a background thread (thread exits when idle), listeners are created once per holder, and
 * selection changes are dispatched as payloads that only flip the row's check state. In reorder mode a drag
 * handle is shown and {@link #moveLocal} moves rows without waiting for the database.
 */
public class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.Holder> {

    public interface Listener {
        void onTrackClick(List<Track> list, int position);

        void onTrackMore(View anchor, Track track);

        /** Long-press: start (or extend) multi-select. */
        default void onTrackLongClick(Track track, int position) { }

        /** Drag handle touched (reorder mode only). */
        default void onStartDrag(RecyclerView.ViewHolder holder) { }
    }

    private static final Object PAYLOAD_SELECTION = new Object();
    private static final Executor DIFF;

    static {
        ThreadPoolExecutor e = new com.mhrsdev.musicplayer.diag.SafeExecutor(1, 1, 20, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), com.mhrsdev.musicplayer.BuildConfig.DEBUG);
        e.allowCoreThreadTimeOut(true);
        DIFF = e;
    }

    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private List<Track> items = Collections.emptyList();
    private Set<String> selected = Collections.emptySet();
    private boolean reorderable;
    private int generation;
    @Nullable private String highlight;
    @Nullable private Runnable onCommitted;

    public TrackAdapter(Listener listener) {
        this.listener = listener;
    }

    /** Text to bold inside titles (search match highlight). */
    public void setHighlight(@Nullable String query) {
        highlight = query;
    }

    /** Shows drag handles (playlist detail). */
    public void setReorderable(boolean reorderable) {
        if (this.reorderable == reorderable) return;
        this.reorderable = reorderable;
        notifyItemRangeChanged(0, items.size());
    }

    /** Called on the main thread after each submitted list is applied (e.g. to refresh the scroller index). */
    public void setOnCommitted(@Nullable Runnable r) {
        onCommitted = r;
    }

    public List<Track> getCurrentList() {
        return Collections.unmodifiableList(items);
    }

    public Track getItem(int position) {
        return items.get(position);
    }

    /** Diffs off the main thread; a newer submit wins over an older one still computing. */
    @SuppressLint("NotifyDataSetChanged")
    public void submitList(@Nullable List<Track> list) {
        List<Track> next = list == null ? Collections.emptyList() : new ArrayList<>(list);
        int gen = ++generation;
        List<Track> old = items;
        if (old.isEmpty() || next.isEmpty()) {
            items = next;
            notifyDataSetChanged();
            if (onCommitted != null) onCommitted.run();
            return;
        }
        DIFF.execute(() -> {
            DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return old.size(); }

                @Override public int getNewListSize() { return next.size(); }

                @Override public boolean areItemsTheSame(int o, int n) { return old.get(o).id().equals(next.get(n).id()); }

                @Override public boolean areContentsTheSame(int o, int n) { return old.get(o).equals(next.get(n)); }
            }, next.size() < 2000); // move detection is O(n^2)-ish; skip it for huge lists
            main.post(() -> {
                if (gen != generation) return;
                items = next;
                diff.dispatchUpdatesTo(this);
                if (onCommitted != null) onCommitted.run();
            });
        });
    }

    /** Moves a row immediately (drag in progress); the caller persists the final move on drop. */
    public void moveLocal(int from, int to) {
        if (from == to || from < 0 || to < 0 || from >= items.size() || to >= items.size()) return;
        List<Track> copy = new ArrayList<>(items);
        copy.add(to, copy.remove(from));
        items = copy;
        generation++; // a list computed before the move is stale
        notifyItemMoved(from, to);
    }

    /** Flips the check state of the rows whose membership changed; no full rebind. */
    public void setSelection(Set<String> ids) {
        Set<String> old = selected;
        selected = ids;
        if (old.isEmpty() != ids.isEmpty()) { // entering / leaving the mode changes every row's trailing icons
            notifyItemRangeChanged(0, items.size(), PAYLOAD_SELECTION);
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            String id = items.get(i).id();
            if (old.contains(id) != ids.contains(id)) notifyItemChanged(i, PAYLOAD_SELECTION);
        }
    }

    @Override public int getItemCount() {
        return items.size();
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LibraryRowTrackBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains(PAYLOAD_SELECTION) && payloads.size() == 1) {
            bindSelection(h, items.get(position));
        } else {
            onBindViewHolder(h, position);
        }
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        Track t = items.get(position);
        Locale locale = h.itemView.getResources().getConfiguration().getLocales().get(0);
        com.mhrsdev.musicplayer.ui.widget.ScriptFont.setText(h.b.title, Highlight.apply(t.title(), highlight));
        com.mhrsdev.musicplayer.ui.widget.ScriptFont.setText(h.b.subtitle, t.artistName());
        h.b.duration.setText(Format.duration(t.durationMs(), locale));
        h.b.explicit.setVisibility(t.explicit() ? View.VISIBLE : View.GONE);
        h.title = t.title(); // the "More options for <title>" label is built on demand (TalkBack), not by a format() per bind
        h.b.drag.setVisibility(reorderable ? View.VISIBLE : View.GONE);
        Art.bind(h.b.art, t.artworkUri(), t.placeholderSeed(), 12, false, CoverSpec.forTrack(t));
        bindSelection(h, t);
    }

    private void bindSelection(Holder h, Track t) {
        boolean on = selected.contains(t.id());
        h.itemView.setActivated(on);
        h.b.check.setVisibility(on ? View.VISIBLE : View.GONE);
        boolean mode = !selected.isEmpty();
        h.b.more.setVisibility(mode ? View.INVISIBLE : View.VISIBLE);
        h.b.drag.setVisibility(reorderable && !mode ? View.VISIBLE : View.GONE);
        h.itemView.setSelected(on); // TalkBack announces "selected"
    }

    @Override public void onViewRecycled(@NonNull Holder h) {
        h.b.art.clear(); // drops the cover and the pending load: nothing is applied to (or rendered for) a pooled row
    }

    class Holder extends RecyclerView.ViewHolder {
        final LibraryRowTrackBinding b;
        @Nullable String title;

        @SuppressLint("ClickableViewAccessibility")
        Holder(LibraryRowTrackBinding b) {
            super(b.getRoot());
            this.b = b;
            b.more.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override public void onInitializeAccessibilityNodeInfo(View host,
                        android.view.accessibility.AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    if (title != null) info.setContentDescription(host.getContext().getString(R.string.library_more, title));
                }
            });
            itemView.setOnClickListener(v -> {
                int p = getBindingAdapterPosition();
                if (p != RecyclerView.NO_POSITION) listener.onTrackClick(getCurrentList(), p);
            });
            itemView.setOnLongClickListener(v -> {
                int p = getBindingAdapterPosition();
                if (p == RecyclerView.NO_POSITION) return false;
                listener.onTrackLongClick(items.get(p), p);
                return true;
            });
            b.more.setOnClickListener(v -> {
                int p = getBindingAdapterPosition();
                if (p != RecyclerView.NO_POSITION) listener.onTrackMore(v, items.get(p));
            });
            b.drag.setOnTouchListener((v, e) -> {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) listener.onStartDrag(this);
                return false;
            });
        }
    }
}
