# Inclusive access

Treat accessibility as a design and behavior obligation, not a decorative checklist at the end. Target the project's required standard; WCAG 2.2 AA is a useful web baseline when no other requirement is supplied. A targeted check is not a certification of full compliance.

## Structure and operation

Provide meaningful landmarks, headings and names. Use semantic elements and native controls first. Maintain a logical DOM order, visible focus and keyboard access to the critical task. Provide a way past repeated navigation where needed. Do not remove outlines without an equally effective replacement, place focused content under sticky overlays, or require hover, precise dragging or a specific pointer.

Distinguish focus, selection and activation. Composite widgets have pattern-specific keyboard behavior; inspect established platform or WAI-ARIA patterns before building one. ARIA cannot repair an interaction model merely by assigning a role. Use live regions for meaningful asynchronous feedback, avoiding repeated announcements of every minor visual change.

## Perception and layout

Check actual text and relevant non-text contrast using [color](color.md). Avoid color-only meaning. At narrow effective widths, content and controls should reflow without page-level two-dimensional scrolling, except where a genuinely two-dimensional representation needs it. Support text enlargement and user spacing adjustments without clipped content.

WCAG 2.2 AA target size is generally at least 24 by 24 CSS pixels or sufficient spacing, subject to criterion exceptions. Larger targets, often around 44–48 CSS pixels, improve comfortable touch use and may be required by a platform. Do not claim the larger comfort recommendation is the universal WCAG minimum.

Give informative images useful text alternatives, video appropriate captions and important audio a text equivalent. Motion-sensitive users need a useful reduced-motion experience. Avoid flashing hazards; long automatic movement may require pause controls. Content should remain available if optional effects fail.

## Forms and cognitive load

Use visible labels, predictable wording and explicit error recovery. Preserve input. Avoid unnecessary repeated entry and memory demands. Authentication and timeouts may have additional standard-specific requirements; do not claim to have checked them after inspecting only colors.

## Verification and limits

Combine automated scanning, keyboard journeys and manual inspection. Verify accessible names and focus behavior, zoom/reflow, reduced motion, representative contrast pairs and failure states. A screen-reader session on the target platform provides evidence automation cannot supply. If it was not performed, say so.

Document each observed failure with state, reproduction, affected task and a correction. Do not average a blocked keyboard task into an overall passing score. Report “automated scan passed on these routes” at its actual scope; do not shorten it to “fully accessible.”
