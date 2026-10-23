package com.mhrsdev.musicplayer.ui.theme;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.widget.TextView;

import androidx.annotation.FontRes;
import androidx.annotation.MainThread;
import androidx.core.content.res.ResourcesCompat;

import com.mhrsdev.musicplayer.R;

/**
 * Mowj type roles for text drawn in code (custom Views, Canvas). Each role = a font family (roundness) + weight:
 * Display ROND 100 w700, Headline ROND 100 w600, Title ROND 60 w500, Label ROND 60 w500, Body ROND 20 w400,
 * Numeric = Body with tabular figures. The families live in res/font (Latin, Google Sans Flex) and res/font-fa
 * (Vazirmatn), so the locale picks the script. Typefaces are created once per role and cached: never per bind.
 * XML text uses the matching TextAppearance.MusicPlayer.* styles instead.
 */
@MainThread
public class TypeRoles {
    public enum Role {
        DISPLAY(R.font.mp_sans_round, 700),
        HEADLINE(R.font.mp_sans_round, 600),
        TITLE(R.font.mp_sans_soft, 500),
        LABEL(R.font.mp_sans_soft, 500),
        BODY(R.font.mp_sans, 400),
        NUMERIC(R.font.mp_sans, 500);

        @FontRes final int family;
        final int weight;

        Role(@FontRes int family, int weight) {
            this.family = family;
            this.weight = weight;
        }
    }

    private static final Typeface[] CACHE = new Typeface[Role.values().length];
    private static String cachedLocale;

    private TypeRoles() {}

    /** Cached typeface of {@code role} for the current locale (falls back to the system sans). */
    public static Typeface typeface(Context context, Role role) {
        String locale = context.getResources().getConfiguration().getLocales().get(0).getLanguage();
        if (!locale.equals(cachedLocale)) {
            java.util.Arrays.fill(CACHE, null);
            cachedLocale = locale;
        }
        Typeface t = CACHE[role.ordinal()];
        if (t != null) return t;
        Typeface family = null;
        try {
            family = ResourcesCompat.getFont(context, role.family);
        } catch (RuntimeException ignored) {
            // missing font (e.g. a stripped test build): system sans below
        }
        if (family == null) family = Typeface.DEFAULT;
        t = Build.VERSION.SDK_INT >= 28 ? Typeface.create(family, role.weight, false)
                : Typeface.create(family, role.weight >= 600 ? Typeface.BOLD : Typeface.NORMAL);
        CACHE[role.ordinal()] = t;
        return t;
    }

    /** Applies the role's typeface (and tabular figures for {@link Role#NUMERIC}) to a TextView. */
    public static void apply(TextView view, Role role) {
        view.setTypeface(typeface(view.getContext(), role));
        if (role == Role.NUMERIC) view.setFontFeatureSettings("tnum");
    }
}
