# Web, mobile and desktop

Clarify the target platform, input methods, supported versions and deliverable. A native mobile specification, browser prototype and production desktop application are different outputs. Preserve the platform and framework already chosen unless a change is part of the task.

## Web

Support semantic HTML, links and URLs, browser history, responsive layout and progressive enhancement where practical. Use available input capabilities rather than guessing from a device name. Keep optional visuals from blocking content. Honor the project's browser support and dependency versions; advanced APIs need compatible fallbacks.

## Native mobile

Use established platform navigation, controls and accessibility APIs when they meet the need. Consider back behavior, safe areas, system text size, dark mode, keyboard avoidance, touch reach, lifecycle, offline transitions and permission prompts. Request permission at a meaningful point and explain its purpose. Avoid moving a familiar primary action merely to make the design appear original.

Apple point sizes, Android density-independent units and CSS pixels are not interchangeable specification units. Platform target guidance, dynamic type and screen-reader behavior require checks in the actual stack. A browser viewport screenshot cannot certify VoiceOver or TalkBack behavior in a native app.

## Desktop software

Consider resizable windows, minimum usable bounds, multiple panes, menus, keyboard shortcuts, focus traversal, selection and undo. Expert workflows often benefit from density and persistent context. Do not replace every desktop action with an oversized touch control or hide menus solely for visual simplicity.

Support high-DPI rendering and platform conventions. Avoid hijacking system shortcuts. An offline desktop shell still needs explicit data durability, save/recovery and update behavior; a convincing static canvas does not establish those capabilities.

## Cross-platform systems

Share semantics, product vocabulary and role-based tokens while allowing platform-native interaction. Consistency should mean a coherent experience, not pixel-identical controls on incompatible platforms. Specify which layers are shared and where adaptation is deliberate. Reuse existing framework primitives, inspect installed versions and test the actual supported targets.

## Delivery boundary

If a target runtime is unavailable, provide a reviewable specification: navigation, screen hierarchy, states, component choices, sizing behavior, accessibility obligations and verification steps. Mark the unrun platform checks explicitly. Do not report a web mockup as a compiled or tested native application.

For actual design-tool or platform-tool calls, load the host's required skills. This module does not replace changing tool APIs, official platform guidelines or project rules. Native mobile and desktop content in this package is guidance; the packaged smoke demonstrations are browser-based only.
