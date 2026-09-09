# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.5.0] - 2026-09-09

### Changed
- Build against stable Compose Multiplatform 1.12.0 and Android BOM 2026.08.00 (Compose 1.12.0).
  DejaVu keeps independent version numbers and the Android Compose 1.11 support floor.
- Retain Android BOM 2026.05.00 and 2026.06.01 as the minimum and latest 1.11 checkpoints.
  Retire the redundant intermediate 2026.06.00 checkpoint without moving the support floor.
- Upgrade the build to AGP 9.4.0 and compile SDK 37 for the new Android Compose artifacts.
  The published AAR requires consumers to use compile SDK 37. Retaining Android Compose 1.11
  requires an enforced BOM so the transitive 1.12 baseline does not win resolution.
- Adapt experimental Styles fixtures to the changed `pressed` API with separate 1.11 and 1.12
  test sources. Both retain the same deliberately inefficient and stable fixture behavior.
- Declare Wasm executables for browser UI tests, as required by Compose Multiplatform 1.12's
  Skiko bundling check. The library continues to publish Wasm KLIB artifacts.

### Added
- The Android rule delegates Compose 1.12's `hasPendingWork` and `runWithoutImplicitWait` APIs.
  These new methods require Compose 1.12; existing rule methods remain covered on Compose 1.11.
- Six public-API regressions for Compose 1.12: stable and changed keyed `SideEffect`, shrinking
  vararg effect keys, shrinking `remember` keys, frame assertions using `runWithoutImplicitWait`,
  and nested movable content under the LinkBuffer composer.
- The keyed-effect regression deliberately recomposes four times while the keyed callback stays
  quiet, proving DejaVu counts every recomposition and rejects an incorrect stability budget.
  Unkeyed `SideEffect` remains the independent recomposition-count oracle.
- Version-specific tests compile only where the API exists. Older Android BOM checks retain the
  complete core suite and the original 20 experimental tests; 1.12 adds the six new regressions.
- A standalone consumer build verifies packaged Maven artifacts across Android checkpoints and
  the KMP baseline. Release checks now reject empty, failing, or skipped JUnit reports.

### Fixed
- Combine Android activity inspection tables with tables supplied by `setTrackedContent`.
  Previously, enabling the Android tracking rule before a KMP helper test in the same process
  could hide the helper's subcomposition and report an unmapped tag. A dedicated Android
  regression and the packaged consumer suite cover both harnesses together.
- Restore previously active tracing and inspector settings after the KMP helper finishes, so
  a subsequent Android rule keeps tracking. The regression checks the full rule/helper/rule path.
- Enable Android tracking before activity launch so initial inspection tables are registered
  after a previous test disabled tracking. The setup regression verifies the first update against
  an independent `SideEffect` count.

## [0.4.0] - 2026-09-09

### Changed
- **Made the legacy recomposition test suites self-validating.** Replaced weak/directional
  assertions (`assertRecompositions(atLeast = …/atMost = …)`, `atLeast = 0`) in the `dejavu`
  pattern tests and the `demo` instrumented tests with exact assertions — either a pinned budget
  (`exactly = N` / `assertStable()`) or, in `dejavu/commonTest`, a `SideEffect`-ground-truth
  equality (`exactly = GroundTruth.delta(tag)` for single-instance / distinct-call-site nodes, and
  `DejavuTracer.getRecompositionCount(fn) == GroundTruth.delta(fn)` at the function level for
  keyless-loop / multi-tag composables whose per-instance counts only resolve on Android). This
  proves the tracer's counts are *accurate*, not merely non-zero. Added a shared `GroundTruth`
  test helper (`record`/`snapshotBaseline`/`delta`) mirroring the `compose-experimental` pattern.
  Two narrow classes stay directional by design: the assertion-API coverage tests
  (`AssertionApiPatternTest` / demo `AssertionApiTest`, which exercise `atLeast`/`atMost`/range)
  and continuously-running animation / scroll-frame counts whose exact value is a moving target.

### Added
- Bundled four Claude Code skills in `.claude/skills/` to help AI agents adopt Dejavu, author tests, triage failures, and run an iterative recomposition-optimization loop:
  - `dejavu-onboarding` — adds Dejavu to a project from scratch (gradle dependency, first `Modifier.testTag`, smallest passing test).
  - `dejavu-test-writer` — authors Compose UI recomposition tests using Dejavu's APIs.
  - `dejavu-error-triage` — one-shot diagnosis of a single `UnexpectedRecompositionsError` block.
  - `dejavu-perf-loop` — closed-loop optimization using Dejavu as the validator.
- Packaged the same skills as a Claude Code plugin (`dejavu`) installable via `/plugin marketplace add himattm/dejavu` + `/plugin install dejavu@dejavu`. Plugin manifest at `.claude-plugin/plugin.json`, marketplace at `.claude-plugin/marketplace.json`, with `skills/` symlinked into `.claude/skills/` for a single source of truth.
- New `compose-experimental` module (a staging area for experimental-API recomposition coverage) with tests for Compose 1.11's new APIs, running on JVM, iOS, Wasm, and Android instrumented: experimental `Grid`, experimental `FlexBox`, the experimental LinkBuffer composer runtime path (`ComposeRuntimeFlags.isLinkBufferComposerEnabled`), `movableContentOf`, `derivedMediaQuery`/`mediaQuery` (adaptive breakpoints), and the Styles API (`androidx.compose.foundation.style`).
- Public `ComposeUiTest.resetRecompositionCounts()` for KMP recomposition tests — resets recomposition counts mid-test while preserving composition history.
- A Coil-style `test.sh` release check and `RELEASING.md` checklist that pin one Compose
  Multiplatform/BOM baseline per Dejavu release while retaining older supported BOM checkpoints.

### Compatibility
- **Minimum supported Compose is now 1.11 (BOM 2026.05.00).** Compose testing v2 is not available
  in the 1.10 BOM line. Compose 1.10 remains supported by Dejavu 0.3.1.
- Migrate the test harness to the Compose testing **v2** APIs (`androidx.compose.ui.test.v2.runComposeUiTest`, `androidx.compose.ui.test.junit4.v2.createAndroidComposeRule`), which default to `StandardTestDispatcher` (v1 used `UnconfinedTestDispatcher`). This completes the testing-v2 half of #63. Validated that recomposition counts are unchanged on JVM under the new dispatcher — no rebaselining was required.
- Bump Compose Multiplatform from 1.10.3 to 1.11.1.
- Bump Compose BOM baseline from 2026.03.01 to 2026.06.01.
- Drop `iosX64` from supported targets because Compose Multiplatform 1.11 no longer supports Apple x64 targets. iOS coverage remains via `iosArm64` (compile) and `iosSimulatorArm64` (tests).
- Promote the LinkBuffer composer-path and `movableContentOf` regression tests into the cross-platform `compose-experimental` common test set (JVM, iOS, Wasm) alongside Android instrumented coverage.
- CI: derive compatibility jobs from version-catalog checkpoints and validate 2026.05.00,
  2026.06.00, and the 2026.06.01 release baseline. Each Android checkpoint runs both the legacy
  UI suite and the new Compose 1.11 composable/runtime regressions.
- Publishing is an explicit workflow dispatch against a matching release tag. A documented local
  release suite and exact verified commit can replace repeated hosted CI checks, conserving CI budget.

### Fixed
- Give the mutable inspection collection stable registration identity. Compose stores that collection
  in a hash set; hashing its changing contents caused lazy-layout registration to crash on iOS and
  Wasm. All eight lazy-row/grid accuracy tests now run on every target (#21).
- Preserve the asynchronous `TestResult` from `runRecompositionTrackingUiTest` and keep tracking
  active across suspensions. The block now accepts suspending calls, matching Compose testing v2.
  Return the helper result directly from each Wasm test; recompile test binaries after upgrading.
- Restore all seven Wasm diagnostic-message tests by inspecting caught assertion errors inside the
  asynchronous UI test. The previous helper read an empty result before the test completed (#22).
- Make `-PcomposeBomVersion` use an enforced platform during compatibility checks. This prevents
  Compose Multiplatform's newer transitive Android artifacts from silently replacing an older BOM
  and making a compatibility job test the wrong runtime.
- Keep Android tags handled by `CommonTagMapping` in the per-frame fallback pass after their initial
  mapping, so their recompositions continue to be detected. Android's primary tooling pass now
  hands off its exact mapped-tag set instead of treating every previously known tag as handled.
- Make JVM/Android inspection-table storage safe for concurrent Compose writes, and synchronize the
  `SideEffect` ground-truth test counters used across UI and instrumentation threads.
- Make the async `produceState`/`snapshotFlow` Android regressions compare Dejavu with a
  `SideEffect` ground truth, and remove the random reset setup that could flake 1 in 720 runs.

### Removed
- Removed the pre-1.10 degraded compatibility path (`-PexcludeCompositionObserver` / `src/observerAndroid` split); `CompositionObserver` support is now unconditional.

## [0.3.1] - 2026-04-15

### Fixed
- Android tag-mapping failure when `Dejavu.enable()` is called after the test activity is already resumed (#52)
  - `seedActiveActivity()` now explicitly seeds the current activity from `DejavuComposeTestRule`, so `onActivityResumed` does not need to re-fire
  - Added `@Volatile` to `lastResumedRef` for safe cross-thread reads
  - `onActivityDestroyed` now clears `lastResumedRef` when the tracked activity is destroyed

### Added
- `runRecompositionTrackingUiTest` and `setTrackedContent` public APIs for KMP test setup (JVM, iOS, WasmJs)
- Dokka API documentation generation
- Regression test for disable/re-enable cycle on a resumed activity

### Changed
- Bump Kotlin from 2.2.20 to 2.3.20
- Bump AGP from 8.13.2 to 9.1.0
- Bump Gradle wrapper from 8.13 to 9.4.1
- Bump Compose Multiplatform from 1.10.1 to 1.10.3
- Bump Compose BOM from 2026.01.01 to 2026.03.00
- Bump Activity Compose from 1.7.0 to 1.13.0
- Bump atomicfu from 0.27.0 to 0.32.1
- Bump Robolectric from 4.14.1 to 4.16.1
- Bump core-ktx from 1.17.0 to 1.18.0
- Bump Dokka from 2.1.0 to 2.2.0
- Bump Gradle Actions from 4 to 6
- Bump mkdocs-material from 9.7.4 to 9.7.6
- Bump mike from 2.1.3 to 2.1.4
- Update compatibility tables and CI matrix for 2026.03.01 BOM baseline

## [0.3.0] - 2026-03-22

### Added
- Kotlin Multiplatform support — JVM Desktop, iOS, and WasmJs targets (#13)
- 210 cross-platform tests reaching 98% coverage parity with Android
- Cross-platform tag mapping and per-instance recomposition tracking
- KMP demo targets (Desktop, iOS, Wasm) with shared demo module

### Changed
- CI matrix expanded with cross-platform regression tests and hardened verification
- Consolidated fingerprint baseline capture into `resetCounts()` for deterministic test setup

### Fixed
- Timing-sensitive per-tag counting for multi-instance composables
- Thread safety issues for concurrent access across platforms
- iOS/WasmJs test failures from platform-specific actuals

## [0.2.0] - 2026-03-16

### Added
- CompositionObserver integration — automatically tracks per-scope invalidation causes and state dependencies on Compose runtime 1.7+ (#27)
  - Enriches `assertStable()` / `assertRecompositions()` error messages with invalidation cause details, value progressions, and state dependency sets
  - Auto-detected at `Dejavu.enable()` — no opt-in required
  - Backward-compatible: observer classes in separate source set, core compiles against Compose BOMs 2024.06+
  - Reentrancy guard in `stateValue()` prevents StackOverflowError from DerivedState reads
  - Uses IdentityHashMap instead of identityHashCode to avoid hash collisions

### Fixed
- Per-tag recomposition tracking: false positives from identity-based multi-instance detection, null-fallback to function-level counts, and runtime-internal objects drifting fingerprint hashes (#28)
- Flaky per-tag recomposition detection in CI

### Changed
- CI publish workflow auto-updates docs version references at deploy time

## [0.1.2] - 2026-03-08

### Changed
- Improved logcat output — unified TAG to "Dejavu", single summary lines, cleaner parent notation, consistent log levels (#23)

### Fixed
- testTag mapping for older Compose versions using value-based InspectableValue (#16)
- Hardcoded framework filter replaced with dynamic simpleNameIndex resolution (#20)

## [0.1.1] - 2026-03-03

### Changed
- Logcat logging now emits per-instance `RECOMPOSE-TAG` lines for composables with a testTag, matching Layout Inspector's per-instance recomposition counts
- Composables without a testTag still get the per-function `RECOMPOSE` line with a `[no testTag — per-function aggregate]` caveat
- No more dual logging — each composable gets exactly one log format

## [0.1.0] - 2026-02-23

### Added
- Implicit recomposition tracking via `CompositionTracer` — no per-composable modifiers needed
- `Dejavu.enable()` / `Dejavu.disable()` API for runtime control
- `DejavuComposeTestRule` for automated test setup with auto-reset
- Semantic node assertions: `assertRecompositions(exactly, atLeast, atMost)` and `assertStable()`
- Recomposition cause tracking with `Snapshot.registerApplyObserver`
- Rich assertion error messages with source location, timeline, and causality info
- Tag-to-composable mapping via CompositionData tree walking
- 23 demo screens covering common Compose patterns
- 160 tests (150 instrumented + 10 JVM unit)
