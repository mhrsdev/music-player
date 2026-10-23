package com.mhrsdev.musicplayer.ui.library;

import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.databinding.U2SheetBinding;
import com.mhrsdev.musicplayer.databinding.U2SheetRowBinding;
import com.mhrsdev.musicplayer.ui.theme.Tokens;
import com.mhrsdev.musicplayer.ui.widget.ScriptFont;
import java.util.ArrayList;
import java.util.List;

/**
 * Base of the app's action sheets (track menu, sort, playlist menu, details): rounded sheet with a grab handle,
 * an optional cover + title header, and rows of icon + label. Subclasses describe header and rows in
 * {@link #build}; a sheet whose state cannot be rebuilt after process death simply closes ({@link #build} false).
 */
public abstract class ActionSheet extends BottomSheetDialogFragment {

    /** One row. {@code checked} shows a trailing check (sort sheet). */
    protected record Row(@DrawableRes int icon, CharSequence label, boolean destructive, boolean checked,
            Runnable action) {
        static Row of(@DrawableRes int icon, CharSequence label, Runnable action) {
            return new Row(icon, label, false, false, action);
        }
    }

    /** Header: covers (0..4), seed, title, subtitle; {@code covers == null} hides the art. */
    protected record Header(@Nullable List<String> covers, int seed, CharSequence title, @Nullable CharSequence subtitle) { }

    private U2SheetBinding b;

    /** Fills {@code rows}; returns the header, or null to hide it. */
    @Nullable protected abstract Header build(List<Row> rows);

    /** Optional primary actions shown as a row of tiles above the rows. */
    protected void buildTiles(List<Row> tiles) { }

    /** False when the sheet's arguments are missing (it then dismisses itself). */
    protected boolean hasState() {
        return getArguments() != null;
    }

    @NonNull @Override public Dialog onCreateDialog(@Nullable Bundle state) {
        BottomSheetDialog d = (BottomSheetDialog) super.onCreateDialog(state);
        d.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        d.getBehavior().setSkipCollapsed(true);
        return d;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = U2SheetBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        if (!hasState()) {
            dismissAllowingStateLoss();
            return;
        }
        render();
    }

    /** Rebuilds header and rows (e.g. after the data behind them changed). */
    protected final void render() {
        if (b == null) return;
        List<Row> rows = new ArrayList<>();
        Header h = build(rows);
        b.sheetHeader.setVisibility(h == null ? View.GONE : View.VISIBLE);
        if (h != null) {
            b.sheetArt.setVisibility(h.covers() == null ? View.GONE : View.VISIBLE);
            if (h.covers() != null) {
                b.sheetArt.setCornerRadiusDp(12);
                b.sheetArt.setCovers(h.covers(), h.seed());
            }
            ScriptFont.setText(b.sheetTitle, h.title());
            b.sheetSubtitle.setVisibility(h.subtitle() == null ? View.GONE : View.VISIBLE);
            if (h.subtitle() != null) ScriptFont.setText(b.sheetSubtitle, h.subtitle());
        }
        b.sheetRows.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        List<Row> tiles = new ArrayList<>();
        buildTiles(tiles);
        b.sheetTiles.removeAllViews();
        b.sheetTiles.setVisibility(tiles.isEmpty() ? View.GONE : View.VISIBLE);
        for (Row tile : tiles) {
            com.mhrsdev.musicplayer.databinding.U2SheetTileBinding t =
                    com.mhrsdev.musicplayer.databinding.U2SheetTileBinding.inflate(inflater, b.sheetTiles, false);
            t.tileIcon.setImageResource(tile.icon());
            t.tileLabel.setText(tile.label());
            t.getRoot().setOnClickListener(v -> {
                dismissAllowingStateLoss();
                tile.action().run();
            });
            b.sheetTiles.addView(t.getRoot());
        }
        int error = Tokens.color(requireContext(), R.attr.mpError);
        for (Row row : rows) {
            U2SheetRowBinding r = U2SheetRowBinding.inflate(inflater, b.sheetRows, false);
            if (row.icon() == 0) r.icon.setVisibility(View.GONE); else r.icon.setImageResource(row.icon());
            if (row.icon() == 0) r.label.setPaddingRelative(0, 0, 0, 0);
            if (row.checked()) r.label.setTextColor(Tokens.primary(requireContext()));
            r.label.setText(row.label());
            r.trailing.setVisibility(row.checked() ? View.VISIBLE : View.GONE);
            r.getRoot().setSelected(row.checked());
            if (row.destructive()) {
                r.label.setTextColor(error);
                r.icon.setColorFilter(error);
            }
            r.getRoot().setOnClickListener(v -> {
                dismissAllowingStateLoss();
                row.action().run();
            });
            b.sheetRows.addView(r.getRoot());
        }
    }

    @Override public void onStart() {
        super.onStart();
        View v = getView();
        if (v != null && v.getParent() instanceof View parent) parent.setBackgroundColor(Color.TRANSPARENT);
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
