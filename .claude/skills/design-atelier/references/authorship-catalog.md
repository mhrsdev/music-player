# Authorship catalog: tells and repair moves

A lookup for the [authorship pass](authorship.md). Each entry is a habit to look at, followed by the kind of decision that usually replaces it. Entries are ordered roughly by how soon a stranger notices them. Prevalence figures come from one analyst's unreviewed count of 500 Show HN sites in 2026 and are context, not law. A pattern that this subject chose is not a tell.

## First screen

- **Gradient hero in purple, indigo or violet, or a gradient-filled headline** (gradients of some kind appeared on about 28% of sites, a purple-filled call to action on about 11%). Ask where the palette came from. Build it from the subject's own materials and name each color's role.
- **Centered stack with a pill badge above the headline** (centered title about 24%, pill badge about 5%). Lead with the most characteristic thing in the subject's world, in whatever form fits: an artifact, a sample, a live piece, a headline that states the claim.
- **One styled word in the headline** (italic, serif, colored or underlined). Set the whole line in one treatment, or let the structure of the sentence carry the emphasis.
- **Near-black ground with one neon accent, glow or halo** (an always-dark page with gray body text about 20%). Keep dark only if it suits the product, and then design its contrast and depth on their own.
- **A metric hero**: a huge number, a small label, supporting stats. Lead with a figure only when it explains the product and you can source it.
- **A decorative grid, dot or stripe background.** Keep grids for maps, canvases and measurement tasks.

## Type

- **One neutral sans for every word** or a short list of the faces generated pages favor (Inter, Roboto, Geist, Plus Jakarta Sans, Space Grotesk, Instrument Serif, Fraunces, Bricolage Grotesque, Sora, Syne; the last few were about 16% of sites). Choose for the subject's reading and character, and keep an established brand face.
- **A small tracked all-caps or monospace label above each heading** (all-caps labels about 11%). Remove it, or fold its useful words into the heading. Keep a label that carries information the heading does not.
- **Meta strings such as "A · B · C", "WORD — fragment" labels, arrows on every link.** Delete what the subject does not need.
- **01, 02, 03 markers** (numbered steps about 9%). Number a real sequence only.
- **Flat hierarchy, crushed tracking, or oversized headlines that fill the screen alone.** Vary size, weight and spacing for the reading path.

## Color and surface

- **Unchosen gradients, gradient borders, gradient blobs.** Use a flat color with a named role, or one gradient that solves a real layering problem.
- **Blur and glass with nothing behind them** (about 17%). Use depth where layers actually overlap.
- **Border, soft shadow and tint stacked on every card.** Pick one way to separate a surface.
- **The brand color on many surfaces, the default success green, a dark theme made by inversion.** Reserve the accent for the action, derive status colors from the palette, and design the second theme separately ([color](color.md)).
- **Warm cream, serif and terracotta; near-black and one neon; tinted near-black as "black".** Fine when the brief or brand owns them. Otherwise derive the palette from the subject.

## Structure and pacing

- **The stock order**: hero, logo row, three icon cards, testimonial, pricing, FAQ, closing banner. Apply the claim test to each section and cut the ones with none.
- **Identical card grids** (three or six icon-topped cards about 20%) and **cards inside cards.** Group related ideas, give the primary one more room, or use rows or a table when the content is tabular. Three plans or steps that are truly parallel can stay.
- **Every section the same height, padding and heading size; equal gaps everywhere; everything centered.** Use less space within a group than between groups, and align by role.
- **Neighboring sections that differ only in their words.** Let each section take its shape from its content.
- **A statistics banner** (about 12%). Keep a figure only if it is real, sourced and relevant.
- **An FAQ accordion with generic questions.** Keep it when real questions exist; answer them in place when they are few.

## Components and motion

- **A tinted square or circle behind every icon; oversized icon tiles; one stock icon set at default weight.** Inline small icons, or drop the icons the content does not need.
- **A thick side or top stripe on rounded cards** (about 13%). A stripe means status; remove it unless there is one.
- **The same large radius on every card, button, input and dialog.** Assign radius by role.
- **The same fade-up on every block, a hover lift on every card, bounce easing, pulsing dots, count-up numbers, marquees.** Keep motion that answers a question about state, and stage one deliberate moment ([motion and spatial](motion-spatial.md)).
- **Emoji as icons, bullets or navigation markers** (emoji nav about 4%). Use a consistent icon set or none.

## Copy

Vocabulary tells shift with each model generation and a single word proves nothing, so rely on the principle: vague, promotional, interchangeable language with no particulars.

- **Filler and promotional words** such as elevate, unlock, seamless, streamline, supercharge, empower, robust, leverage, vibrant, pivotal, tapestry, boasts, nestled, groundbreaking. Say what a person can do and what the product does.
- **Contrast slogans** ("Not a feature. A platform.", "not just X but Y"). State the useful distinction directly.
- **Importance puffery, vague attribution ("experts say"), copula avoidance ("serves as", "features"), hedge-and-reassure, an aphoristic closer.** Replace with the plain claim and its proof.
- **Three-part lists everywhere, and sentences of uniform length.** Let the content decide how many items there are, and vary rhythm.
- **Round invented numbers, stock names (John Doe, Sarah Chen, Acme), unsourced testimonials, logo rows, ratings.** These are trust failures and rank above style ([content and assets](content-assets.md)). Use supplied facts, labeled samples or nothing.
- **An em dash in most sentences, title case on every heading, emoji in headings.** Weak alone, because rates differ by model. Use ordinary punctuation and sentence case.
- **"Get started" or "Learn more" on every button.** Name what happens next.
- **A label that repeats its heading, a paragraph that restates the one above it.** Say it once.
- **Not evidence by themselves:** correct grammar, plain or formal prose, a transition word, a mix of registers.

## Imagery and illustration

- **Abstract blobs, circles and decorative shapes; hand-coded scenes or mascots that look unfinished; plastic, over-symmetrical illustrations.** Use imagery specific to the product, a well-made drawing, or none.
- **A fake dashboard of gray bars, a fake terminal with invented latency, a sparkle logo, a "beta" pill.** Show a real screen or an honestly labeled sample.
- **A photo under a near-opaque wash, jagged "torn" masks, empty placeholder boxes.** Use a clean crop or leave it out.

## Completeness and behavior

- **Links that go nowhere** (`href="#"`, a missing anchor target), buttons made of `div`s, and calls to action with no destination. Wire them, or say plainly which destinations are missing.
- **Hover-only actions, no empty, loading, error or disabled states, desktop-only layouts, desktop-only focus handling.** See [interaction](interaction.md) and [responsive design](responsive.md).
- **Placeholders left behind**: lorem, TODO, example.com, stock image URLs, a fake payment form. Replace or flag them.
- **In repository work:** comments that restate the line below, invented API fields, defensive checks around impossible cases, a second helper beside an existing one. Reuse what exists ([delivery](delivery.md)).

## Data and charts

Drawn from general visualization practice, not from studies of generated charts: library-default or rainbow palettes, too many series or slices, a second value axis, a truncated bar axis, color-only meaning, tiles of invented metrics, and no table equivalent. See [charts](charts.md).

## Right-to-left pages

Evidence here is thinner. Vendor write-ups and forum reports describe layouts coded with physical left and right values (`margin-left`, `text-align: left`) so the page is left-to-right with translated words, directional icons and progress bars that do not mirror, and a missing `dir` attribute. The rest follows from [localization](localization.md): letter-spacing on connected script, tracked all-caps labels carried over although Persian and Arabic have no case, Latin-only font stacks that fall back for Persian glyphs, and copy that reads like a translation. No reliable list of Persian copy clichés was found and detectors are unreliable for Persian, so a native-speaker review is the check.

## Across a set

When several pages or artifacts come from the same hand, look for a shared ground, display face, accent and structure. Vary them on purpose so the set does not read as one template.
