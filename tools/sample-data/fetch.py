#!/usr/bin/env python3
"""Re-download the sample-data audio files so their tags/durations can be checked. Dev tool, not shipped.

    python3 -I tools/sample-data/fetch.py OUT_DIR [--hash]

Every file goes into its own new directory OUT_DIR/<key>/ and is treated as untrusted data (never executed).
It is checked against SHA256 and, when ffprobe exists, the tags are printed next to the expected metadata
(title, artist, album, duration) that fake/SampleData.java uses. --hash only prints the SHA256 values.
Audio files are NOT committed (size + licences); see docs/CREDITS.md for licences and attribution.
"""
import hashlib, json, os, subprocess, sys, time, urllib.error, urllib.parse, urllib.request

IA = "https://archive.org/download/"
CM = "https://upload.wikimedia.org/wikipedia/commons/"
# key, url, expected duration (s, +-2), checksum of the file as fetched (SHA-256; the two Commons files use the SHA-1
# that the Commons API reports, because Wikimedia rate-limited the download when this list was written)
FILES = [
    ("reject-texas", IA + "reject-bin/Reject%20Bin/1%2001.%20Texas.mp3", 260, "a42c56648032a81fd59f4a287f63cc2dc061b9aa01a5f3449fae71ffd2b2aa56"),
    ("reject-1000-words", IA + "reject-bin/Reject%20Bin/1%2002.%201000%20Words.mp3", 193, "ea554ccb8072f2457ef4552ad40e4c5a1f6a45a00ce5a2cd055db47fcdcf7186"),
    ("reject-keep-in-touch", IA + "reject-bin/Reject%20Bin/1%2003.%20Keep%20In%20Touch.mp3", 275, "753b9a49533a34ba09c025cca92c76fb82678e918ae6b6388aa40832e0ba644b"),
    ("reject-iou", IA + "reject-bin/Reject%20Bin/1%2004.%20IOU.mp3", 247, "50ae972f8a83900deeb2bf30cabee265526851784ab0b8615f9fe80c0a7390fa"),
    ("reject-skyline", IA + "reject-bin/Reject%20Bin/1%2019.%20Skyline%20of%20the%20Sea.mp3", 256, "deeec8b7552c6961395119c4b5746c40c3261287a13741da091042b3efb7476b"),
    ("cz-i-can-be-your-love", IA + "cz-ogreatqueenelectric/Chris%20Zabriskie%20-%20O%20Great%20Queen%20Electric%2C%20What%20Do%20You%20Have%20Waiting%20for%20Me%C2%BF%20-%2002%20-%20I%20Can%20Be%20Your%20Love.mp3", 244, "bf7f6355f724c700131fc3feb381e8203745b47148f7894cf2848993b9a80b02"),
    ("cz-start-the-cure", IA + "cz-ogreatqueenelectric/Chris%20Zabriskie%20-%20O%20Great%20Queen%20Electric%2C%20What%20Do%20You%20Have%20Waiting%20for%20Me%C2%BF%20-%2003%20-%20We%20Start%20the%20Cure%20in%20Paris.mp3", 354, "d04e08ab794bb03a6f668436d1ebc7be48b3530c398e9bf6c3acb6f9ea6c07c4"),
    ("cz-if-only-she-knew", IA + "cz-ogreatqueenelectric/Chris%20Zabriskie%20-%20O%20Great%20Queen%20Electric%2C%20What%20Do%20You%20Have%20Waiting%20for%20Me%C2%BF%20-%2005%20-%20If%20Only%20She%20Knew.mp3", 266, "e4a6d9465dc25eee0f23cd058668ce79eaa03a77b366e040e64a8ec6d7e34d5b"),
    ("cz-gods-will", IA + "cz-ogreatqueenelectric/Chris%20Zabriskie%20-%20O%20Great%20Queen%20Electric%2C%20What%20Do%20You%20Have%20Waiting%20for%20Me%C2%BF%20-%2007%20-%20God%27s%20Will.mp3", 249, "3ca657b7b22ac7a07a90c1a6ba7d9b5ea4480f4b4eea2f595a74ffb5ad25a84e"),
    ("ki-aria", IA + "The_Open_Goldberg_Variations-11823/Kimiko_Ishizaka_-_01_-_Aria.mp3", 300, "6412a52127ef4c16b6cf7e4abc134ba3fbdb7419e542690135d5def80c09cfb4"),
    ("ki-var-1", IA + "The_Open_Goldberg_Variations-11823/Kimiko_Ishizaka_-_02_-_Variatio_1_a_1_Clav.mp3", 116, "3dacdccbb2f40e82a60131b487f7da1e0fda20ef6791871e051e22dea4aa9e99"),
    ("ki-var-2", IA + "The_Open_Goldberg_Variations-11823/Kimiko_Ishizaka_-_03_-_Variatio_2_a_1_Clav.mp3", 124, "09cb268bf3683a938f1646d92235fedb7db9dad96a15cb0cc5416d3122bbba03"),
    ("lf-sunglasses", IA + "LoyaltyFreakMusic-INSTRUMENTALRBBEATSTOSINGORRAPON/Loyalty%20Freak%20Music%20-%20INSTRUMENTAL%20R%26B%20BEATS%20TO%20SING%20OR%20RAP%20ON%20-%2001%20Sunglasses.mp3", 169, "36bd3388cc3809fa04a32008e684bde8fb116e6d92ba02283a5158dee2baa0e2"),
    ("lf-sensual-melancholia", IA + "LoyaltyFreakMusic-INSTRUMENTALRBBEATSTOSINGORRAPON/Loyalty%20Freak%20Music%20-%20INSTRUMENTAL%20R%26B%20BEATS%20TO%20SING%20OR%20RAP%20ON%20-%2003%20Sensual%20Melancholia.mp3", 275, "eb3d1e8c8c08bb9bf734a41c49c5d06fbae8ab0417f7d2ddf0a60f2a5d2649ab"),
    ("lf-dirty-shoes-blues", IA + "LoyaltyFreakMusic-INSTRUMENTALRBBEATSTOSINGORRAPON/Loyalty%20Freak%20Music%20-%20INSTRUMENTAL%20R%26B%20BEATS%20TO%20SING%20OR%20RAP%20ON%20-%2004%20Dirty%20shoes%20blues.mp3", 183, "efe1881ef23a3584c30c3e8cac17bbb62e5f2ae344d5ad138e669c48c97e5254"),
    ("lf-the-candle", IA + "LoyaltyFreakMusic-INSTRUMENTALRBBEATSTOSINGORRAPON/Loyalty%20Freak%20Music%20-%20INSTRUMENTAL%20R%26B%20BEATS%20TO%20SING%20OR%20RAP%20ON%20-%2005%20The%20candle.mp3", 247, "f79390971c3d28cdfa03ccd4f7fd9216edc24584b1c5968ce6500ca68b2320fc"),
    ("lf-picnic", IA + "LoyaltyFreakMusicPOSITIVEATTITUDE20170920175908839/Loyalty_Freak_Music_-_01_-_Go_to_the_Picnic.mp3", 187, "dbeaad7bb137f39f5d3e7ee5c83b2ac53f4963cc92f3d3fe6321c490e1760cf1"),
    ("lf-hug-convoy", IA + "LoyaltyFreakMusicPOSITIVEATTITUDE20170920175908839/Loyalty_Freak_Music_-_02_-_Hug_Convoy.mp3", 200, "bf3a5b185adca22222fc4dea07a230d46e985df036d041f74c28820329faebed"),
    ("bff-night-owl", IA + "DirectionlessEP/Broke%20For%20Free%20-%20Directionless%20EP%20-%2001%20Night%20Owl.mp3", 194, "687124084f343891894c263fafa877a4ecff84c91c5d56931b0dd1e0dce26f2d"),
    ("bff-my-always-mood", IA + "DirectionlessEP/Broke%20For%20Free%20-%20Directionless%20EP%20-%2002%20My%20Always%20Mood.mp3", 188, "16249a1d15c34fc9fe534f1fbc495bd1bfc2900af0d03293fc15a829364fdd45"),
    ("bff-day-bird", IA + "DirectionlessEP/Broke%20For%20Free%20-%20Directionless%20EP%20-%2003%20Day%20Bird.mp3", 223, "adc48d1b04c4e4cd58d625ac71d40ebbc664a368c3d4e674f4eeb64e13c956be"),
    ("km-sneaky-snitch", "https://incompetech.com/music/royalty-free/mp3-royaltyfree/Sneaky%20Snitch.mp3", 137, "db1710333d68a215277f77e4e17e1a64bf4ed38f87c70f8d0f56b16b6aa4f884"),
    ("km-carefree", "https://incompetech.com/music/royalty-free/mp3-royaltyfree/Carefree.mp3", 205, "8433b770a630d9b1594fd484442c677907ece899a4d149954cd2e74fd733e311"),
    ("km-wallpaper", "https://incompetech.com/music/royalty-free/mp3-royaltyfree/Wallpaper.mp3", 220, "9fa810e5193c88f1b360537366f210f16c2588106d5292f0659e9957fa274f96"),
    ("fa-dokhtaran-quchan", CM + "e/ea/Dokhtaran-e_Quchan-_%D8%AF%D8%AE%D8%AA%D8%B1%D8%A7%D9%86_%D9%82%D9%88%DA%86%D8%A7%D9%86.ogg", 409, "bc9ca0b71ec3599961e62bc1e4746f1a92e2b9e0"),
    ("fa-ey-iran", CM + "8/87/Ey_Iran_%28santoor%29_by_Ruhollah_Khaleghi.ogg", 105, "76283d4f7289083c106a958447438e2404378747"),
]


def fetch(key, url, out):
    d = os.path.join(out, key)
    os.makedirs(d)  # new empty directory per download; fails if it exists
    path = os.path.join(d, "audio" + os.path.splitext(urllib.parse.urlparse(url).path)[1])
    req = urllib.request.Request(url, headers={"User-Agent": "MowjSampleData/1.0 (fetch.py)"})
    for attempt in range(4):  # archive.org answers an occasional transient 5xx/429
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                data = r.read()
            break
        except urllib.error.HTTPError as e:
            print(f"{key}: HTTP {e.code}, retry {attempt + 1}", file=sys.stderr)
            if attempt == 3:
                raise
            time.sleep(5 * (attempt + 1))
    with open(path, "wb") as f:
        f.write(data)
    return path


def probe(path):
    try:
        out = subprocess.run(["ffprobe", "-v", "error", "-print_format", "json", "-show_format", path],
                             capture_output=True, text=True, check=True).stdout
    except (OSError, subprocess.CalledProcessError):
        return None, {}
    f = json.loads(out)["format"]
    return float(f.get("duration", 0)), {k.lower(): v for k, v in f.get("tags", {}).items()}


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    out, only_hash = sys.argv[1], "--hash" in sys.argv
    bad = 0
    for key, url, secs, sha in FILES:
        path = fetch(key, url, out)
        data = open(path, "rb").read()
        h = hashlib.sha1(data).hexdigest() if sha and len(sha) == 40 else hashlib.sha256(data).hexdigest()
        if only_hash:
            print(f'    ("{key}", {h!r}),')
            continue
        dur, tags = probe(path)
        ok = (sha is None or sha == h) and (dur is None or secs == 0 or abs(dur - secs) <= 2)
        bad += not ok
        print(("OK  " if ok else "BAD ") + key, f"{dur:.1f}s" if dur else "?", tags.get("title"), "|",
              tags.get("artist"), "|", tags.get("album"), "|", tags.get("date"))
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
