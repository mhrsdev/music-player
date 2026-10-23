# Mowj / موج — brand

**Mowj** (Persian *موج*, "wave", pronounced *mowj*) is the app's name. Sound travels as a wave, and the player's signature control is the Wavy seek bar, so the name and the UI describe the same thing. Package id stays `com.mhrsdev.musicplayer`.

## The mark: M ~ م

A bilingual monogram. The Latin **M** (left, read left to right) and the Persian **م** (right: a closed round head with its tail falling from the lower left) are joined by one wave line that both letters stand on. It holds the name in both scripts at the same weight, and the wave between them is the meaning of the name.

- Chosen from the comparison sheet `logo-options.png` (option **M2**). Runner-up: **M4** (a large م with an M cut out of its head). Every option, including round 1, is kept in `options/` as SVG so another one can be picked later.
- Geometry: three round-capped strokes, weight 7 on the 108-unit icon canvas (about 10% of the 66 dp safe zone, so there are no hairlines). Master: `src/options.js` (`M2`); exported to `src/mark.svg`, `src/mark-mono.svg` and `logo.svg` by `src/export-options.mjs`. Keep `res/drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml` and `ic_stat_playback.xml` in sync with it.
- The mark reads M then م whatever the layout direction. Do not mirror it.

## Colours

| Token | Hex | Use |
|---|---|---|
| Lapis | `#1D4A80` | icon background, top left |
| Lapis-teal | `#1B6787` | icon background, middle |
| Turquoise | `#0E8C84` | icon background, bottom right |
| Glow | `#9EF2E7` at 38% → 0 | soft radial light from the top left of the tile |
| Foam | `#FFFFFF` → `#CDEFEA` | the mark on the tile (top to bottom), with a 28% `#05182C` offset shadow under it |
| Ink | `#0F1216` | wordmark on light, app dark background |
| Paper | `#F7F9FF` | light background |
| Mark gradient (light bg) | `#2F5F95` → `#0E8C84` | standalone mark / wordmark on light |
| Mark gradient (dark bg) | `#A6C8FF` → `#7FD6CB` | standalone mark / wordmark on dark |

Contrast of the mark on the tile is at least 3.35:1 (foam-deep on turquoise, the weakest pair) and 6.3:1 in the middle, above the 3:1 needed for graphics. Android resources are in `res/values/brand_colors.xml`.

## Type

Wordmark: **Mowj** in Google Sans Flex, weight 640, tracking −2%; **موج** in Vazirmatn ExtraBold. These are the app's own fonts (`res/font/`). The wordmarks are delivered as PNG rendered with the real fonts (no fontTools here to outline them into SVG).

## Usage

- **Clear space**: at least the height of the م head (about a third of the mark height) on every side.
- **Minimum size**: mark 16 px tall on screen / 5 mm in print; app icon 24 px. Wordmark lockups need at least 120 px width; below that use the mark or the icon.
- **One colour**: `src/mark-mono.svg` (currentColor). The same strokes are the themed-icon layer and the status-bar icon.
- Do: use the tile icon on busy or photographic backgrounds; use Foam or Ink for single-colour placements.
- Don't: mirror, rotate, outline, split the two letters apart, change the stroke weight, put the light-bg gradient on dark surfaces, or set the name in other typefaces.

## Files

| File | What |
|---|---|
| `logo-options.png` | comparison sheet: bilingual M + م family (M1–M5) + one non-letter control, with verdicts |
| `options/` | every direction as `<id>-<name>.svg` (colour tile) and `<id>-mono.svg`, plus `logo-directions-round1.png` |
| `logo.svg`, `logo.png` (512) | app icon tile (rounded-square presentation) |
| `logo-wordmark-en.png`, `logo-wordmark-fa.png` (+ `-dark`) | lockups, transparent background |
| `social-preview.png` (1280 × 640) | GitHub social preview |
| `launcher-icon-preview.png` | design sheet: masks, themed, small sizes, status-bar icon |
| `launcher-icon-render.png` | the real Android resources rendered by Robolectric (`LauncherIconRenderTest`; it writes `app/build/launcher-icon-render.png`, copied here) |
| `src/` | geometry (`options.js`, `brand.js`), HTML sheets, `render.mjs`, `export-options.mjs` |

Re-render: `node docs/brand/src/export-options.mjs && PLAYWRIGHT_BROWSERS_PATH=/opt/pw-browsers node docs/brand/src/render.mjs [name ...]`.

## Android

- Launcher: `mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml`. Adaptive: `ic_launcher_background` (3-stop linear gradient + radial glow), `ic_launcher_foreground` (mark with offset shadow and gradient stroke, scaled 0.84 so every corner is inside the 66 dp safe circle), `ic_launcher_monochrome` (mark only, for Android 13 themed icons). minSdk is 26, so there are no PNG mipmaps. The `-v26` folder is required (see `app/lint.xml`).
- Status bar: `drawable/ic_stat_playback.xml` (same strokes, 22 dp wide).
- Splash (Android 12+): `values-v31/themes.xml` shows the launcher icon on the theme background; below 31 the starting window is the plain theme background.
