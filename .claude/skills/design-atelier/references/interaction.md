# Interaction, forms and recovery

Design the state transition before its animation. Identify the trigger, precondition, state change, feedback, cancellation and recovery. For each requested critical action, connect the control to a real destination or explicit local demo behavior.

## Semantics and input

Use a link for navigation and a button for an action. Prefer native controls when they meet the need. Custom widgets require equivalent keyboard, touch, focus and name/role/value behavior; a clickable `div` is not a complete button. Hover can enhance a control but must not expose the only path to essential information.

Choose input types, autocomplete and keyboard hints deliberately. Keep visible labels; placeholders do not replace them. Mark required and optional fields clearly. Provide format help before errors when the required format is unusual. Validate at a useful point, preserve entered values, and connect field errors to their controls. For multi-field failure, an error summary can link to the fields; move focus when that improves recovery rather than repeatedly interrupting typing.

## Asynchronous truth

An action begins, remains pending, succeeds or fails according to durable application state. Announce relevant changes without flooding a live region. Disable or otherwise guard duplicate submission when consequences require it. Do not use a timer to imply server success. Distinguish first-use empty, filtered empty, unavailable, stale, partial and offline states with the correct next action.

Include retry semantics: can retry duplicate the operation, and is the result already known? Preserve work during recoverable failure. An optimistic update needs a clear rollback and error path. For a local prototype, make the simulated boundary apparent in user-facing wording where it affects trust.

## Consequences and permissions

Use proportionate safeguards for destructive or expensive actions. Undo is often better for reversible changes; explicit confirmation fits consequential, difficult-to-reverse actions. Describe the specific consequence. Do not add confirmation to every harmless click. Distinguish permission restrictions from an empty dataset and offer a legitimate next step; UI hiding does not replace server authorization.

## Dialogs and gestures

Dialogs need a name, initial focus, sensible keyboard containment, closing behavior and focus restoration. Do not trap focus in a nonmodal panel. Dismissal should protect unsaved work according to its consequence. Sticky overlays must leave the focused control visible.

Dragging needs a usable non-drag alternative when required by the task/accessibility contract. Handle Escape, pointer cancellation, blur and unmount so interactions do not become stuck. Preserve page scrolling outside the intended gesture area.

Verify the complete journey using both pointer and keyboard. Test a relevant failure, back/cancel path and return to the starting context. Use [the state template](../templates/state-contract.md) when a flow is complex enough to benefit.
