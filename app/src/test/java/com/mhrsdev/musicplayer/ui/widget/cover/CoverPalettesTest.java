package com.mhrsdev.musicplayer.ui.widget.cover;

import static org.junit.Assert.assertTrue;

import com.mhrsdev.musicplayer.ui.theme.ColorMath;
import com.mhrsdev.musicplayer.ui.widget.cover.CoverPalettes.Palette;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/** Every text/background pair a generated cover can produce must reach WCAG 4.5:1. */
public class CoverPalettesTest {

    @Test public void atLeast24DistinctPalettes() {
        Set<String> names = new HashSet<>();
        for (Palette p : CoverPalettes.ALL) names.add(p.name());
        assertTrue(CoverPalettes.ALL.size() >= 24);
        assertTrue(names.size() == CoverPalettes.ALL.size());
    }

    @Test public void textContrastAtLeast4_5() {
        for (Palette p : CoverPalettes.ALL) {
            int sub = ColorMath.lerp(0xFF000000 | p.sky0(), p.ink(), CoverPalettes.SUBTITLE_ALPHA);
            int[] grounds = {p.sky0(), p.sky1(), p.sky2(), p.hill(), p.deep(),
                    over(p.accent(), p.deep()), over(p.accent2(), p.deep()), over(p.glow(), p.deep()),
                    over(p.halo(), p.deep())};
            for (int g : grounds) {
                check(p.name() + " title", p.ink(), g);
                // subtitle ink is drawn at 90% over the ground under it
                check(p.name() + " subtitle", ColorMath.lerp(g, p.ink(), CoverPalettes.SUBTITLE_ALPHA), g);
            }
            check(p.name() + " subtitle on sky", sub, p.sky0());
        }
    }

    @Test public void palettesAreDarkAndCalm() {
        for (Palette p : CoverPalettes.ALL) {
            assertTrue(p.name() + " sky0 dark", ColorMath.luminance(p.sky0()) < 0.03);
            assertTrue(p.name() + " deep darkest", ColorMath.luminance(p.deep()) <= ColorMath.luminance(p.hill()));
        }
    }

    /** {@code fg} under the 70% text scrim of {@code deep}. */
    private static int over(int fg, int deep) {
        return ColorMath.lerp(fg, deep, CoverPalettes.TEXT_SCRIM);
    }

    private static void check(String what, int fg, int bg) {
        double c = ColorMath.contrast(fg | 0xFF000000, bg | 0xFF000000);
        assertTrue(what + String.format(" %06X on %06X = %.2f", fg & 0xFFFFFF, bg & 0xFFFFFF, c), c >= 4.5);
    }
}
