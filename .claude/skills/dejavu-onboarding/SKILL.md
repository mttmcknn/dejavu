---
name: dejavu-onboarding
description: Install DejaVu and prove recomposition tracking in an Android or Compose Multiplatform project that does not have it yet. Use for dependency, compatibility, compiler source information, test host, or first-test setup. For an already configured project, use test writing or failure triage instead.
---

# DejaVu onboarding

Inspect the existing version catalog, plugins, SDK levels, source sets and test
host. Add a compatible test dependency and one test that proves tracking is
active. Preserve dependency conventions and the user's version constraints.

## Choose the compatible setup

Read [setup.md](references/setup.md) for the versioned dependency contract and
Android/KMP requirements. It is bundled so installation outside the DejaVu
repository works. Use the host project's files, not assumed module paths. For
newer versions, consult their release-specific documentation before selecting
one; don't silently downgrade or upgrade Compose, Kotlin or SDK levels to fit
an example.

If prerequisites conflict, identify the exact mismatch and compatible DejaVu
line. Continue useful diagnosis; request a version decision only when the task
has not already authorized it. If setup is complete, leave it alone.

## Prove tracking works

- Android: retain the activity host with
  `createRecompositionTrackingRule<YourActivity>()`, or use the plain factory for
  test-owned content. Include Compose test APIs explicitly and the debug test
  manifest when needed. Do not nest independent Compose test environments.
- KMP: use `runRecompositionTrackingUiTest { setTrackedContent { … } }` and return
  its `TestResult` from the test method. Assertions and cleanup stay inside the
  suspending body so Wasm observes failures.
- Use a tagged user composable and a real state-changing interaction. Settle
  initial content, reset counts preserving live composition history, perform the
  interaction, settle, and assert the changed UI and a nonzero count. A stable-only
  assertion can pass when tracking is broken.
- Preserve existing UI assertions. Library accuracy fixtures need an independent
  **unkeyed** `SideEffect` counter and expected budget failures. Intentionally
  excessive recompositions are the behavior under test.

Run the narrowest relevant actual UI test available. Report the dependency line,
source set, host and observed result. Compilation does not prove tracking;
report a missing device or runtime as an unrun check.

For more coverage, `dejavu-test-writer` is useful when available; it is not a
prerequisite to finishing. Public reference:
[versioned setup](https://dejavu.mmckenna.me/0.5.0/getting-started/).
