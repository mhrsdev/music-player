# Design review rubric

Use observable anchors rather than a ceremonial total score. Mark each relevant dimension **blocked**, **weak**, **resolved**, or **strong**, with evidence. Use **not inspected** for missing coverage and **not applicable** with a reason. These ordinal judgments are not calibrated measurements and should not be averaged into a certification.

| Dimension | Weak or blocked | Resolved | Strong |
|---|---|---|---|
| Task and hierarchy | Primary task hidden, competing actions, dead end | Main task and next step understandable | Scan path and recovery fit realistic use with little unnecessary work |
| Content and trust | Generic, misleading, missing consequence or fabricated proof | Specific wording and honest boundaries | Content resolves important questions precisely and supports confident decisions |
| Composition and identity | Accidental grouping, monotonous layout or unrelated decoration | Coherent grouping, proportion and visual language | Distinctive choices grow from the subject and remain coherent across states |
| Typography and color | Clipping, weak legibility, role confusion or failed required contrast | Readable roles and tested required pairings | Refined optical relationships, resilient scripts/modes and deliberate emphasis |
| Interaction and recovery | Broken task, unclear state, lost input or fake success | Relevant states and a complete working path | Errors, interruption and return are particularly clear and forgiving |
| Inclusion and adaptation | Keyboard blocker, hover dependency, unusable narrow view | Relevant keyboard, reflow, motion and locale checks pass | Actual assistive/platform testing supports broader confidence at a stated scope |
| Authorship and specificity | Stock page order, unexamined defaults, filler copy or invented proof | Habitual choices examined and each either justified by the subject or replaced | Choices traceable to this subject and still coherent across states |
| Implementation and performance | Regression, missing assets, incompatible API or unbounded effects | Project conventions respected and affected checks pass | Measured improvements with proportionate complexity and maintained behavior |

## Three-perspective post-build pass

Apply every relevant rubric dimension through all three perspectives in [the self-critique protocol](../references/self-critique.md):

| Perspective | Main question | Evidence boundary |
|---|---|---|
| Senior professional | Is the work well crafted, complete and justified against the brief? | Separate requirement violations and direct observations from aesthetic judgment |
| Complete beginner | Can someone with very little computer experience recognize the next action, understand feedback and recover without coaching? | Predicted confusion is a hypothesis until observed with a real person |
| Experienced user of similar tools | Does familiarity transfer, with efficient, predictable behavior and preserved work? | Ground comparisons in inspected tools or explicitly labeled conventional expectations |

Use one concrete task and a relevant recovery path. Account for partial or unavailable inspection. Merge duplicate defects while retaining which perspectives they affect; do not average away a blocker or invent three independent votes. Use [the record](../templates/self-critique.md) when useful.

Prioritize by consequence:

- **S0:** critical task blocked, severe misleading transaction state or comparable serious failure.
- **S1:** major friction, lost work, required access failure or important missing recovery.
- **S2:** meaningful local clarity, consistency or visual hierarchy problem.
- **S3:** optional refinement or contextual preference.

Authorship findings are usually S2 or S3, except invented proof, statistics or testimonials, which are content-and-trust failures and rank higher. Severity depends on the task; a small-looking contrast failure on a primary form can matter more than a large decorative mismatch. Distinguish a requirement violation from an aesthetic suggestion.

For each important finding use [the finding contract](../templates/finding.md). Preserve at least the important strengths relevant to a repair. Do not invent independent reviews or user preference data. Completion requires meeting the actual contract and disclosing unverified checks, not achieving a self-assigned perfect score.
