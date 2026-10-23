#!/usr/bin/env python3
"""Builds app/src/main/res/font/google_sans_flex.ttf from the upstream Google Sans Flex variable font (OFL 1.1).

Keeps only the wght (400-800) and ROND (0-100) axes; pins wdth 100, opsz 18, slnt 0. Subsets to Basic Latin,
Latin-1 punctuation/symbols and the typographic punctuation the UI uses, and keeps the tnum feature for timers.
Not shipped in the app. Usage:

    pip install fonttools
    curl -o /tmp/gsf.ttf "https://raw.githubusercontent.com/google/fonts/main/ofl/googlesansflex/GoogleSansFlex%5BROND,opsz,slnt,wdth,wght%5D.ttf"
    python3 tools/fonts/build_sans_subset.py /tmp/gsf.ttf app/src/main/res/font/google_sans_flex.ttf
"""
import sys

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

UNICODES = (
    list(range(0x20, 0x7F))            # Basic Latin
    + list(range(0xA0, 0x100))         # Latin-1 supplement (accents for artist names, symbols)
    + [0x2013, 0x2014, 0x2018, 0x2019, 0x201C, 0x201D, 0x2022, 0x2026, 0x2032, 0x2033,
       0x2039, 0x203A, 0x2044, 0x20AC, 0x2116, 0x2122, 0x2190, 0x2192, 0x2212, 0x00D7, 0x2009, 0x200A]
)
FEATURES = ["kern", "liga", "calt", "ccmp", "locl", "mark", "mkmk", "tnum", "pnum", "lnum", "case"]


def main(src, dst):
    font = TTFont(src)
    axes = {a.axisTag for a in font["fvar"].axes}
    pins = {"wght": (400, 400, 800), "ROND": (0, 0, 100)}
    for tag, value in (("wdth", 100), ("opsz", 18), ("slnt", 0), ("GRAD", 0)):
        if tag in axes:
            pins[tag] = value
    opts = subset.Options()
    opts.layout_features = FEATURES
    opts.name_IDs = ["*"]
    opts.name_legacy = True
    opts.notdef_outline = True
    opts.hinting = False          # Android renders unhinted variable fonts well; saves ~30%
    s = subset.Subsetter(opts)
    s.populate(unicodes=UNICODES)
    s.subset(font)  # subset first: the instancer's lazy gvar trips the subsetter otherwise
    font = instancer.instantiateVariableFont(font, pins, updateFontNames=False)
    font.save(dst)
    kept = [(a.axisTag, a.minValue, a.defaultValue, a.maxValue) for a in font["fvar"].axes]
    print("axes:", kept, "glyphs:", len(font.getGlyphOrder()))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
