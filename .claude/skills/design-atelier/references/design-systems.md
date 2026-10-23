# Design systems and component contracts

Inspect the existing system before introducing one: tokens, component exports, usage in real screens, interaction primitives, dependency versions and ownership. Published names and actual usage can differ. Search authoritative code or library instances rather than concluding that a component is absent after one failed keyword query.

## Reuse with judgment

Prefer an existing component when it satisfies the task. Extend a documented variant when a recurring need is genuinely different. A single unusual editorial section may need local composition rather than a universal component. Avoid parallel implementations of dialogs, selects or date pickers whose behavior is expensive to maintain.

Keep structure, behavior and theme responsibilities clear. Reusing a headless primitive can preserve keyboard behavior while allowing identity in the styling layer. Do not override its focus management or semantics casually. Inspect the installed API instead of copying syntax from a different major version.

## Tokens

Use layers where useful: primitives → semantic roles → component-specific mappings. A small project may only need semantic CSS variables. Avoid both scattered magic colors and a token factory for a single spacing correction. Use shared type, spacing, radius, elevation and motion roles when they encode real repeated decisions.

Modes should preserve meaning across light/dark, density and language. A token named `action-primary` communicates a stable job; a hardcoded blue inside a component cannot respond coherently to every theme. Document exceptions and avoid automatic merging of similar colors with different semantic purposes.

## Component contract

Specify purpose, anatomy, variants, state ownership, events, accessibility, responsive behavior and content constraints. Decide controlled versus internal state explicitly. For asynchronous actions, distinguish disabled from pending and ensure duplicate submissions cannot produce misleading feedback. Provide labels, descriptions and errors through supported composition rather than adding unrelated ARIA everywhere.

For meaningful reusable components, show relevant states in the project's existing story or test system: normal, focus, selected, pending, empty, error, narrow container, long content, RTL and supported themes. Do not require an entire Storybook installation for a local prototype.

## Design/code continuity

When using a design tool, map actual component variants, text styles and variables to their code counterparts. Keep instances and variable bindings when the task calls for a maintained system. A collection of visually similar rectangles does not provide a component system. Follow the host's tool-specific skills for actual Figma operations; this reference is conceptual guidance, not an API contract.

Record where new tokens and variants live and how they are intended to be used. Avoid broad dependency upgrades or architectural replacement during visual polish. Verify an extension in at least one realistic page so the component contract survives composition.
