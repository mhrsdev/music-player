package com.mhrsdev.musicplayer.ui.library;

import android.content.Context;
import android.graphics.Outline;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import androidx.annotation.Nullable;
import com.mhrsdev.musicplayer.ui.widget.ArtworkView;
import java.util.List;

/**
 * Playlist cover: a 2x2 mosaic of the first four distinct covers, or one cover (or the seeded gradient) when there
 * are fewer. Four plain ArtworkViews clipped by one rounded outline, so it shares the bitmap cache and costs nothing
 * per frame.
 */
public class MosaicArtView extends ViewGroup {
    private final ArtworkView[] tiles = new ArtworkView[4];
    private float radiusPx;
    private float radiusDp;
    private boolean mosaic;

    public MosaicArtView(Context context) {
        this(context, null);
    }

    public MosaicArtView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        for (int i = 0; i < 4; i++) {
            tiles[i] = new ArtworkView(context);
            tiles[i].setCornerRadiusDp(0);
            tiles[i].setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(tiles[i]);
        }
        setCornerRadiusDp(24);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        });
        setClipToOutline(true);
    }

    public void setCornerRadiusDp(float dp) {
        radiusDp = dp;
        radiusPx = dp * getResources().getDisplayMetrics().density;
        tiles[0].setCornerRadiusDp(mosaic ? 0 : dp);
        invalidateOutline();
    }

    /** Four or more URIs give a mosaic; otherwise the first URI (or the gradient for {@code seed}) fills the view. */
    public void setCovers(List<String> uris, int seed) {
        boolean m = uris.size() >= 4;
        if (m != mosaic) {
            mosaic = m;
            tiles[0].setCornerRadiusDp(m ? 0 : radiusDp); // single cover rounds itself (no outline clip needed)
            requestLayout();
        }
        if (m) {
            for (int i = 0; i < 4; i++) {
                tiles[i].setVisibility(VISIBLE);
                tiles[i].setArtwork(uris.get(i), seed + i);
            }
        } else {
            tiles[0].setArtwork(uris.isEmpty() ? null : uris.get(0), seed);
            for (int i = 1; i < 4; i++) tiles[i].setVisibility(GONE);
        }
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = MeasureSpec.getMode(heightSpec) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(heightSpec) : w; // square
        int tw = mosaic ? w / 2 : w;
        int th = mosaic ? h / 2 : h;
        for (ArtworkView t : tiles) {
            t.measure(MeasureSpec.makeMeasureSpec(tw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(th, MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(w, h);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        int h = b - t;
        if (!mosaic) {
            tiles[0].layout(0, 0, w, h);
            return;
        }
        int hw = w / 2;
        int hh = h / 2;
        tiles[0].layout(0, 0, hw, hh);
        tiles[1].layout(hw, 0, w, hh);
        tiles[2].layout(0, hh, hw, h);
        tiles[3].layout(hw, hh, w, h);
    }
}
