# Typography and reading

Choose type for language coverage, context, role and actual rendering. A font name is not an identity strategy. Existing system fonts may be the right choice for an operational or native interface; a distinctive display face may help an editorial project when licensed and available.

## Roles and hierarchy

Define a modest set of roles: display if needed, page title, section title, body, supporting text and data. Establish hierarchy through size, weight, spacing and placement together. Do not make every label tiny uppercase or assign monospace to every metric. Use tabular figures when stable numeric comparison benefits from them.

Set comfortable body text for the platform and audience. Around 16 CSS pixels is a common web starting point, not a universal compliance threshold. A useful initial Latin paragraph measure is roughly 45–75 characters; test the actual script, font, column and reading situation. Give line boxes enough room for ascenders, descenders and diacritics. A heading's deliberate line break should survive small content and width changes.

## Scale and fluid behavior

Use rem-based bounds and restrained fluid scaling where appropriate. Test increased text size; a viewport-only size can undermine zoom. Avoid fixed-height text containers and indiscriminate `white-space: nowrap`. Use a small number of meaningful sizes rather than a unique value for each element.

Check small type at normal viewing size, not only in a zoomed screenshot. Weight and contrast that look elegant in a large mockup can become faint on a phone. Correct perceived alignment between icons and actual glyphs without moving the entire hit target.

## Language and font delivery

For Persian and Arabic scripts, inspect joining, half-spaces, diacritics and mixed Latin content. Avoid Latin-style letter spacing on connected script. Choose script-aware line height and fallback coverage. Use [localization](localization.md) for direction and numbers.

Load only necessary licensed faces and weights. Prefer appropriate WOFF2 assets, avoid speculative preloading of an entire family, and choose a `font-display` strategy based on legibility and layout stability. Font metric overrides can reduce shift when measured; do not paste arbitrary values. Verify the font actually loaded and examine a font-unavailable state.

## Motion and output

Animate wrappers or language-safe word groups if motion has a clear purpose. Preserve a coherent accessible text source; avoid duplicate spoken fragments and broken shaping. Important text must remain available if scripts or animation fail. Text in a 3D scene needs a semantic equivalent when it conveys meaning.

For handoff, specify role, family/fallback, real weight, size behavior, line height, measure and language exception. A screenshot of a font pairing does not prove its license, availability or behavior across scripts.
