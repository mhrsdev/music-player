# Information architecture and journeys

Organize by the user's questions, objects and tasks. Repository modules and internal teams rarely provide a good navigation vocabulary. Give the main object a stable name across navigation, actions, breadcrumbs and feedback.

## Establish hierarchy

List the critical jobs and what users need before acting. For each page, define its main purpose, entry points, next action, essential context and exit. Place related information close together and separate groups according to meaning. A collection of equal cards is inappropriate when one issue is urgent and the others are supporting context.

Choose navigation depth according to the information space. A small site may need a short header; complex software may need stable sections, local navigation and search. Avoid adding all three by default. Keep location and selection apparent. Support deep links and the browser's back button where relevant; a useful view should survive refresh if the product contract calls for persistence.

## Model the journey

Describe entry → understanding → decision → action → confirmation → follow-up. Include returning users, partially completed work, permission restrictions and failure. Decide which state belongs in a URL, saved account preference, durable record or temporary component. Avoid resetting filters or input after an incidental navigation.

Onboarding should collect only information needed to reach the first useful outcome. A stepper is justified when stages differ meaningfully or reduce complexity, not because every form needs a wizard. Let users review consequential choices and go back without losing work.

## Density and disclosure

Use progressive disclosure for secondary or conditional detail. Keep essential costs, constraints and consequences visible before commitment. Hiding everything behind accordions can increase work and obstruct comparison. Dense expert tools can expose more information than occasional-use public forms; density should preserve legible distinctions and a clear scan path.

Map status and ownership directly to the places users decide. For operations software, an exception list and its next action can matter more than aggregate totals. For marketing, an unsupported metric is less valuable than a clear explanation of what the product actually does.

## Verify

Walk through at least the requested critical journey and a relevant failure or recovery path. Confirm labels predict destinations, navigation reflects the current location, action names remain consistent and completion has an understandable next step. If a link, route or service is out of scope, label that boundary rather than building a convincing dead end.

Use [interaction](interaction.md) for state behavior and [product patterns](product-patterns.md) for domain-specific obligations. Do not force every application into a hero/features/pricing/FAQ sequence.
