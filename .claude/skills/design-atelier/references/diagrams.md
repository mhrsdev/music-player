# Diagrams and explanatory drawings

A diagram earns its place when a cold reader can see a mechanism they would otherwise assemble from paragraphs: where data flows, which parts talk, what changes between two options, what state something moves through. If one sentence says it faster, write the sentence. A figure that restates its heading in boxes adds weight and no understanding.

## Draw the mechanism, not its name

A box labeled "cache" repeats what the prose already said. The path a request takes through it, the two stores it sits between and the arrow that disappears when it is removed carry the argument. Show the parts the decision turns on (the boundary being crossed, the hop being added, the data that moves) and leave out the rest. Match complexity to the stakes: a one-hop question is three boxes, while a migration that reroutes writes through a queue needs the queue, the writer, the reader and the ordering arrow. Neither forced minimalism nor an inventory of the whole system serves the reader.

## Compare by drawing the difference

When the figure supports a choice, the reader should be able to point at what differs: two structures side by side with their shared parts aligned, a before and an after, the single edge each option adds or removes. A separate labeled box per option that connects to nothing restates a list and does not compare.

## Make every mark say something

Label arrows with the verb or the data (`writes`, `invalidates`, `polls every 30 s`); an unlabeled arrow only says "related". Add a legend only when an encoding such as dashed, colored or doubled repeats; otherwise label the mark itself. Reserve color for the one element that carries the argument, such as the option favored or the hop under discussion, and check that it reads in both themes.

Reading direction follows the language. Latin flows left to right and top to bottom. For a Persian or Arabic interface decide deliberately whether the flow mirrors, keeping in mind that time axes, code, logos and domain diagrams may keep their direction, and that mixed-script labels need bidi isolation ([localization](localization.md)).

## Build it as inline SVG

Author it by hand with native shapes and text, without libraries. Size it by `viewBox` and let CSS scale it to its container (`max-width: 100%; height: auto`). Draw strokes, text and arrowheads in `currentColor` so the figure inherits the foreground in light and dark. Make arrowheads from a marker or a small polygon, never an image. Keep labels near 11 to 13 px at drawn scale and short, and put explanation in the caption. Align to a simple grid with shared baselines and even gaps, because eyeballed offsets read as noise.

Wrap the figure in `<figure>` with a `<figcaption>` stating its claim, and give the `<svg>` `role="img"` and an `aria-label` carrying the same claim. For a complex figure add a text description or a list of the relations nearby. Keep the SVG self-contained: no scripts, no styles inside it, no external images. Long decorative path data means the drawing wants a real graphics tool, so simplify instead.

## Ask yourself before delivering

- Cover the caption. What mechanism does a cold reader see?
- Delete each element in turn. Which one can go without changing the argument?
- Does every arrow say what moves? Do any lines cross that need not?
- At phone width and in the other theme, is the text legible and does the one colored element still read?
