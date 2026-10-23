# Implementation and delivery

Inspect project instructions, scripts, architecture, component exports and installed versions. Preserve the current framework, routing and data flow. Use generated tool output as a representation of intent, then translate it into the repository's conventions. Do not install a new stack to avoid understanding the existing one.

## Build the smallest complete change

Prefer content and grouping corrections before architectural work. Reuse native controls, existing primitives and installed dependencies where suitable. A local prototype can use simple HTML/CSS/JavaScript; a maintained product should follow its existing boundaries. Avoid overbuilding generalized systems for a single screen and avoid compressing logic until it loses readability.

Keep input validation, semantic behavior, error recovery and data integrity intact. Do not remove defensive behavior to reduce code or token counts. Add a meaningful check for nontrivial state logic and reproduced defects; do not create implementation-mirroring tests for every spacing edit.

## Assets and performance

Record source and rights for copied assets. Use responsive image sizes and formats with dimensions, prioritize only critical resources, and lazy-load appropriate below-fold content. Load licensed fonts and necessary weights deliberately. Keep optional animation and 3D code outside the critical path when possible.

Measure before making performance claims. Distinguish lab runs, browser traces, simulated frame-time inputs and real-user field data. Core Web Vitals depend on context and actual collection; a static page screenshot does not establish LCP, INP or CLS. Set budgets appropriate to the product rather than declaring one universal bundle or draw-call count.

## Trust boundaries

Do not expose credentials, inject unsanitized untrusted content or treat UI permissions as server enforcement. Inspect copied code and installer side effects. Follow the project's real authentication, privacy and backend contracts. Do not attach live services, publish, purchase or send messages unless that action is within the user's authorization.

## Handoff

Provide the working result or explicit specification, important decisions, component/token locations, changed behavior, actual checks and unresolved limitations. Label mock data and missing integrations where the distinction affects the user's decision. Give concise use instructions for interactive artifacts.

For significant work, retain a brief, state contract and compact decision log in the project only when useful or requested. Do not scatter process documents into a small change. The skill itself does not install hooks or write to host configuration. Its optional helpers are offline and inspect local files only.

Use [verification](verification.md) before claiming completion. Re-run affected checks after the final edit, not only before it.
