# Dejavu

*Wait... didn't we just compose this?*

[![CI](https://github.com/mttmcknn/dejavu/actions/workflows/ci.yml/badge.svg)](https://github.com/mttmcknn/dejavu/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/me.mmckenna.dejavu/dejavu)](https://central.sonatype.com/artifact/me.mmckenna.dejavu/dejavu)
[![Compose](https://img.shields.io/badge/Compose-1.11–1.12-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/develop/ui/compose)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**[GitHub Repository](https://github.com/mttmcknn/dejavu)**

**Guard your Compose UI efficiency. Catch recomposition regressions before your users.**

## The Problem

Compose's recomposition behavior is an implicit contract — composables should recompose when their inputs change and stay stable otherwise. But that contract breaks silently, and today's options for catching it are limited:

- **Layout Inspector** — manual, requires a running app, can't automate, can't run in CI
- **Manual tracking code** — `SideEffect` counters, `LaunchedEffect` logging, wrapper composables; invasive, doesn't scale, and ships in your production code
- Neither gives you a **testable, automatable contract** you can enforce on every PR

## What Dejavu Does

Dejavu is a test-only library that turns recomposition behavior into assertions. Tag your composables with standard `Modifier.testTag()`, write expectations against recomposition counts, and get structured diagnostics when something changes — whether from a teammate, a library upgrade, an AI agent rewriting your UI code, or a refactor that silently destabilizes a lambda.

- **Zero production code changes** — just `Modifier.testTag()`
- **One-line test setup** — `createRecompositionTrackingRule()`
- **Rich diagnostics** — source location, recomposition timeline, parameter diffs, causality analysis
- **Per-instance tracking** — multiple instances of the same composable get independent counters

Dejavu 0.5.x supports Compose 1.11–1.12 (BOM 2026.05.00 through 2026.08.00); the release baseline is
Compose Multiplatform 1.12.0 and Android Compose BOM 2026.08.00. For Compose 1.10, use Dejavu
0.3.1. The 0.5.x test harness uses the Compose testing v2 APIs (which default to
`StandardTestDispatcher`). Instrumented BOM gates run the supported BOM range, and the
`compose-experimental` module (a staging area for experimental-API
recomposition coverage) validates Dejavu against Compose 1.11's new composables (`Grid`, `FlexBox`,
`derivedMediaQuery`, the Styles API) and the LinkBuffer runtime path. See [How It Works](how-it-works.md) for the full compatibility table and details.

## Next Steps

- [Getting Started](getting-started.md) — add the dependency and write your first test
- [Use Cases](use-cases.md) — locking in UI efficiency, AI agent guardrails, and CI enforcement
- [Examples](examples.md) — test patterns for common scenarios
- [API Reference](api/index.html) — all assertions and utilities
- [How It Works](how-it-works.md) — internals, compatibility, and limitations
- [Releases and migration](releases/index.md) — supported versions, release notes, and validation
- [Contributing](contributing.md) — development setup, test expectations, and project policies

## Using Claude Code?

Install the bundled Dejavu skills globally — they cover initial install (`dejavu-onboarding`), authoring tests (`dejavu-test-writer`), diagnosing single failures (`dejavu-error-triage`), and the iterative perf-optimization loop (`dejavu-perf-loop`):

```
/plugin marketplace add mttmcknn/dejavu
/plugin install dejavu@dejavu
```

See [Use Cases → Give AI Agents a Recomposition Signal](use-cases.md#give-ai-agents-a-recomposition-signal) for what each skill does.

DejaVu 0.5.0 also covers Compose 1.12 keyed effects, shrinking `remember` keys, test synchronization,
and nested movable content. The Android Compose 1.11 support floor is retained; see
[compatibility and coverage](how-it-works.md) for the tested version matrix.
