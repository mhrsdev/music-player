# Motion, scroll, gestures and 3D

First establish a readable, functional static state. Add movement when it communicates change, maintains orientation, demonstrates the product or contributes intentional atmosphere. Motion is optional unless requested or necessary; do not animate all headings by default.

## Motion contract

Identify the trigger, owned property, start/end state, duration behavior, interruption and reduced-motion alternative. Keep business state independent of animation completion. An operation succeeds when the application knows it succeeded, not when a checkmark finishes drawing.

Use CSS or native transitions for straightforward effects, an installed animation library when it adds needed capability, and another engine only with a clear reason. Assign one owner to each animated property. Clean up timelines, animation frames, observers and listeners on teardown. Avoid permanently running loops offscreen.

Prefer responsive feedback over delay. Exact duration depends on distance, device, context and platform conventions; a universal preset is insufficient. Stage a coherent moment rather than adding separate reveal effects to every element. Reduced motion should present all information and useful feedback in a deliberate composition.

## Scroll and gesture

Use one normalized source of progress for coordinated effects. The same progress should produce the same scene when approached forward or backward. Recalculate geometry after resize, fonts or locale changes. Test anchors, browser back, fast scroll, short heights and reduced motion. Do not trap scroll or hide essential content behind a long pinned sequence.

Pointer effects need bounded motion, coarse-pointer fallbacks and fixed semantic hit targets. Handle pointer cancellation and provide alternatives for meaningful drag actions. Do not hide the system cursor or attach audio without a clear, appropriate user interaction.

## Spatial necessity

Before introducing 3D, identify what it communicates better than an image or ordinary interaction. Provide equivalent meaningful content and controls outside the canvas. Set explicit device and loading budgets. A poster fallback should preserve the task when WebGL/WebGPU, assets or motion are unavailable.

Inspect asset rights, size, geometry, material and texture requirements. Match library versions and renderer features. Use instancing, bounded DPR, appropriate LOD and compressed textures when warranted. Pause idle/offscreen rendering and dispose resources. Profile actual hardware before making GPU performance claims.

Frame the scene in the actual available slot. Consider projected bounds, text safe regions, aspect ratio and camera limits rather than shrinking a desktop canvas. Test fallback, context loss and restoration when spatial functionality is in scope.

No packaged example requires a 3D dependency. These are specialist decision and verification requirements, not a claim that every advanced graphics technique has been implemented or tested by this skill.
