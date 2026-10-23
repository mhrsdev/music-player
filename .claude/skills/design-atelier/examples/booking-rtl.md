# Worked example: Persian appointment request

**Brief:** Create a Persian, right-to-left appointment-request prototype with visible labels, a choice of time, validation and an honest local confirmation.

**Contract:** The person selects one fictional availability option and enters a name and phone number. The form identifies missing or invalid input, preserves entered data, then shows an on-page review. This is a local request preview; no appointment is reserved and no personal information leaves the page.

**Direction:** Calm violet emphasis, white fields, generous line height and a compact single-column form. The visual expression stays quiet because the task demands confidence and clear recovery. System font fallbacks include Persian-capable candidates; a commissioned typeface is not invented or downloaded without its license.

**Simpler alternatives:** A multi-step wizard would add navigation to a short task. A complex calendar would imply real availability and introduce calendar/time-zone requirements not supported by the fixture. Use plainly labeled sample day/time options and keep their demonstration status explicit.

**Localization:** document language and direction are Persian/RTL; layout uses logical properties; phone and time values use isolated LTR spans. Persian and Arabic numeral input is normalized for phone validation. The example does not claim to implement a production Persian calendar, international phone validation or timezone-safe booking engine.

**State contract:** default → validation failure with inline messages and focus on the first invalid field → corrected input → local request review → return to edit with input retained. There is no fake network delay or claim that a server accepted the request. The review message explicitly says nothing was sent or reserved.

**Critique target:** inspect Persian shaping and punctuation, long headings, 320px layout, keyboard radio selection, error linkage and the distinction between a local review and a real booking. Screen-reader and native-device testing remain separate checks unless actually performed.

**Artifact:** [Open the Persian demonstration](web/booking.html). The documentation remains English; the artifact's user-facing Persian is intentional localization coverage.
