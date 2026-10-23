# Mowj / موج — brand

**Mowj** (Persian *موج*, "wave", pronounced *mowj*) is the app's name. Sound travels as a wave, and the player's signature control is the Wavy seek bar, so the name and the UI describe the same thing. Package id stays `com.mhrsdev.musicplayer`.

## The mark

A single round-capped stroke: a gentle swell (the Wavy seek bar) rises and curls over into a breaking wave. The curl is also the head of the Persian letter **م**, the first letter of موج, so the mark carries the name in both scripts without containing text.

- Master geometry: `src/mark.svg` (69 × 40 design box, stroke 8, round caps and joins). Every other asset derives from this path; keep `src/brand.js`, `res/drawable/ic_launcher_foreground.xml` and `res/drawable/ic_stat_playback.xml` in sync with it.
- The mark always flows left to right, in LTR and RTL layouts alike. Do not mirror it.

## Colours

| Token | Hex | Use |
|---|---|---|
| Lapis | `#24548C` | icon background start (top-left) |
| Turquoise | `#0E8C84` | icon background end (bottom-right) |
| Foam | `#F2FAFF` | mark on the icon tile and on dark surfaces |
| Ink | `#0F1216` | wordmark on light, app dark background |
| Paper | `#F7F9FF` | light background |
| Mark gradient (light bg) | `#2F5F95` → `#0E8C84` | standalone mark / wordmark on light |
| Mark gradient (dark bg) | `#A6C8FF` → `#7FD6CB` | standalone mark / wordmark on dark |

Foam on the lightest corner of the tile is 3.9:1, above the 3:1 needed for graphics. These colours are the UI scheme's own lapis/turquoise (docs/design/DESIGN.md); Android resources are in `res/values/brand_colors.xml`.

## Type

Wordmark: **Mowj** in Google Sans Flex, weight 640, tracking −2%; **موج** in Vazirmatn ExtraBold. These are the app's own fonts (`res/font/`). The `.svg` wordmarks are not provided on purpose: the PNGs are the reference, rendered with the real fonts.

## Usage

- **Clear space**: at least half the mark height on every side (the icon tile needs no extra space beyond the platform grid).
- **Minimum size**: mark 16 px tall (screen) / 5 mm (print); app icon 24 px. Wordmark lockups need at least 120 px width; below that use the mark or icon alone.
- **One colour**: `src/mark-mono.svg` (currentColor). It is also the themed-icon (`<monochrome>`) layer and the status-bar icon.
- Do: use the tile icon on busy or photographic backgrounds; use Foam or Ink for single-colour placements.
- Don't: mirror, rotate, outline, add drop shadows to the mark itself, change the stroke weight, put the light-bg gradient on dark surfaces, or set the name in other typefaces.

## Files

| File | What |
|---|---|
| `logo.svg`, `logo.png` (512) | app icon tile (rounded-square presentation) |
| `logo-wordmark-en.png`, `logo-wordmark-fa.png` (+ `-dark`) | lockups, transparent background |
| `social-preview.png` (1280 × 640) | GitHub social preview |
| `launcher-icon-preview.png` | design sheet: masks, themed, small sizes |
| `launcher-icon-render.png` | the real adaptive icon resources rendered by Robolectric (`LauncherIconRenderTest`, output in `app/build/`) |
| `src/` | SVG masters, HTML sheets, `render.mjs` |

Re-render: `PLAYWRIGHT_BROWSERS_PATH=/opt/pw-browsers node docs/brand/src/render.mjs [name ...]`.

## Android

- Launcher: `mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml` (adaptive; gradient background vector, mark foreground inside the 66 dp safe circle, same mark as `<monochrome>` for Android 13 themed icons). minSdk is 26, so there are no PNG mipmaps.
- Status bar: `drawable/ic_stat_playback.xml`.
- Splash (Android 12+): `values-v31/themes.xml` shows the launcher icon on the theme background; below 31 the starting window is the plain theme background.
