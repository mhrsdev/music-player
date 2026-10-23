package com.mhrsdev.musicplayer.ui.widget.cover;

import java.util.List;

/**
 * Curated night palettes for generated covers (lapis, turquoise, saffron, rose, forest, amber, violet, graphite
 * families). Built like the owner's reference cover (design 05, "Without You"): a three-stop sky, two hill tones that
 * get darker towards the viewer, an accent pair, a glow (moon, stars) with its halo, and the ink for text.
 * Rules, enforced by {@code CoverPalettesTest}: ink vs every surface text may sit on is at least 4.5:1, also for the
 * 85% subtitle ink and over the 70% text scrim on the bright accents.
 */
public class CoverPalettes {

    /**
     * @param sky0  top of the sky (darkest sky tone)
     * @param sky1  middle of the sky
     * @param sky2  horizon (lightest sky tone; still dark enough for white text)
     * @param hill  back silhouette
     * @param deep  front silhouette and text scrim
     * @param accent  main saturated element (sun, ribbon, tile, label)
     * @param accent2 secondary accent (gold rule, second ribbon)
     * @param glow  moon, stars, highlights
     * @param halo  soft light around the glow (drawn at low alpha)
     * @param ink   text
     */
    public record Palette(String name, int sky0, int sky1, int sky2, int hill, int deep, int accent, int accent2,
                          int glow, int halo, int ink) { }

    private static Palette p(String name, int sky0, int sky1, int sky2, int hill, int deep, int accent, int accent2,
                             int glow, int halo) {
        return new Palette(name, 0xFF000000 | sky0, 0xFF000000 | sky1, 0xFF000000 | sky2, 0xFF000000 | hill,
                0xFF000000 | deep, 0xFF000000 | accent, 0xFF000000 | accent2, 0xFF000000 | glow, 0xFF000000 | halo,
                0xFFFFFFFF);
    }

    public static final List<Palette> ALL = List.of(
            // lapis
            p("lapis-moon", 0x081A33, 0x11486C, 0x1F7584, 0x0C2C41, 0x05141F, 0x3E8FC9, 0x7FD6CB, 0xEEF8FF, 0xBFE6FF),
            p("lapis-gold", 0x0A1438, 0x1B3A7A, 0x2C5A9E, 0x13285A, 0x070E24, 0xE2B64A, 0x3FC1C9, 0xF6ECD4, 0xFFE3A0),
            p("indigo-teal", 0x0D0F2E, 0x23306B, 0x2E6F8A, 0x172350, 0x080A1E, 0x4FC3C9, 0x9AA8FF, 0xE8F4FF, 0xA8E6F0),
            p("midnight-silver", 0x0B1220, 0x1F2D44, 0x3A4C66, 0x1A2536, 0x090D15, 0x8FA7C4, 0xC9D6E8, 0xF4F7FB, 0xD6E2F2),
            p("ocean-deep", 0x04121F, 0x0B3354, 0x145A7A, 0x08263D, 0x030B14, 0x2FA8D8, 0x7FE0E0, 0xE6F8FF, 0x9FDCF5),
            p("kashi-tile", 0x0E1F45, 0x15357A, 0x1E4F8F, 0x102A5E, 0x07122A, 0x3FC1C9, 0xE2B64A, 0xF2E6CC, 0x9EE8EC),
            // turquoise
            p("turquoise-dusk", 0x061E22, 0x0F4A50, 0x1F7A76, 0x0C3438, 0x04161A, 0x3FB3A6, 0xE2B64A, 0xE6FFF9, 0x9BE3D5),
            p("teal-amber", 0x07201F, 0x154845, 0x2B6E5E, 0x113A36, 0x061614, 0xE0A547, 0x6FD1BE, 0xFFF1D6, 0xF7CF8E),
            p("sea-glass", 0x0A1E2A, 0x1A4A5C, 0x2F7280, 0x133746, 0x07151D, 0x7FD1C7, 0xB9E6F0, 0xEFFFFC, 0xB5F0E6),
            // saffron / amber
            p("saffron-night", 0x1E0E06, 0x5A2C0E, 0x93501A, 0x3E1F0B, 0x140803, 0xF0A23A, 0xFFD27A, 0xFFF3D6, 0xFFC46B),
            p("amber-dune", 0x22160A, 0x5C3E18, 0x86602A, 0x3F2A10, 0x150D05, 0xE8B04E, 0xD9704A, 0xFFF0C9, 0xF7CF8E),
            p("copper-dusk", 0x1C0D0A, 0x52241A, 0x84462E, 0x3A1912, 0x120705, 0xE0875A, 0xF2C58A, 0xFFE8D6, 0xF5A07A),
            // rose
            p("rose-dusk", 0x1E0A1A, 0x5A1F48, 0x8E3A64, 0x3E1534, 0x14060F, 0xE87AA0, 0xFFC2A8, 0xFFE6EF, 0xF7A8C4),
            p("pomegranate", 0x2A040B, 0x6E0F1E, 0x9E1A26, 0x4A0A14, 0x160206, 0xFF5468, 0xE9B85A, 0xFFE1DE, 0xFF8F8F),
            p("mulberry", 0x1A0B22, 0x4A1E52, 0x74306E, 0x33163C, 0x100614, 0xD077B8, 0xF2B5D4, 0xFBE6FF, 0xE3A6E8),
            // forest
            p("forest-mist", 0x07150F, 0x173A2A, 0x2D5E44, 0x10291E, 0x050D09, 0x7CC48F, 0xD7E8A0, 0xEEFBEF, 0xB8E8C4),
            p("olive-grove", 0x141608, 0x3A3F18, 0x5A6126, 0x2A2E10, 0x0C0E04, 0xC2C85A, 0xE9D58A, 0xF6F7DE, 0xDCE29A),
            p("pine-frost", 0x0A1716, 0x1F3B3A, 0x3D6261, 0x162C2B, 0x070F0F, 0x9FD8CF, 0xD2E7F0, 0xF2FBFA, 0xC4ECE6),
            // violet
            p("violet-dream", 0x120A2C, 0x3A1F6E, 0x64398F, 0x28165A, 0x0B0620, 0xA98BF0, 0xF0A8D8, 0xF3EDFF, 0xC9B6FF),
            p("tehran-nights", 0x0C0A2C, 0x3B1C6B, 0x7E3678, 0x2B1650, 0x08051A, 0xE3B6FF, 0xFFCF7A, 0xF3E6FF, 0xD3A8F5),
            p("plum-ink", 0x160B1A, 0x3C1F44, 0x5E3566, 0x2A1430, 0x0D0610, 0xC08FD0, 0x8FD0C8, 0xF6ECF8, 0xD9B8E3),
            p("desert-night", 0x1A1230, 0x4A2C55, 0x7E4854, 0x33203F, 0x110A1C, 0xF0A77A, 0xFFD9A0, 0xFFF0DF, 0xF5B990),
            // graphite
            p("graphite", 0x121418, 0x2A2F36, 0x454C55, 0x1E2228, 0x0A0B0D, 0xA3ADBA, 0xE2B64A, 0xF2F4F7, 0xD0D6DE),
            p("ink-vermillion", 0x0E0D10, 0x26222A, 0x3A3440, 0x1C191F, 0x080709, 0xE0533D, 0xF2C14E, 0xFFF1E8, 0xF59A7A),
            p("slate-mint", 0x0F1519, 0x263540, 0x3E5662, 0x1A252C, 0x090D10, 0x8FE0C0, 0xC9D6E8, 0xEFFAF5, 0xB5EAD6),
            p("aurora-north", 0x050E1A, 0x0E2A3A, 0x15474A, 0x0A1E28, 0x03090E, 0x59E0A8, 0x8F7BF0, 0xE9FFF6, 0x9FF2CF));

    /** Alpha of the text scrim ({@link Palette#deep}) under a title that may overlap a bright element. */
    public static final float TEXT_SCRIM = 0.7f;
    /** Opacity of the subtitle ink. */
    public static final float SUBTITLE_ALPHA = 0.86f;

    private CoverPalettes() { }
}
