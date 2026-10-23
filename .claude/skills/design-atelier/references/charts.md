# Charts and data graphics

Begin with the question the viewer must answer, as in [data interfaces](data-interfaces.md). A chart helps when position, length or slope lets someone see a relationship faster than a table would. When the real message is one number with its comparison, a sentence or a small table is the stronger design. Choosing no chart is a design decision, not a failure to deliver.

## Match the form to the job

Name the job before the geometry. Comparing magnitudes calls for aligned bars from a zero baseline. Change over time calls for a line with the endpoint emphasized. A part of a whole works with few parts labeled directly; otherwise rank them as bars. Distribution needs marks that expose spread. Polarity around a reference needs a diverging scale. Two measures related to each other need a scatter. Avoid 3D and decorative gradients on marks.

Use one value axis. A second axis lets the designer pick two scales that appear to cross or track each other, which is a claim the data may not support. When two measures differ in scale, draw two aligned charts, small multiples, or index both to a shared start.

## Give color one job at a time

Decide color last, after the form and labels already work in grayscale. The job picks the rule.

- **Identity (categorical):** a few clearly different hues in a fixed order. A color belongs to the entity, never to its rank or position, so a filter that removes one series must leave the others unchanged; otherwise the viewer relearns the legend at every click. A ninth series is not a ninth hue. Fold the tail into "Other," split into small multiples or add a second channel.
- **Magnitude (sequential):** one hue from light to dark, darkest for the largest value. A rainbow invents boundaries the data does not have.
- **Polarity (diverging):** two hues leaving a neutral gray midpoint at the meaningful reference (zero, target, average). A hue at the midpoint implies a direction that is not there.
- **State (status):** good, warning, serious and critical belong to status alone and are never borrowed as "series four". They travel with an icon or a word, because hue alone fails under color-vision differences, glare and print.

Test the palette instead of trusting the eye. Neighboring series should stay separable in grayscale and under simulated color-vision deficiency; where they are separable only by hue, add direct labels, marker shapes, dashes or texture. Marks that carry meaning need about 3:1 against their surface, and text 4.5:1 (`scripts/contrast.py`). If the `dataviz` skill is installed, its palette validator also measures color-vision separation; without it, say that this check was not run.

## Mark and label craft

Thin marks leave room for comparison. Give bars a small gap, lines roughly two pixels, markers enough size to see without dominating, and a thin surface-colored ring where marks overlap. Anchor bars to a visible baseline. Keep grid and axes recessive so the data carries the contrast. Label directly where it helps (line ends, the largest bar, the exception) and never every point. Show a legend whenever there are two or more series; with one, the title is the legend. Set text in the theme's ink tokens, not in the series color, so legibility does not depend on hue. Put units where the eye lands, state period, aggregation and freshness, and disclose any truncated axis.

## Interaction and alternatives

Hover and focus reveal exact values: a crosshair with a tooltip for lines and areas, a per-mark tooltip for bars, points and cells, with hit areas larger than the marks. Hover never holds the only copy of the information. Provide a table or text equivalent for essential data and let the keyboard reach the same values. Filters sit together above the charts and show what is active. Keep missing distinct from zero in the marks, the tooltip and the table. On narrow screens reduce ticks, change orientation or summarize rather than shrinking the plot until it is unreadable.

## Themes and finishing

Dark mode is a fresh selection, not an inversion: choose each step again against the dark surface, recheck contrast and keep the same entity-to-color mapping in both modes. Chart text and gridlines come from theme tokens ([color](color.md)). After building, render the chart and look at it. A palette check measures color, not layout; label collisions, clipped axis text, a wrong baseline and overflow appear only in the render.

## Ask yourself before delivering

- Cover the title. Can you still say what the chart claims, and which mark makes the claim?
- Strip the color. Can you still tell the series apart and read the order?
- Remove one series. Do the remaining colors stay the same?
- Does the first place the eye lands match the point the message depends on?
- What could be misread: zero against missing, a truncated axis, mismatched periods?
