package com.mhrsdev.musicplayer.ui.player.lyrics;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.domain.model.LyricLine;
import com.mhrsdev.musicplayer.domain.model.Lyrics;

import java.util.Collections;
import java.util.List;

/** Centered lyric lines; active line full alpha + scale 1, others dimmed (DESIGN §8 alphas). Tap = seek (synced). */
public final class LyricsAdapter extends RecyclerView.Adapter<LyricsAdapter.Holder> {
    public interface OnLineClick { void onLine(LyricLine line); }

    private static final Object PAYLOAD_ACTIVE = new Object();
    private List<LyricLine> lines = Collections.emptyList();
    private boolean synced;
    private int active = -1;
    private final OnLineClick click;
    private int textColor = 0xFFFFFFFF;

    public LyricsAdapter(OnLineClick click) { this.click = click; }

    @android.annotation.SuppressLint("NotifyDataSetChanged") // whole new song text
    public void setLyrics(Lyrics l) {
        lines = l.lines();
        synced = l.synced();
        active = -1;
        notifyDataSetChanged();
    }

    /** Rebinds only the two lines whose emphasis changed. */
    public void setActive(int index) {
        if (index == active) return;
        int old = active;
        active = index;
        if (old >= 0 && old < lines.size()) notifyItemChanged(old, PAYLOAD_ACTIVE);
        if (index >= 0 && index < lines.size()) notifyItemChanged(index, PAYLOAD_ACTIVE);
        // past/upcoming alpha of the lines between also depends on the active index
        int from = Math.min(old, index), to = Math.max(old, index);
        for (int i = Math.max(0, from + 1); i < to && i < lines.size(); i++) notifyItemChanged(i, PAYLOAD_ACTIVE);
    }

    public int getActive() { return active; }

    /** White on the dark player, onSurface on the light one (design 15). */
    @android.annotation.SuppressLint("NotifyDataSetChanged")
    public void setTextColor(int color) {
        if (color == textColor) return;
        textColor = color;
        notifyDataSetChanged();
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.lyrics_line_item, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) { onBindViewHolder(h, position); return; }
        applyEmphasis(h, position, true);
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        LyricLine line = lines.get(position);
        h.text.setText(line.text());
        h.text.setTextColor(textColor);
        h.text.setOnClickListener(synced ? v -> click.onLine(line) : null);
        h.text.setClickable(synced);
        applyEmphasis(h, position, false);
    }

    private void applyEmphasis(Holder h, int position, boolean animate) {
        float alpha = LyricsScrollPolicy.alphaFor(position, active, synced);
        float scale = !synced || position == active ? 1f : 0.94f;
        View v = h.text;
        if (animate) {
            v.animate().alpha(alpha).scaleX(scale).scaleY(scale).setDuration(250).start();
        } else {
            v.animate().cancel();
            v.setAlpha(alpha);
            v.setScaleX(scale);
            v.setScaleY(scale);
        }
    }

    @Override public int getItemCount() { return lines.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView text;
        Holder(View v) {
            super(v);
            text = (TextView) v;
        }
    }
}
