package com.mhrsdev.musicplayer.ui.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;

import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.ui.theme.MotionPolicy;
import com.mhrsdev.musicplayer.ui.theme.Tokens;

/**
 * Floating pill navigation (DESIGN.md 01/16): a 68 dp pill on surfaceContainerHigh; the selected item widens into a
 * secondaryContainer pill and shows its label (bounds transition, only on selection). Others are icon-only with a
 * tooltip + content description. Mirrors in RTL automatically. Use {@link #addDefaultItems()} for the 4 app tabs.
 */
public class FloatingNavView extends LinearLayout {
    public interface OnItemSelectedListener {
        void onItemSelected(@IdRes int id);

        /** Tapped the already-selected tab (scroll to top). */
        default void onItemReselected(@IdRes int id) {}
    }

    private final int selectedBg;
    private final int onSelected;
    private final int unselectedTint;
    @IdRes private int selectedId = View.NO_ID;
    @Nullable private OnItemSelectedListener listener;

    public FloatingNavView(Context context) {
        this(context, null);
    }

    public FloatingNavView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int pad = (int) Tokens.dp(context, 8f);
        setPadding(pad, pad, pad, pad);
        setMinimumHeight((int) Tokens.dp(context, 68f));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Tokens.dp(context, 34f));
        int high = Tokens.color(context, R.attr.mpSurfaceContainerHigh);
        bg.setColor((high & 0x00FFFFFF) | 0xEB000000); // 92% opaque, no live blur
        setBackground(bg);
        setElevation(Tokens.dp(context, 6f));
        selectedBg = Tokens.color(context, R.attr.mpSecondaryContainer);
        onSelected = Tokens.color(context, R.attr.mpOnSecondaryContainer);
        unselectedTint = Tokens.color(context, R.attr.mpOnSurfaceVariant);
    }

    public void setOnItemSelectedListener(@Nullable OnItemSelectedListener l) {
        listener = l;
    }

    /** Home, Search, Library, Settings with ids R.id.mp_nav_*; selects Home. */
    public void addDefaultItems() {
        addItem(R.id.mp_nav_home, R.drawable.ic_mp_home, getContext().getString(R.string.mp_nav_home));
        addItem(R.id.mp_nav_search, R.drawable.ic_mp_search, getContext().getString(R.string.mp_nav_search));
        addItem(R.id.mp_nav_library, R.drawable.ic_mp_library, getContext().getString(R.string.mp_nav_library));
        addItem(R.id.mp_nav_settings, R.drawable.ic_mp_settings, getContext().getString(R.string.mp_nav_settings));
        setSelectedItem(R.id.mp_nav_home, false);
    }

    public void addItem(@IdRes int id, @DrawableRes int icon, CharSequence label) {
        Context c = getContext();
        LinearLayout item = new LinearLayout(c);
        item.setId(id);
        item.setOrientation(HORIZONTAL);
        item.setGravity(Gravity.CENTER);
        int h = (int) Tokens.dp(c, 52f), padH = (int) Tokens.dp(c, 14f);
        item.setPadding(padH, 0, padH, 0);
        item.setMinimumWidth((int) Tokens.dp(c, 56f));
        item.setClickable(true);
        item.setFocusable(true);
        item.setContentDescription(label);
        item.setTooltipText(label);
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(h / 2f);
        pill.setColor(selectedBg);
        pill.setAlpha(0);
        item.setBackground(pill);

        ImageView iv = new ImageView(c);
        iv.setImageResource(icon);
        iv.setImageTintList(ColorStateList.valueOf(unselectedTint));
        int is = (int) Tokens.dp(c, 24f);
        item.addView(iv, new LayoutParams(is, is));

        TextView tv = new TextView(c);
        tv.setText(label);
        tv.setTextAppearance(R.style.TextAppearance_MusicPlayer_Label);
        tv.setTextColor(onSelected);
        tv.setMaxLines(1);
        tv.setVisibility(GONE);
        tv.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LayoutParams tlp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.setMarginStart((int) Tokens.dp(c, 8f));
        item.addView(tv, tlp);

        item.setOnClickListener(v -> {
            if (id == selectedId) {
                if (listener != null) listener.onItemReselected(id);
            } else {
                setSelectedItem(id, true);
                if (listener != null) listener.onItemSelected(id);
            }
        });
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, h);
        lp.setMarginStart(getChildCount() == 0 ? 0 : (int) Tokens.dp(c, 2f));
        addView(item, lp);
    }

    @IdRes
    public int getSelectedItem() {
        return selectedId;
    }

    /** Selects without notifying the listener (e.g. restoring state or following back navigation). */
    public void setSelectedItem(@IdRes int id, boolean animate) {
        if (animate && MotionPolicy.decorativeMotionEnabled(getContext()) && isLaidOut()) {
            ChangeBounds t = new ChangeBounds();
            t.setDuration(Tokens.MOTION_FAST + 100);
            TransitionManager.beginDelayedTransition(this, t);
        }
        selectedId = id;
        for (int i = 0; i < getChildCount(); i++) {
            ViewGroup item = (ViewGroup) getChildAt(i);
            boolean sel = item.getId() == id;
            item.setSelected(sel);
            item.getBackground().setAlpha(sel ? 255 : 0);
            ((ImageView) item.getChildAt(0)).setImageTintList(ColorStateList.valueOf(sel ? onSelected : unselectedTint));
            item.getChildAt(1).setVisibility(sel ? VISIBLE : GONE);
        }
    }
}
