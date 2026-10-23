# Responsive composition

Design for available space, content and input modality. Device labels are convenient test categories, not layout logic. A narrow desktop window and a phone may share width but differ in pointing accuracy, keyboard, browser chrome and viewing distance.

## Intrinsic foundations

Use flexible tracks, wrapping, appropriate minimums and `min-inline-size: 0` where shrinking is intended. Bound paragraph measure and overall content width independently. Let components react to their containers when the same component appears in a sidebar and a full-width page. Keep useful intrinsic fallbacks when container queries or advanced features are unavailable.

Set breakpoints when content relationships stop working: navigation becomes crowded, a summary no longer fits beside a form, or chart labels collide. Do not uniformly scale a desktop screenshot. On a phone, change the grouping or disclosure strategy while retaining the user's ability to compare and act.

## Edge conditions

Check long headings, untranslated strings, long identifiers, empty and very dense records, missing images and large user fonts. Avoid fixed-height cards that clip text. Reserve media dimensions to prevent layout shifts. Keep focused inputs and submission feedback visible when the on-screen keyboard opens.

Use dynamic or small viewport units according to intended behavior; a full-height composition can be unstable under mobile browser chrome. Account for safe-area insets without padding every element arbitrarily. Test short landscape or laptop windows as well as tall portraits.

Tables and complex diagrams may need an intentionally scrollable region. Give it an understandable context, preserve key identifiers and keyboard access, and avoid making the whole page overflow. Do not hide important columns without another way to access their information.

## Input and movement

Use hover/fine-pointer capabilities only for enhancements. Touch users need visible actions and comfortably spaced targets. Preserve native page scrolling and browser navigation. Reduced motion should retain information, orientation and a deliberate composition.

## Evidence

Choose a representative matrix based on the task: for example 320, 390, 768 and 1440 CSS pixels, plus a short-height window, increased text size and relevant locale. These are starting points rather than universal mandatory device counts. An effective 320px viewport checks much of reflow, but it does not independently verify every browser-zoom or assistive-technology behavior.

Capture comparable states, wait for fonts and required resources, and inspect both full frame and details. Use overflow detection to locate offenders rather than adding `overflow-x: hidden` to conceal defects. Clearly distinguish desktop browser emulation, a real mobile browser and a physical device performance measurement.
