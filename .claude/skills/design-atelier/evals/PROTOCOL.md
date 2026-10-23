# Evaluation protocol

The bundled examples are author-run smoke exercises. The helper tests establish narrow deterministic behaviors. Neither demonstrates that a model using the skill designs better than the same model without it. The task set below is provided for that separate experiment; unexecuted cases remain unexecuted.

## Task coverage

`evals.json` contains design tasks spanning new creation, constrained polish, reference uncertainty, read-only review, error recovery, RTL, dense data, accessibility, native platforms, motion and truthful content. The first three correspond to author-built demonstrations; the remaining cases provide broader future evaluation coverage. `trigger-cases.json` contains positive and near-miss host-trigger cases. The optional router is a lexical reference suggester after task selection, not the host's skill-trigger classifier.

Version 1.1 adds four development cases for three-perspective self-critique, novice/expert conflicts, review-only scope and unavailable rendering. The task set had twenty-four design tasks at 1.1.0. Version 1.3 adds two more (31, 32) for the authorship pass; they have not been run. Version 1.2 adds six development cases (25 to 30) for the landing-page pair, chart color and themes, comparison diagrams, sandboxed single-page tools, a Persian chart and self-diagnosis, each with objective assertions. Case 25 was run once per condition by subagents; see the 1.2.0 trial in `BUILD-REPORT.md`, which is a single-pair result and not a statistical comparison. The others have not been run. These four cases have not been run as model trials. When evaluating the new behavior, inspect actual artifacts and any repair/recheck evidence; counting three persona headings or a high self-score is insufficient. Assess whether each perspective asks a different useful question and whether observed issues are separated from predicted user behavior.

## Controlled comparison

Use an identical model version, tools, initial files, task and relevant limits for each with-skill/without-skill pair. The baseline receives the same user/project requirements without this skill. Use isolated workspaces, preserve prompts and outputs, and do not give the baseline advice learned from the skill's output. Alternate execution order and repeat enough cases to understand variance. Do not claim meaningful statistical significance from a tiny sample.

Measure actual input/output tokens, wall time, failures and executed checks from host/API records when available. Word counts are not model token measurements. Report missing usage data as missing, including whether reasoning or cached tokens are visible. Account for tool and context-loading costs rather than counting only the entry file.

## Objective review

Test the task-specific acceptance conditions against the output. Include critical journey completion, real state transitions, input preservation, required constraints, relevant viewport behavior and truthful service boundaries. Capture commands, environment, exit codes and artifacts. Do not count skipped checks as passes. The evidence helper validates declarations and hashes, not the underlying truth of execution.

## Visual and product review

Render matched content, viewport, state, fonts and motion setting. Conceal variant identity and order-randomize comparisons when practical. Have independent human evaluators assess task fit, clarity, identity, coherence and preference using the rubric. Record disagreements and reasons; “more distinctive” and “easier to use” may conflict. Do not replace independent review with a model's own high score.

Keep a development subset separate from held-out tasks used for final reporting. Do not revise the skill around a held-out result and still label that result unseen. A quality gate failure remains visible even if an aesthetic preference favors the same output.

## Reporting

Publish the exact tested revision, task count, repeated runs, environment, evaluators, objective results, preference results, usage data and uncertainty. Disclose failures and incomplete coverage. Only report a token-saving or quality-improvement percentage when the experiment actually supports it. Native specifications, emulated mobile pages, real devices and production usage require distinct labels.
