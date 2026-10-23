# Mowj theming, type and motion (M1)

## Theme
- `Theme.Mowj` (`res/values/themes.xml`) extends `Theme.Material3.DayNight.NoActionBar` and maps every M3 colour
  role (primary/secondary/tertiary + containers, error, surface containers, outline, inverse, state layer) onto the
  lapis/turquoise scheme (DESIGN.md 2.1). `Base.Theme.MusicPlayer` (the app themes) inherits it; Black overrides
  the surfaces. Stock Material components (dialogs, switches, snackbars, menus) therefore look like Mowj.
- Shape families: `ShapeAppearance.Mowj.ExtraSmall/Small/Medium/Large/ExtraLarge` = 6/12/16/24/32 dp.
- Snackbar: `Widget.Mowj.Snackbar`, opaque inverse surface, 16 dp.
- Floating chrome tokens: `?attr/mpSurfaceFloating` / `?attr/mpOnSurfaceFloating`, always opaque (nav dock,
  mini-player, snackbar, sheets). Never draw a floating surface with alpha < 1.

## Wallpaper colours (Material You) - contract for the Settings screen
- `ThemeController.isDynamicColorSupported()`: API 31+.
- `ThemeController.isDynamicColor(Context)`: persisted choice, key `dynamic_color` in the settings prefs
  (`data.settings.SettingsStore#isDynamicColor/setDynamicColor`), **default off** (lapis stays the identity).
- `ThemeController.setDynamicColor(Activity, boolean)`: persists and recreates the activity.
- `MainActivity` calls `ThemeController.applyDynamicColor(this, black)` right after `setTheme`. It applies
  `ThemeOverlay.Mowj.Dynamic` (`values-v31`, `values-night-v31`): `system_accent1/2/3` and `system_neutral1/2`
  tones mapped onto our `mp*` tokens and the M3 roles. Black mode applies `ThemeOverlay.Mowj.DynamicAccents`
  (accents only, black surfaces kept).
- Strings: `mp_setting_dynamic_color`, `mp_setting_dynamic_color_summary`, `mp_setting_dynamic_color_unsupported`
  (en + fa). Show the row disabled with the "unsupported" summary below API 31.
- "Colors from artwork" is unchanged and still tints the player/mini-player on top.

## Type roles
| Role | Family (res/font) | ROND | Weight |
|---|---|---|---|
| Display / Headline | `mp_sans_round` | 100 | 700 / 600 |
| Title / Label / Lyrics | `mp_sans_soft` | 60 | 500 |
| Body / Supporting | `mp_sans` | 20 | 400 |
| Numeric | `mp_sans` + `tnum` | 20 | 500 |

XML uses `TextAppearance.MusicPlayer.*`; code uses `ui/theme/TypeRoles` (cached per role, never per bind).
`res/font-fa/` maps the same family names to Vazirmatn (variable wght, no roundness axis).
The Latin font is rebuilt by `tools/fonts/build_sans_subset.py` (keeps `wght` 400-800 and `ROND` 0-100; ~117 KB).

## Motion (`ui/motion`)
- `Spring`: closed-form damped oscillator (exact for any frame time, no allocation). Presets:
  SNAPPY (900, 0.82) presses / nav, BOUNCY (520, 0.55) pills / pop, GENTLE (240, 1.0) sheets (no overshoot),
  JIGGLE (700, 0.3) state changes.
- `SpringAnimator`: one float on the shared `FrameDriver` (single Choreographer callback, stops when idle);
  retargeting keeps velocity. `SpringInterpolator`: a preset as a `TimeInterpolator` + its settle duration.
- `Motion`: tokens (durations, GLIDE/LIFT/EASE curves, amplitudes) and the jiggle vocabulary:
  `press/release`, `attachPress(view, scale)`, `jiggle(view)` (1 -> 0.92 -> ~1.03 -> 1), `pop(view)` (heart).
- `PageTransitions.tab/push`: fade-through for tabs, shared X axis for drill-downs (mirrored in RTL), Animator XML
  so Fragment predictive back can scrub them. The full player keeps its own cover flight.
- Policy: `Motion.enabled()` = `MotionPolicy.decorativeMotionEnabled()`: app reduce-motion, animator scale 0 or
  battery saver make every spring snap, skip jiggles and page transitions.
- `ShapeMorph`: circle / squircle / rounded square / cookie / pill as equal-count point outlines in the unit box,
  mirror-symmetric, morphable point by point, rebuilt into a reused Path.

## Play/pause
`MorphPlayPauseView.PAUSE_STYLE` picks the pause look (A squircle + round bars, **B circle (default)**,
C breathing blocks, D rounded square). See `docs/screens/m1-pause-options.png`.
