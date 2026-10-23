package com.mhrsdev.musicplayer.ui.widget;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import com.mhrsdev.musicplayer.R;

/**
 * Per-script font: text that contains Arabic-script letters (Persian titles inside an English UI, or anything in the
 * fa locale) is drawn with Vazirmatn at the view's weight; Latin text keeps the view's own typeface.
 * Cheap enough for every bind: a plain char-range scan, no regex or allocation, one cached Typeface per weight.
 */
public final class ScriptFont {
    private static Typeface vazir;
    private static final Typeface[] BY_WEIGHT = new Typeface[10];

    private ScriptFont() {}

    /** Sets {@code text} on {@code view} and picks the typeface for its script. */
    public static void setText(TextView view, @Nullable CharSequence text) {
        view.setText(text);
        apply(view, text);
    }

    /** Applies the script typeface for {@code text} without changing the view's text. */
    public static void apply(TextView view, @Nullable CharSequence text) {
        Object tag = view.getTag(R.id.script_font_base);
        Typeface base;
        if (tag instanceof Typeface t) {
            base = t;
        } else {
            base = view.getTypeface() == null ? Typeface.DEFAULT : view.getTypeface();
            view.setTag(R.id.script_font_base, base);
        }
        Typeface want = hasArabicScript(text) ? vazirFor(view.getContext(), base) : base;
        if (want != null && want != view.getTypeface()) view.setTypeface(want);
    }

    /** True when {@code s} has at least one Arabic-script letter (Arabic, Supplement, Presentation Forms A/B). */
    public static boolean hasArabicScript(@Nullable CharSequence s) {
        if (s == null) return false;
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            if (c < 0x0600) continue;
            if (c <= 0x06FF || (c >= 0x0750 && c <= 0x077F) || (c >= 0x08A0 && c <= 0x08FF)
                    || (c >= 0xFB50 && c <= 0xFDFF) || (c >= 0xFE70 && c <= 0xFEFF)) return true;
        }
        return false;
    }

    @Nullable
    private static Typeface vazirFor(Context context, Typeface base) {
        if (vazir == null) {
            try {
                vazir = ResourcesCompat.getFont(context, R.font.vazirmatn);
            } catch (RuntimeException e) {
                return base;
            }
            if (vazir == null) return base;
        }
        int weight = Build.VERSION.SDK_INT >= 28 ? base.getWeight() : (base.isBold() ? 700 : 400);
        int slot = Math.max(1, Math.min(9, Math.round(weight / 100f)));
        Typeface t = BY_WEIGHT[slot];
        if (t == null) {
            t = Build.VERSION.SDK_INT >= 28 ? Typeface.create(vazir, slot * 100, false)
                    : Typeface.create(vazir, slot >= 6 ? Typeface.BOLD : Typeface.NORMAL);
            BY_WEIGHT[slot] = t;
        }
        return t;
    }
}
