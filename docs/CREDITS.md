# Sample-data credits

`fake/SampleData.java` (the in-memory library behind the screenshots, galleries and unit tests) uses **only the metadata**
(title, artist, album, year, duration) of the tracks below and the **cover images** listed further down. No audio file is
committed or shipped: `tools/sample-data/fetch.py` re-downloads the audio (URLs + SHA-256) to verify the metadata.
The covers live in `app/src/debug/assets/sample-covers/` (debug builds only, 512x512 JPEG, never in the release APK).
Sample lyrics in the app are placeholder text written for this project, not the songs' real lyrics.

## Tracks

| # | Title | Artist | Album (year) | Licence (SPDX) | Source | Required attribution |
|---|-------|--------|--------------|----------------|--------|----------------------|
| 1-5 | Texas; 1000 Words; Keep In Touch; IOU; Skyline of the Sea | Josh Woodward | Reject Bin (2024) | CC-BY-4.0 | https://archive.org/details/reject-bin | Josh Woodward, "Reject Bin", CC BY 4.0 |
| 6-9 | I Can Be Your Love; We Start the Cure in Paris; If Only She Knew; God's Will | Chris Zabriskie | O Great Queen Electric, What Do You Have Waiting for Me? (2008) | CC-BY-4.0 | https://archive.org/details/cz-ogreatqueenelectric | Chris Zabriskie, CC BY 4.0 (chriszabriskie.com) |
| 10-12 | Aria; Variatio 1 a 1 Clav.; Variatio 2 a 1 Clav. | Kimiko Ishizaka | The Open Goldberg Variations (2012) | CC0-1.0 | https://archive.org/details/The_Open_Goldberg_Variations-11823 | none required (CC0); Kimiko Ishizaka, Open Goldberg Variations |
| 13-16 | Sunglasses; Sensual Melancholia; Dirty Shoes Blues; The Candle | Loyalty Freak Music | Instrumental R&B Beats to Sing or Rap On (2018) | CC0-1.0 | https://archive.org/details/LoyaltyFreakMusic-INSTRUMENTALRBBEATSTOSINGORRAPON | none required (CC0) |
| 17-18 | Go to the Picnic; Hug Convoy | Loyalty Freak Music | Positive Attitude! (2017) | CC0-1.0 | https://archive.org/details/LoyaltyFreakMusicPOSITIVEATTITUDE20170920175908839 | none required (CC0) |
| 19-21 | Night Owl; My Always Mood; Day Bird | Broke For Free | Directionless EP (2011) | CC-BY-3.0 | https://archive.org/details/DirectionlessEP | Broke For Free, "Directionless EP", CC BY 3.0 |
| 22-24 | Sneaky Snitch; Carefree; Wallpaper | Kevin MacLeod | Royalty Free (2010) | CC-BY-4.0 | https://incompetech.com/music/royalty-free/music.html | "Title" Kevin MacLeod (incompetech.com) Licensed under Creative Commons: By Attribution 4.0 https://creativecommons.org/licenses/by/4.0/ |
| 25 | دختران قوچان (Dokhtaran-e Quchan; poem by Ali-Akbar Dehkhoda) | آریا طبیب‌زاده (Arya Tabibzadeh, kamancheh and voice) | دختران قوچان (2023) | CC0-1.0 | https://commons.wikimedia.org/wiki/File:Dokhtaran-e_Quchan-_دختران_قوچان.ogg | none required (CC0); Arya Tabibzadeh |
| 26 | ای ایران (Ey Iran), santoor | روح‌الله خالقی (Ruhollah Khaleghi, composer; santoor recording by Wikimedia Commons user "Iran-persian", 2017) | ای ایران (2017) | CC-BY-SA-4.0 | https://commons.wikimedia.org/wiki/File:Ey_Iran_(santoor)_by_Ruhollah_Khaleghi.ogg | Recording: Iran-persian, CC BY-SA 4.0, via Wikimedia Commons |

Notes: Josh Woodward's "Reject Bin" track numbers are the real ones on disc 1 (Skyline of the Sea is no. 19). Kevin MacLeod's
files carry no track number. Durations come from `ffprobe` on the downloaded files (rounded to whole seconds).

## Cover art (all public domain, resized to 512x512 JPEG)

| File | Used for | Work | Source (Wikimedia Commons) | Licence |
|------|----------|------|----------------------------|---------|
| `reject-bin.jpg` | Reject Bin | Katsushika Hokusai, The Great Wave off Kanagawa (c. 1830-32) | File:Katsushika Hokusai - Thirty-Six Views of Mount Fuji- The Great Wave Off the Coast of Kanagawa - Google Art Project.jpg | PD (author died 1849) |
| `o-great-queen-electric.jpg` | O Great Queen Electric... | Edvard Munch, The Scream (1893) | File:Edvard Munch, 1893, The Scream, oil, tempera and pastel on cardboard, 91 x 73 cm, National Gallery of Norway.jpg | PD (author died 1944) |
| `goldberg.jpg` | The Open Goldberg Variations | Johannes Vermeer, The Music Lesson (c. 1662) | File:Johannes Vermeer - Lady at the Virginal with a Gentleman, 'The Music Lesson' - Google Art Project.jpg | PD (author died 1675) |
| `rnb-beats.jpg` | Instrumental R&B Beats... | Vincent van Gogh, Cafe Terrace at Night (1888) | File:Vincent Willem van Gogh - Cafe Terrace at Night (Yorck).jpg | PD (author died 1890) |
| `positive-attitude.jpg` | Positive Attitude! | Pierre-Auguste Renoir, Luncheon of the Boating Party (1880-81) | File:Pierre-Auguste Renoir - Luncheon of the Boating Party - Google Art Project.jpg | PD (author died 1919) |
| `directionless-ep.jpg` | Directionless EP | Caspar David Friedrich, Wanderer above the Sea of Fog (c. 1817) | File:Caspar David Friedrich - Wanderer above the sea of fog.jpg | PD (author died 1840) |
| `royalty-free.jpg` | Royalty Free | J. M. W. Turner, Rain, Steam and Speed (1844) | File:Turner - Rain, Steam and Speed - National Gallery file.jpg | PD (author died 1851) |
| `dokhtaran-quchan.jpg` | دختران قوچان | Young man in European dress playing a lute, Iran, 1630s (Smithsonian F1929.77) | File:Young man in European dress playing on a lute, Iran, 1630s.jpg | PD (anonymous, 17th century) |
| `ey-iran.jpg` | ای ایران | Kamal-ol-Molk, Hall of Mirrors (1896) | File:Hall of Mirrors - Kamal-ol-molk - Golestan Palace.png | PD (author died 1940) |

Crops and offsets: `tools/sample-data/covers.py`. Faithful photographs of public-domain 2D works add no new copyright.
