# Worked example: an operations desk

**Brief:** Build a local shipment exception interface. An operator needs to find a delayed record and inspect the next action. Favor direct scanning over a decorative analytics hero.

**Contract:** Search, status filtering, clearing filters and opening/closing record details work on fixture data. No live shipping system, authentication or real customer records are connected.

**Direction:** A compact workbench with a stable record table, clear state labels and restrained blue actions. Numeric summaries derive from the same fixture array as the rows; they do not imply live business metrics. Text and status dots redundantly encode state.

**Simpler alternatives:** Large record cards would reduce direct cross-record comparison. A chart would show aggregates without helping the specified next action. Use a semantic table and a detail dialog; keep the established tabular relationship in an intentional narrow-screen scroll region.

**Interaction contract:** search filters names and IDs; status combines with search; zero results explains how to recover; reset restores the full dataset; a row action opens details for the correct record; Escape and Close dismiss the modal and return focus to the opener. The current scope and fixture boundary remain visible.

**Tradeoffs:** The demonstration does not edit records or persist filters across browser sessions. Those capabilities should be added when the product job requires them, rather than fabricated as silent functionality. Data freshness and real server failures remain specification concerns outside this local fixture.

**Critique target:** evaluate the scan path from status to owner and next action, filtered-empty recovery, modal focus and useful table access at narrow width. Ensure a table's deliberate overflow is not mistaken for unwanted page overflow.

**Artifact:** [Open the operations demonstration](web/operations.html). Data is explicitly fictional.
