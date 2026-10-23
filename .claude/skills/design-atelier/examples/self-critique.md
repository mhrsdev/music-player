# Worked self-critique: reviewing the Persian booking fixture

**Mode:** Review only. This example applies the new protocol to the unchanged local booking fixture; proposed interface changes below are not implemented. Evidence is the existing source, the earlier fixture screenshots and the 1.0.0 browser report described in `BUILD-REPORT.md`. It is not a new browser test or a real user study.

**Task:** Choose a sample time, enter a sample name and phone number, review the request, then return to correct it. Inspect a missing-field or invalid-phone error as the recovery case.

| Perspective | Observation and evidence basis | Consequence / action |
|---|---|---|
| Senior professional | The source defines RTL layout, isolated LTR phone/time values, linked field errors, first-invalid focus and a local review state. Existing screenshots show the hierarchy; the previous browser suite covers input preservation and return-to-edit. | Preserve the calm hierarchy, honest service boundary and recovery. This supports a scoped artifact assessment, not a full accessibility sign-off. |
| Complete beginner | The primary button says “Review request” and the screen explains that nothing is reserved. Field labels do not explicitly state before submission that time, name and phone are all required. The absent required-field explanation is a source observation; possible first-use confusion is a hypothesis. | Consider a short visible required-field instruction. Check whether it improves understanding without crowding the form; do not claim a beginner was actually confused or succeeded. |
| Experienced user of similar tools | The earlier fixture checks support keyboard submission, editable review and preserved input. The source offers fictional times without a production calendar, timezone selector, service availability or server confirmation. | Preserve input and keyboard behavior. Do not misclassify missing production booking features as prototype regressions; revisit them if the brief becomes a real booking product. Comparisons here use conventional expectations, not a claim about a specific competitor. |

**Synthesis:** The beginner-oriented instruction can clarify the same short path without adding an onboarding wizard or removing keyboard efficiency. Its effect on wrapping and hierarchy would need a professional reinspection. A hypothetical user's confusion does not become an observed S0 failure merely because it appears in a persona pass.

**Coverage:** All three perspectives are inspected at the source/available-artifact level. Prior browser results are attributed to their original fixture revision. The new copy suggestion is proposed and untested; actual novice/expert behavior, screen-reader use and real booking services remain unverified.

**Next action in implementation mode:** If the requested scope includes improving the fixture, add the instruction, inspect its narrow-screen wrapping, recheck the error and review/edit path, and record the result for each affected perspective. In this review-only example, report the recommendation without modifying the interface.
