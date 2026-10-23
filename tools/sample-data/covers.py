#!/usr/bin/env python3
"""Rebuild app/src/debug/assets/sample-covers/*.jpg from the public-domain Commons originals. Dev tool, not shipped.

    python3 -I tools/sample-data/covers.py WORK_DIR

WORK_DIR must be new/empty (downloads are untrusted data). Each image is cropped to a square (offset = where the crop
window sits in the free range, 0..1), resized to 512x512 and saved as JPEG at the highest quality <= ~48 KB (q80..66).
Sources and licences: docs/CREDITS.md. Wikimedia rate-limits hard: this waits on 429 and keeps a pause between files.
"""
import os, sys, time, urllib.error, urllib.request
from PIL import Image

T = "https://thumb.wikimedia.org/wikipedia/commons/thumb/"
# name, thumbnail URL (a size from https://w.wiki/GHai), crop offset
COVERS = [
    ("reject-bin", T + "b/b3/Katsushika_Hokusai_-_Thirty-Six_Views_of_Mount_Fuji-_The_Great_Wave_Off_the_Coast_of_Kanagawa_-_Google_Art_Project.jpg/960px-Katsushika_Hokusai_-_Thirty-Six_Views_of_Mount_Fuji-_The_Great_Wave_Off_the_Coast_of_Kanagawa_-_Google_Art_Project.jpg", .3),
    ("o-great-queen-electric", T + "c/c5/Edvard_Munch%2C_1893%2C_The_Scream%2C_oil%2C_tempera_and_pastel_on_cardboard%2C_91_x_73_cm%2C_National_Gallery_of_Norway.jpg/960px-Edvard_Munch%2C_1893%2C_The_Scream%2C_oil%2C_tempera_and_pastel_on_cardboard%2C_91_x_73_cm%2C_National_Gallery_of_Norway.jpg", .3),
    ("goldberg", T + "b/bc/Johannes_Vermeer_-_Lady_at_the_Virginal_with_a_Gentleman%2C_%27The_Music_Lesson%27_-_Google_Art_Project.jpg/960px-Johannes_Vermeer_-_Lady_at_the_Virginal_with_a_Gentleman%2C_%27The_Music_Lesson%27_-_Google_Art_Project.jpg", .3),
    ("rnb-beats", T + "2/21/Vincent_Willem_van_Gogh_-_Cafe_Terrace_at_Night_%28Yorck%29.jpg/960px-Vincent_Willem_van_Gogh_-_Cafe_Terrace_at_Night_%28Yorck%29.jpg", .45),
    ("positive-attitude", T + "8/8d/Pierre-Auguste_Renoir_-_Luncheon_of_the_Boating_Party_-_Google_Art_Project.jpg/960px-Pierre-Auguste_Renoir_-_Luncheon_of_the_Boating_Party_-_Google_Art_Project.jpg", .5),
    ("directionless-ep", T + "b/b9/Caspar_David_Friedrich_-_Wanderer_above_the_sea_of_fog.jpg/960px-Caspar_David_Friedrich_-_Wanderer_above_the_sea_of_fog.jpg", .35),
    ("royalty-free", T + "9/96/Turner_-_Rain%2C_Steam_and_Speed_-_National_Gallery_file.jpg/960px-Turner_-_Rain%2C_Steam_and_Speed_-_National_Gallery_file.jpg", .7),
    ("dokhtaran-quchan", T + "d/d4/Young_man_in_European_dress_playing_on_a_lute%2C_Iran%2C_1630s.jpg/960px-Young_man_in_European_dress_playing_on_a_lute%2C_Iran%2C_1630s.jpg", .25),
    ("ey-iran", T + "3/30/Hall_of_Mirrors_-_Kamal-ol-molk_-_Golestan_Palace.png/500px-Hall_of_Mirrors_-_Kamal-ol-molk_-_Golestan_Palace.png", .5),
]


def download(url, path):
    for _ in range(8):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "MowjSampleData/1.0 (covers.py)"})
            with urllib.request.urlopen(req, timeout=90) as r, open(path, "wb") as f:
                f.write(r.read())
            return
        except urllib.error.HTTPError as e:
            if e.code != 429:
                raise
            time.sleep(min(int(e.headers.get("retry-after", "20")), 90) + 1)
    raise SystemExit("rate-limited: " + url)


def main():
    work = sys.argv[1]
    out = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "debug", "assets", "sample-covers")
    os.makedirs(work)
    for name, url, off in COVERS:
        os.makedirs(os.path.join(work, name))
        src = os.path.join(work, name, "src")
        download(url, src)
        im = Image.open(src).convert("RGB")
        w, h = im.size
        s = min(w, h)
        x, y = (int((w - s) * off), 0) if w > h else (0, int((h - s) * off))
        sq = im.crop((x, y, x + s, y + s)).resize((512, 512), Image.LANCZOS)
        dest = os.path.join(out, name + ".jpg")
        for q in (80, 75, 70, 66):
            sq.save(dest, "JPEG", quality=q, optimize=True)
            if os.path.getsize(dest) <= 48000:
                break
        print(name, os.path.getsize(dest))
        time.sleep(3)


if __name__ == "__main__":
    main()
