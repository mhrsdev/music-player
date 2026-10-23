# Data interfaces and expert workflows

Start with the decision the data supports. A dashboard should help a person detect, compare, explain or act. Four decorative KPI cards and a large area chart are not a substitute for understanding the operator's task.

## Information and density

Define metrics, units, periods, aggregation, freshness and source. Show comparisons only when meaningful. Separate actual, estimated and unavailable values; zero is not the same as missing. Use fictional fixture data only when clearly labeled. Never present prototype values as live business evidence.

Structure the first view around what requires attention. Keep identifiers, status, owners and next actions close together. Dense interfaces can be calm when alignment, type and grouping are consistent. Avoid spreading every small value into a large card or shrinking important text to fit an arbitrary grid.

## Tables and filters

Use tables for record comparison and stable columns. Align numbers appropriately, label units and keep headings unambiguous. Preserve useful identifiers when scrolling. Provide an alternative to hover-only row actions. Distinguish row navigation from selection so a click does not unexpectedly edit a record.

Make filters discoverable and their active state clear. Retain them through relevant navigation and provide a reset. Test combined filters, no results, pending updates and stale results. Pagination and virtualization should respond to real data scale and accessibility needs rather than being installed by default.

For bulk actions, identify the selected scope, including whether it spans pages. Make consequences and permissions explicit. A frontend permission state cannot authorize a server action. Preserve context after editing a row or resolving an exception.

## Charts

Use [charts](charts.md) for form selection, the jobs of color, marks, labels and interaction. In short:

Choose a chart according to the question: position or length for comparison, lines for ordered change, a meaningful diverging scale around a reference value. Avoid 3D effects that distort comparisons. Start bar magnitude axes at zero unless there is a clearly explained specialized reason; disclose truncated axes in other relevant contexts.

Provide labels, units and a useful text or table equivalent for essential information. Do not depend only on hue or hover tooltips. A mobile chart may need fewer ticks, a changed orientation or explicit summaries rather than illegible shrinkage.

## Resilience

Design loading, empty, error, stale, partial and offline states. Real-time updates need reconnection, freshness indicators and restrained announcements; avoid moving a row beneath the pointer during a consequential action. Keep the user's selection stable where possible.

Verify a realistic scan-and-act journey, a filter/reset path, a failure and a narrow view. Assess correctness and readability together; visual polish does not compensate for misleading metric definitions.
