# Standalone and published pages

Use this when the deliverable is one self-contained page that a host publishes, such as an HTML artifact, a shared prototype or a landing page served from a sandbox. The host's published contract outranks this note. Read it first and treat what follows as the kinds of limits to expect.

## Design inside the sandbox

A sandboxed host usually restricts what a page may load or do. Typical limits: external scripts only from a short allowlist of CDNs at a pinned version, stylesheets only from one font service, everything else inlined with images embedded as data URIs; no `alert`, `confirm` or `prompt`; no `window.print`; downloads started by the page blocked; `mailto:` and `tel:` links unreliable; no third-party iframes or device APIs without an explicit capability; storage that may be empty or throw.

Build the constraint into the design. Put a confirmation step on the page, show an address or number as selectable text with a copy control and a fallback for when the clipboard is refused, and use real links for navigation. Wrap storage reads and writes in try/catch so the page is right without it, and keep storage for per-viewer conveniences. State that other people must see belongs in the shared-state capability the host provides, not in one viewer's browser. Never say a message was sent because a viewer tapped a link that may have done nothing.

## Be complete at rest

The first still frame is what a thumbnail, a shared link and a skimming reader see. Everything meant to be read is visible on load, with no scroll trigger needed. An element may animate in, but from a visible resting state, never held at zero opacity until an observer fires. Size a hero to its content instead of the viewport height so the next section is not pushed out of the first frame. A tool opens in a realistic working state, with sample rows, a loaded example or a plausibly filled form, labeled as sample data, because an empty shell shows nothing about what it does.

## Themes belong in the tokens

Write the design plan into the file as a token block: the palette as a handful of named roles, the type roles, and a one-line comment naming the layout idea. Declare every token on `:root` first with the light values. Redefine only the tokens for dark, once under `prefers-color-scheme: dark` guarded against an explicit light choice and once for an explicit dark choice, setting `color-scheme: dark` there so form controls and scrollbars follow ([template](../templates/theme-tokens.css)). Give `body` an explicit token background, since hosts paint their own ground behind a transparent page. Components read tokens only, because a literal color that works in one theme fails in the other. A page may commit to a single look on purpose, but it still sets the background and every color explicitly.

## Name, fit and finish

Name the page like a product: two to four specific words, neither a category label nor a caption. Put the explanation in the description field the host provides. Keep a side gutter of at least 16 px at every width and clear any safe-area insets. Let rows wrap or stack at about 400 px. Give content that can outgrow its track `min-width: 0`, and let wide tables or code scroll inside their own container so the body never scrolls sideways. Use `gap` for spacing between siblings, tabular figures where digits align, a visible focus state and reduced-motion support, and give every form control a stable id. Write words from the user's side of the screen: a control says exactly what happens and an error says what to fix. Prefer plain, specific sentences over mannered ones.

## Depth follows consequence

A published single page or a prototype wants one honest look at the render at phone and desktop widths in both themes, one round of repairs, and delivery. A product in a repository, a multi-step flow, or anything that handles data or money wants the full [three-perspective loop](self-critique.md). Say which one you did.
