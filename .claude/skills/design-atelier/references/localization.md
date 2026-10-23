# Localization, Persian and bidirectional interfaces

Treat language as a content, typography and interaction requirement. Preserve meaning, state and task completion across locales. Set document language and direction appropriately; use logical layout properties such as inline start/end instead of hardcoding left/right everywhere.

## RTL is not a flipped screenshot

Decide the reading and interaction order for the actual locale. Keep DOM order coherent with navigation and focus. Mirror directional affordances when their meaning depends on direction; retain logos, photographs, media controls and domain diagrams when mirroring would change their meaning incorrectly. Time and spatial axes need domain-specific decisions rather than a global transform.

Isolate mixed-script content with `bdi`, suitable `dir` values and explicit LTR treatment for URLs, code and machine identifiers. Test punctuation around a Persian sentence containing an English product name, version number, price and path. Avoid storing display-formatted digits as the canonical data representation.

## Persian type

Verify actual Persian glyph coverage, joining, half-spaces, diacritics and baseline relationships. Avoid character splitting and letter spacing that damages connected script. Animate wrappers or complete language-safe groups instead. A named Latin font with a fallback is not proof of deliberate Persian typography; inspect the actual rendered fallback and document it.

Use language-appropriate line height and realistic text. Test long labels at 320px and with increased text size. A Persian heading may need different wrapping and weight from its English counterpart while preserving the same hierarchy.

## Dates, numbers and forms

Choose calendar, numerals, time zone, currency and unit display from product requirements. Persian language does not automatically imply a specific calendar or time zone for every user. Use locale-aware formatting and verify the actual result. Preserve machine-readable IDs and validate any conversion at the boundary.

For scheduling, distinguish a date, local wall time, time zone and an absolute instant. Include ambiguity and unavailable slots in the interaction contract. Accept appropriate numeral input where feasible, normalize deliberately and keep validation messages localized. Do not ask for unnecessary sensitive information simply because a form template contains it.

## State and verification

Switching languages should retain appropriate user progress, selection and location. Recompute affected measurements after fonts or text change. Check focused fields, dialogs, errors and long data labels in both directions, rather than inspecting only navigation alignment.

Use a native speaker review for final public copy when required; disclose if the available review was only model-authored. The included booking example is a local Persian interaction exercise using system font fallbacks, not a verified production calendar or medical booking service.

## Persian page implementation notes

Set `lang="fa"` and `dir="rtl"` on the document and write spacing with logical properties. Wrap embedded Latin names, prices, times and versions in `<bdi>`. Keep letter-spacing at zero for connected script and give body text generous line height, often around 1.7 to 2, then confirm with the real font. Choose a Persian-capable family on purpose: families such as Vazirmatn or Noto Sans Arabic are candidates on common font services, but confirm in the render that پ چ ژ گ ک ی and the half-space between word parts shape and join correctly, because a font can claim Arabic coverage and still mishandle them.

Format with `Intl.NumberFormat` and `Intl.DateTimeFormat` and pass the calendar and numbering system explicitly, since the Persian language does not decide them. Keep stored values in a neutral form. For charts and diagrams, decide deliberately whether axes and flow mirror, keep numerals and timestamps readable, and isolate mixed-script labels. Treat these as starting points to verify in a render. They do not replace a native-speaker review.
