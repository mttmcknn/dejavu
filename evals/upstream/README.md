# Upstream evaluator provenance

The harness, runner, JSON schemas, and generic harness tests are adapted from
[Chris Banes' skills evaluator](https://github.com/chrisbanes/skills/tree/2db11bb412254246bf0a5f6e5e320177107a7c53/evals),
revision `2db11bb412254246bf0a5f6e5e320177107a7c53` (retrieved 2026-09-09).
The original is licensed under Apache-2.0; [LICENSE](LICENSE) preserves its license.
No separate upstream NOTICE file was present. Unmodified source retains its text;
modified files carry adaptation comments. DejaVu's corpus and bundle checks are new.

Local changes: four-entry catalog with no router; default `dejavu` suite; DejaVu
plugin-qualified names; retain existing execpolicy rules; explicit historical
skill selection; include evaluator code in fingerprints; cap run call counts;
no automatic subject/judge retries during `run`; adapt generic test skill names.
The parser's inherited Gradle command checks remain to reject unsafe/offline-policy
violations if future cases add runnable Gradle fixtures.

To update, fetch a specific upstream revision, compare `evals/harness`, `run.py`,
`schemas` and the five adapted generic test modules against this pin, carry local
changes forward, and run all offline checks. Record the new pin and changes here.
Do not overwrite DejaVu's cases, `skill_source.py`, validator or bundle checks.
