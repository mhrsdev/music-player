package com.mhrsdev.musicplayer.ui.library;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.ui.theme.Tokens;
import java.util.Collections;
import java.util.List;

/**
 * Fast scroller for long track lists: a pill thumb on the end edge that appears while scrolling and fades after a
 * moment; dragging it jumps through the list and shows the current letter in a bubble. Draws only when visible,
 * allocates nothing per frame, and lets touches outside its edge strip fall through to the list.
 */
public class FastScrollerView extends View {
    private static final long HIDE_DELAY_MS = 1200;
    private static final int MIN_ITEMS = 40;

    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float dp;
    private final Runnable hide = () -> animate().alpha(0f).setDuration(220).start();
    @Nullable private RecyclerView list;
    private List<TrackListViewModel.Section> sections = Collections.emptyList();
    private int offset; // adapter positions before the first track (header rows)
    private int itemCount;
    private float fraction;
    private boolean dragging;

    public FastScrollerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        thumbPaint.setColor(Tokens.color(context, R.attr.mpOnSurfaceVariant));
        bubblePaint.setColor(Tokens.color(context, R.attr.mpPrimaryContainer));
        textPaint.setColor(Tokens.color(context, R.attr.mpOnPrimaryContainer));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(28 * dp);
        textPaint.setFakeBoldText(true);
        setAlpha(0f);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); // TalkBack users scroll the list itself
    }

    public void attach(RecyclerView rv, int headerRows) {
        list = rv;
        offset = headerRows;
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView r, int dx, int dy) {
                if (dragging || dy == 0 || itemCount < MIN_ITEMS) return;
                int range = r.computeVerticalScrollRange() - r.computeVerticalScrollExtent();
                fraction = range <= 0 ? 0 : r.computeVerticalScrollOffset() / (float) range;
                reveal();
            }
        });
    }

    /** New rows: item count and the alphabetical sections (empty = thumb only, no bubble). */
    public void setData(int count, List<TrackListViewModel.Section> sections) {
        itemCount = count;
        this.sections = sections;
        if (count < MIN_ITEMS) setAlpha(0f);
    }

    private void reveal() {
        removeCallbacks(hide);
        animate().cancel();
        setAlpha(1f);
        invalidate();
        if (!dragging) postDelayed(hide, HIDE_DELAY_MS);
    }

    private float edgeX() {
        return getLayoutDirection() == LAYOUT_DIRECTION_RTL ? 0 : getWidth();
    }

    private float thumbCenterY() {
        float half = 24 * dp;
        return half + fraction * (getHeight() - getPaddingBottom() - getPaddingTop() - 2 * half) + getPaddingTop();
    }

    @Override protected void onDraw(Canvas c) {
        if (getAlpha() == 0f || itemCount < MIN_ITEMS) return;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float cy = thumbCenterY();
        float w = (dragging ? 8 : 5) * dp;
        float inset = 6 * dp;
        float x = rtl ? inset : getWidth() - inset - w;
        rect.set(x, cy - 24 * dp, x + w, cy + 24 * dp);
        c.drawRoundRect(rect, w / 2, w / 2, thumbPaint);
        String label = dragging ? labelAt(positionFor(fraction)) : null;
        if (label != null) {
            float r = 30 * dp;
            float bx = rtl ? inset + w + 16 * dp + r : getWidth() - inset - w - 16 * dp - r;
            c.drawCircle(bx, cy, r, bubblePaint);
            c.drawText(label, bx, cy - (textPaint.descent() + textPaint.ascent()) / 2, textPaint);
        }
    }

    @Nullable private String labelAt(int position) {
        if (sections.isEmpty()) return null;
        int lo = 0, hi = sections.size() - 1, found = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (sections.get(mid).start() <= position) {
                found = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return sections.get(found).label();
    }

    private int positionFor(float f) {
        return Math.max(0, Math.min(itemCount - 1, Math.round(f * (itemCount - 1))));
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouchEvent(MotionEvent e) {
        if (itemCount < MIN_ITEMS || list == null) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                float strip = 40 * dp;
                if (getAlpha() < 0.5f || Math.abs(e.getX() - edgeX()) > strip) return false;
                dragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                scrollTo(e.getY());
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (dragging) scrollTo(e.getY());
                return dragging;
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) return false;
                dragging = false;
                reveal();
                return true;
            }
            default -> {
                return dragging;
            }
        }
    }

    private void scrollTo(float y) {
        float half = 24 * dp;
        float usable = getHeight() - getPaddingTop() - getPaddingBottom() - 2 * half;
        fraction = usable <= 0 ? 0 : Math.max(0f, Math.min(1f, (y - getPaddingTop() - half) / usable));
        RecyclerView.LayoutManager lm = list.getLayoutManager();
        if (lm instanceof LinearLayoutManager llm) llm.scrollToPositionWithOffset(positionFor(fraction) + offset, 0);
        reveal();
    }
}
