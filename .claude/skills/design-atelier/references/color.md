# Color as a semantic system

Begin with explicit brand constraints and functional roles. Define canvas, surface, foreground, muted foreground, border, action, focus and relevant status tokens. Color names can describe primitives; component code should usually consume roles so light, dark and high-contrast modes remain coherent.

## Build hierarchy

Choose temperature and chroma in relation to the subject, surrounding media and audience context. Color psychology is a cultural and situational hypothesis, not a universal scientific mapping. Use the strongest emphasis where a person needs to notice or act. Several equally saturated elements can destroy hierarchy even when each looks attractive alone.

Explore alternatives when a palette is unresolved. Test candidates on actual text, buttons, surfaces, charts and failure states; swatches hide important interactions. Do not always generate five palettes or change an approved brand simply to create variation.

## Contrast

For WCAG 2.x AA, normal text generally needs 4.5:1 and large text 3:1. The large-text threshold is 18pt regular or 14pt bold, approximately 24 CSS px or 18.67 CSS px. Relevant non-text UI components and graphical objects generally need 3:1 against adjacent colors where the criterion applies. Account for exemptions and the actual context rather than applying a single number to every decorative line.

Use the WCAG relative luminance formula on rendered foreground/background combinations. Transparency, images, gradients and overlays require checking the actual composited background, potentially at several points or states. A palette file alone cannot establish every rendered contrast ratio. APCA may inform supplementary work; do not substitute it silently for a required WCAG 2.x test.

Communicate status with text, icons, position or pattern as well as color. Test required distinctions in grayscale and consider color-vision differences; simulations are approximate and do not replace usable redundant cues.

## Perceptual spaces and themes

OKLCH can help construct perceptually organized ramps. Check the output gamut and actual contrast; equal numeric steps are not automatically a usable token system. Preserve an sRGB fallback before optional wide-gamut enhancements.

Design dark mode rather than inverting the light palette. Reassess surface separation, image treatment, borders, status intensity and focus. Respect an explicit user preference and avoid unreadable hydration flashes. Support forced-colors behavior; do not disable system adjustments without a justified local reason.

Record the tested token pairs and states. Report measured ratios only when calculated, including the method and relevant limitations. “Accessible palette” is too broad when only a primary button was checked. Use [accessibility](accessibility.md) for the wider interaction obligations.

## Themes in code

Declare every color role as a token on `:root` first, with the light values. Redefine only the tokens for dark, once under `prefers-color-scheme: dark` guarded so an explicit light choice still wins, and once under an explicit dark attribute so a toggle works in both directions, setting `color-scheme` there so native controls and scrollbars follow. A color defined only inside a dark block leaves the light theme without a value, and a literal color inside a component works in one theme only. Give `body` an explicit background token, because a host may paint its own ground behind a transparent page. Start from [the token template](../templates/theme-tokens.css), then compute text, muted text, action and focus pairs in each theme with `scripts/contrast.py --css FILE --pair fg:bg` instead of trusting swatches.
