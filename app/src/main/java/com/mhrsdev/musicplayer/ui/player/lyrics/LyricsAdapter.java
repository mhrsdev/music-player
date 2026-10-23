package com.mhrsdev.musicplayer.ui.player.lyrics;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mhrsdev.musicplayer.domain.model.LyricLine;
import com.mhrsdev.musicplayer.domain.model.Lyrics;

import java.util.Collections;
import java.util.List;

/**
 * Lyric lines as {@link LyricLineView}s. The active index decides each line's distance (emphasis, alpha, blur); only
 * lines whose distance changed are rebound, and the per-frame word sweep is driven by {@link LyricsTicker}, which
 * invalidates the active line's view alone. Tap = seek (synced lyrics).
 */
public class LyricsAdapter extends RecyclerView.Adapter<LyricsAdapter.Holder> {
    public interface OnLineClick { void onLine(LyricLine line); }

    /** Lines farther than this from the active one share the same look, so they are not rebound on line changes. */
    static final int FALLOFF_LINES = 6;
    private static final Object PAYLOAD_ACTIVE = new Object();
    private List<LyricLine> lines = Collections.emptyList();
    private boolean synced;
    private int active = -1;
    private final OnLineClick click;
    private final LyricLineView.Clock clock;
    private int textColor = 0xFFFFFFFF;
    private float textSp = 26f;
    private boolean blur, decorative = true;

    public LyricsAdapter(OnLineClick click, LyricLineView.Clock clock) {
        this.click = click;
        this.clock = clock;
    }

    @android.annotation.SuppressLint("NotifyDataSetChanged") // whole new song text
    public void setLyrics(Lyrics l) {
        lines = l.lines();
        synced = l.synced();
        active = -1;
        notifyDataSetChanged();
    }

    public LyricLine lineAt(int i) { return lines.get(i); }

    /** Rebinds only the lines whose distance-based look changed. */
    public void setActive(int index) {
        if (index == active) return;
        int old = active;
        active = index;
        int n = lines.size();
        int from = Math.max(0, Math.min(old, index) - FALLOFF_LINES);
        int to = Math.min(n - 1, Math.max(old, index) + FALLOFF_LINES);
        if (old < 0 || index < 0) { from = 0; to = n - 1; }
        if (from <= to) notifyItemRangeChanged(from, to - from + 1, PAYLOAD_ACTIVE);
    }

    public int getActive() { return active; }

    /** White on the dark player, onSurface on the light one (design 15). */
    @android.annotation.SuppressLint("NotifyDataSetChanged")
    public void setTextColor(int color) {
        if (color == textColor) return;
        textColor = color;
        notifyDataSetChanged();
    }

    /** A−/A+ (SettingsStore lyrics text size). */
    @android.annotation.SuppressLint("NotifyDataSetChanged")
    public void setTextSizeSp(float sp) {
        if (sp == textSp) return;
        textSp = sp;
        notifyDataSetChanged();
    }

    /** Blur (API 31+, not in battery saver / reduce motion / low RAM) and decorative lift/glow. */
    @android.annotation.SuppressLint("NotifyDataSetChanged")
    public void setEffects(boolean blur, boolean decorative) {
        if (this.blur == blur && this.decorative == decorative) return;
        this.blur = blur;
        this.decorative = decorative;
        notifyDataSetChanged();
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LyricLineView v = new LyricLineView(parent.getContext());
        v.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return new Holder(v);
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) { onBindViewHolder(h, position); return; }
        h.view.setDistance(distance(position), true);
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        LyricLine line = lines.get(position);
        h.view.bind(line, textSp, textColor, clock);
        h.view.setEffects(blur && synced, decorative);
        h.view.setOnClickListener(synced ? v -> click.onLine(line) : null);
        h.view.setClickable(synced);
        h.view.setDistance(distance(position), false);
    }

    /** Signed distance from the active line (past lines negative); unsynced lyrics are all "active" (no falloff). */
    float distance(int position) {
        if (!synced) return 0f;
        if (active < 0) return Math.min(FALLOFF_LINES, position + 1);
        int d = position - active;
        return Math.max(-FALLOFF_LINES, Math.min(FALLOFF_LINES, d));
    }

    @Override public int getItemCount() { return lines.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        final LyricLineView view;
        Holder(LyricLineView v) {
            super(v);
            view = v;
        }
    }
}
