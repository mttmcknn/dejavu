# Evaluating DejaVu skills

The four bundled skills now have a repeatable evaluation workflow adapted from
[Chris Banes' evaluator](https://github.com/chrisbanes/skills/blob/2db11bb412254246bf0a5f6e5e320177107a7c53/evals/README.md).
See [the audit](audit-2026-09-09.md) for the concrete instruction defects corrected
in skill bundle 0.3.0, and [upstream provenance](upstream/README.md) for the code pin,
license and local adaptations.

## What this measures

Each case gets a fresh workspace and conversation under three arms:

| Arm | Skills available | Purpose |
|---|---|---|
| `none` | Public skills disabled | Baseline capability |
| `forced` | Only target skills, explicitly invoked | Instruction utility and over-application |
| `automatic` | All four, none named in task | Implicit routing and interference |

Default runs repeat each condition three times. Compare old and revised skills
on the **same model, reasoning, corpus, judge and execution environment**. Repeat
that comparison separately for each model you use. Different model results cannot
establish a skill-edit improvement. A strong baseline at ceiling is useful
compatibility evidence; examine restraint, unnecessary work and cost rather than
claiming nonexistent correctness uplift.

`skills_used` is **reported routing**, not independently observed activation. The
CLI used during development (0.144.5) does not provide a separate reliable skill
activation event. The judge receives the task, rubric, initial source, diff,
response and validator evidence without arm/routing metadata. A response can still
mention a skill name; inspect packets for incidental unblinding during human audit.

## Corpus and evidence limits

There are 15 synthetic source-excerpt cases: direct edit, novel read-only review
and authorized no-change control for each of four skills, plus three routing
reviews. They cover Android BOM enforcement and complete test setup, KMP version
constraints, existing-test augmentation, Wasm async lifetime, keyed-effect oracle
mistakes, deliberate excessive recompositions, dirty-bit diagnostic uncertainty,
mixed test-harness lifecycle, coalesced writes and upper-budget restraint.

`case.json` holds the routing, write scope, rubric and provenance. `prompt.md` is
arm-neutral; `overlay/` contains the subject's source. `expectations.json` belongs
only to the external grader. It is never staged for the subject.

The inexpensive deterministic validator checks protected inputs and required
artifacts/edits. It **does not prove semantic Kotlin correctness**. The separate
judge assesses the actual outcome against each criterion; success requires both.
These excerpts intentionally have no runnable Gradle wrapper. No score from this
initial corpus proves compilation, Android/device execution or KMP runtime accuracy.
Before promoting generated runtime/test changes, run their real project UI suite.
Future corpus additions should include executable fixtures and held-out snapshots
from immutable public revisions, with license and normalization notes. Keep those
out of prompt tuning; use separate calibration cases to investigate ceiling effects.

## Offline checks and CI

Python 3.11+ and Git are sufficient; no pip install, SDK, emulator or model account:

```bash
python3 validation/skills.py
python3 evals/run.py validate
python3 -m unittest discover -s evals/tests -p 'test_*.py'
```

The path-filtered `Skill contracts` workflow runs only these checks. Model scores
are advisory and never merge/release gates. The fake-CLI test checks orchestration
and is explicitly **not** behavioral model evidence.

## Preview and bound a live run

Authenticate a compatible Codex CLI using its normal login flow. The runner uses
`--ignore-user-config`, explicit skill configuration, disabled web search and
network-disabled tool sandboxes. Existing execpolicy rules remain active; do not
bypass a host restriction to obtain a score. Models still need their normal
provider connection. Review tasks use a read-only sandbox, edit tasks a workspace
sandbox with externally graded write allowlists. Subjects must not use external
services or escalation. This is an evaluation control, not a production skill rule.

Choose explicit model IDs supported by your account. As of this audit, useful
separate candidates are `gpt-6-astra` (frontier compatibility) and
`gpt-5.6-terra` (a lower-cost comparison that may expose more baseline headroom).
Use a suitably capable fixed judge, such as Astra with high reasoning, across
comparisons. Availability and performance must be verified for each run; there is
no silent fallback to another model. The first adapter is Codex; no Claude model
performance has been measured or implied.

```bash
python3 evals/run.py plan \
  --model gpt-6-astra --reasoning medium \
  --judge-model gpt-6-astra --judge-reasoning high
```

Full default matrix: **135 subject + 135 judge calls**. Start with one case:

```bash
python3 evals/run.py run \
  --case perf-budget-negative --repetitions 1 \
  --model gpt-6-astra --reasoning medium \
  --judge-model gpt-6-astra --judge-reasoning high \
  --output-dir .scratch/skill-evals/astra-current-smoke
```

`run` is still a preview. Add `--execute --max-calls 6` to authorize that six-call
matrix. The default cap is 12; larger matrices require an explicit higher cap.
There are no automatic subject/judge retries during `run`, so failures remain
visible and do not silently double the budget. Model-internal tool calls and
provider transport retries are not separately capped by this process count.
Optional `--subject-cost-per-call-usd` and `--judge-cost-per-call-usd` produce an
estimate from your supplied assumptions; no price table or dollar guarantee is
embedded. Progress and outputs persist locally under the selected directory.

## Compare old and revised skills

`--skills-ref` stages only the canonical skills from a Git revision into an
immutable local cache. The current harness and corpus remain identical:

```bash
python3 evals/run.py run \
  --skills-ref 9142685 --case perf-budget-negative --repetitions 1 \
  --model gpt-6-astra --reasoning medium \
  --judge-model gpt-6-astra --judge-reasoning high \
  --output-dir .scratch/skill-evals/astra-before-smoke
```

Add the same explicit execution/cap flags when ready. Run without `--skills-ref`
for the current files, using another output directory. Repeat both for Terra with
unchanged cases and judge settings. That bounded two-model, before/after smoke is
24 calls total; one case and one repetition cannot establish benchmark uplift.
Add positive edit/review and other no-change cases before drawing broader conclusions.

Raw records fingerprint case and fixture content, evaluator/validator/schema code,
repository commit, exact staged skill contents, external skill catalog, CLI and
model/reasoning settings. Matching runs resume; stale evidence is rejected. Keep
host/tool versions and environment stable, and record them in your audit. Do not
combine output directories or model configurations into one scorecard.

## Inspect results and audit

```bash
python3 evals/run.py report --output-dir .scratch/skill-evals/astra-current-smoke
python3 evals/run.py regrade --output-dir .scratch/skill-evals/astra-current-smoke
```

Each run writes raw evidence, blinded packets, `results.json`, `scorecard.md` and
`audit-queue.json`. Regrading creates separate results; it does not overwrite the
original. `judge` previews rejudging existing packets; its separate `--execute`
flag authorizes those judge calls (review that count separately from `run` caps).
`rejudged-report` requires a complete, unambiguous set of rejudgments.

The inherited advisory thresholds track positive outcome uplift, automatic
retention, reported routing precision/recall, no-change restraint and forbidden
actions. Missing categories do not pass; a smoke run cannot certify a skill.
Efficiency reports tokens, tool calls, turns and elapsed time, with failed work
included in cost per successful outcome. Judge cost is evaluation overhead.

Audit every objective/judge disagreement, within-condition inconsistency and the
deterministic 10% sample of other results. Check for vacuous tests, incidental
unblinding, unjustified edits, unsupported verification claims and model ceiling.

```bash
python3 evals/run.py audit \
  --output-dir .scratch/skill-evals/astra-current-smoke \
  --id 'perf-budget-negative:automatic:1' \
  --decision accept --rationale 'Reviewed source diff, response and rubric evidence'
```

Audit decisions supplement immutable evidence. Do not overwrite outcomes or tune
cases merely to improve the score. Keep raw local paths/tool transcripts out of
public reports unless reviewed; commit a concise sanitized result record stating
models, revisions, cases, repetitions, failures and limitations.

## Improvement cycle

Add an observed failure and a restraint control before revising a skill. Correct
the smallest decision-changing instruction, test routing descriptions, and move
conditional reference material out of the main skill body. Compare before/after
under each selected model; audit errors and extra work even when both pass.
Retain improvements supported by behavior and maintain the corpus when DejaVu or
Compose gains a new supported contract. Keep library accuracy tests intentionally
inefficient where their oracle requires it.
