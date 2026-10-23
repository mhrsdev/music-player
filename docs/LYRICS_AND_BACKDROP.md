# Lyrics word sync and the living backdrop (L1, D19)

What runs, when, and how much it costs. The owner's hard rule: this must not undo the app's battery and RAM work, and it is our own implementation, not a copy of any other player.

## Data

| Source (resolver order, unchanged) | Word timing |
|---|---|
| Embedded tag: ID3 SYLT | SYLT entries that carry their own newlines are words or syllables. They are written as enhanced LRC (`[line]<t>word<t>word`), so the word timing survives the existing text cache |
| Embedded tag: USLT, Vorbis `LYRICS`, MP4 `©lyr` | Enhanced tags inside the text are parsed |
| Sidecar file, same base name: `.ttml`, `.elrc`, `.lrc`, `.LRC`, `.xml` (only if it is TTML), `.txt` | TTML `<p>`/`<span begin end>` (Apple style, nested spans walked through). `.elrc` is enhanced LRC or a simple JSON (`{"lines":[{"time","text","words":[{"start","end","text"}]}]}`) |
| Cache, then LRCLIB (online toggle, negative cache and TTLs unchanged) | `syncedLyrics` with enhanced tags keeps the real timing |

- **LRC variants:** `<mm:ss.xx>` enhanced tags, A2 (the first word has no tag and starts at the line time), inline `[mm:ss.xx]` word stamps, and Walaoke `M:/F:/D:` prefixes (removed). A tag right after a unit marks its end. A last unit with no end tag ends at the next line, at most 3 s later.
- **Format sniffing** (`LyricsParser`): TTML, then JSON, then LRC/plain. The TTML reader is a small tag scanner with no DTD or entity expansion (XXE-safe), and it runs in JVM tests.
- **Model:** `LyricLine.words` (a list of `LyricWord{startMs,endMs,text,start,end}`, or null) plus `estimated`. The old `new LyricLine(time, text)` still works.
- **Emulated timing** (`WordTimingEstimator`): applies to synced lines that have no word timing. The line is sung from its stamp for `min(gap − tail, syllables × 330 ms + 250 ms)`, where tail = 10 % of the gap, clamped to 120–700 ms. Inside that window each word's weight is its syllable count: Latin vowel groups, about one per two letters for Persian/Arabic, one per character for CJK, plus pauses after `, ، ؛` (short) and `. ! ? ؟ …` (long). The last word is held ×1.35. The result is deterministic. It is computed once per lyrics load on the loading thread and stored in the `Lyrics` value, so the UI never estimates. Plain lyrics stay plain.

## Rendering (`ui/player/lyrics`)

- **`LyricLineView`**, one per visible line in the existing RecyclerView, which keeps tap-to-seek, manual scroll, the 3 s pause and "Back to current line". Each line builds a `StaticLayout` once per text, width and size. Direction comes from the line's first strong character, so Persian lines run RTL and align right even in an English song. Width is capped at 560 dp for tablets and landscape.
- **Active line, per frame:**
  1. A dim base pass.
  2. Fully sung words in full colour, using one reused clip `Path` and one draw.
  3. The word being sung: lifted up to 2.5 dp on a bell curve, with a dim copy and a full-colour copy masked by a moving gradient edge. There is one `LinearGradient` per text colour, and only its local matrix changes.
  Emulated lines use a wider, softer edge and a smaller lift, so they read slightly softer than real timing. No allocation happens per frame.
- **Other lines:** alpha falls with distance from the active line (1 → 0.60/0.52 → … → 0.35). Scale is 0.94 → 1 on our spring curve when a line becomes active. **Blur:** `RenderEffect.createBlurEffect` on the line's own RenderNode, with the radius growing by distance (max 7 dp). The effects are cached in half-pixel buckets, so a line changing distance only swaps a cached object. The GPU blurs a few short lines, never the screen. The blur is applied only to the lines RecyclerView keeps attached (the viewport, plus ±6 lines of falloff).
- **No blur** (alpha + scale falloff only) below API 31, in battery saver, with reduced motion or animator scale 0, and on low-RAM devices (`LyricsEffects.blurAllowed`).
- **One frame loop** (`LyricsTicker`): `postOnAnimationDelayed` at ~30 fps. It runs only while the lyrics list is shown and attached, the window is visible, and the player is playing. It stops on pause, when the lyrics are hidden, on `onStop` and on `onDestroyView`. Each frame does a binary search for the active line and invalidates the active line's view only.
- **Smooth position** (`LyricsClock`): the player still publishes position 4×/s (unchanged). Between ticks the clock extrapolates with uptime, by at most 1 s.
- **Per-track offset:** ±0.5 s from the ⋮ menu, stored in a tiny prefs file and written only when the user changes it. **Text size:** A−/A+ from the ⋮ menu, using `SettingsStore.lyricsTextSize` (22/26/31 sp). The active line sits at 36 % of the list height, reached with a spring-curve `smoothScrollBy`.
- **Lyrics button:** shown only when the current track has lyrics (`LyricsAvailability`). This reuses the repository's single lazy lookup for the current track: local sources, then the cached online answer, then at most one LRCLIB request with the online setting on. That lookup already runs only while the full player is visible, and queued tracks are never looked up. The button is INVISIBLE rather than GONE, so the centred pill group does not jump. If the track changes to one without lyrics, the lyrics view returns to the cover. Opening is blocked from the cover tap and the menu.

## Living backdrop (`PlayerBackdropView`, shared by the player and lyrics)

- **Colours:** four colours are taken once per track from the quadrants of the existing 20×20 cover downscale (about 1.6 KB, no bigger bitmap), with a slight saturation lift.
- **Colour field:** four unit-radius `RadialGradient`s in those colours drift on slow sine paths (periods 19–31 s, offsets ≤ 12 %/9 %) and breathe ±6 %. Only the local matrices change per frame. This sits over the existing tiny-art layer and under the radial glow and the guarded scrim. **ContrastGuard is unchanged and still decides the scrim.** The blobs are the cover's own average colours at 70 % layer alpha, so they do not move the measured luminance much. The ⚠ caveat is under "Unverified" below.
- **When it moves:** at ≤ 15 fps (66 ms), only while the player is visible, playing, and decorative motion is allowed (`setAnimating` from the state observer; stopped in `onStop` and on detach). Otherwise one still frame is drawn. Track changes crossfade the layers (alpha only, 600 ms).

## Shared ambient backdrop (`ui/widget/AmbientBackdropView`, owner: "the background should change with the song and the content")

- **Engine:** `AmbientField`, the same four-blob engine the player uses.
- **Colours:** `AmbientPalette` = ColorExtractor seed + quadrant averages of the 32x32 downscale. Cached per artwork key (LRU, 48 entries). Contrast guard: lightness is clamped (dark L ≤ 0.42, light L ≥ 0.72), so white/onSurface text keeps 4.5:1.
- **Modes:** `SOFT` (headers, Home glow) is static and never animates except the one cross-fade on `setSource`. `FULL` (player) drifts at ≤ 15 fps only while `setAnimating(visible && playing)` is on, the view is shown, and motion is allowed.
- **Cross-fade:** one `ObjectAnimator`, 600 ms, alpha only. None under reduce motion or battery saver.
- **Settings:** "Colors from artwork" off gives the neutral accent from ThemeController.
- **Artwork:** loaded at 96 px through the shared `ArtworkLoader` cache. The view keeps no bitmap.
- **Render:** `docs/screens/l1-ambient-soft-full-dark.png` (top: SOFT from a two-tone cover; bottom: FULL from a seed colour).

## Frame cost reasoning

| State | Work |
|---|---|
| Player hidden / screen off / paused | Nothing. Both loops stopped, no wakeups beyond the existing 4 Hz tick (which itself runs only while observed and playing) |
| Cover view, playing | Backdrop: ≤ 15 redraws/s of 1 rect + 1 tiny bitmap + 4 gradient rects + glow + scrim (~7 fills, no blur, no offscreen layer) |
| Lyrics view, playing | Backdrop as above, plus ≤ 30 redraws/s of ONE line view (3–4 `Layout.draw` calls of a 1–3 line layout). Blurred lines are not re-recorded. Their cached RenderNode + effect is recomposited by the GPU |
| Line change (every few seconds) | ~13 rebinds with property changes, plus one 460 ms spring per changed line |

RAM: no new bitmaps. There are at most 33 cached `RenderEffect` objects (only the used buckets are created), one `StaticLayout` per attached line, and 4 shaders per backdrop layer.

## Verification

- JVM: `WordSyncParsersTest` (enhanced/A2/inline/Walaoke/Persian/offset/TTML/JSON/SYLT/LRCLIB), `WordTimingEstimatorTest`, `LyricsAvailabilityTest` (fake clock and sources, cache hit, miss, negative, online toggle), `LyricsMotionTest`, `PlayerViewModelTest`.
- Robolectric renders, `docs/screens/l1-*.png`:
  - word 1 / word 3 / end of line (dark), light, Persian RTL
  - emulated timing
  - blur on: the hardware renderer through PixelCopy, so the real RenderEffect path. The other PNGs are the software canvas, which is the no-blur fallback
  - top row with and without the lyrics button (LTR, RTL)

**Unverified on a device:**
- The real fps and power draw.
- Blur cost on low-end GPUs (API 31+).
- Variable-font weight axes of the bundled fonts (`setFontVariationSettings` is ignored when an axis is missing).
- Contrast of the drifting blobs over extreme covers. The guard measures the tiny art and the container, not the blobs.
- Bluetooth-latency lyrics offset needs.
