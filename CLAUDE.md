# Dejavu

Implicit recomposition tracking for Compose UI tests. KMP library targeting Android, Desktop (JVM), iOS, and WasmJs.

Many sample composables intentionally recompose too often. Preserve these fixtures when validating
the library. Accuracy means matching independent `SideEffect` counters and producing the expected
budget failures; it does not mean optimizing every sample until it is stable.

## Verification Requirements

**Always run UI tests when validating changes.** This is a UI testing framework — unit tests alone are not sufficient. The SideEffect accuracy tests in `commonTest` verify that the tracer's recomposition counts match ground-truth `SideEffect` counters through actual Compose UI rendering.

Minimum verification after any code change:

```bash
./gradlew -q --console=plain :dejavu:jvmTest                    # Desktop JVM (unit + compose UI tests)
./gradlew -q --console=plain :dejavu:testDebugUnitTest           # Android unit tests
./gradlew -q --console=plain :dejavu:iosSimulatorArm64Test       # iOS compose UI tests on simulator
./gradlew -q --console=plain :dejavu:wasmJsBrowserTest           # Wasm compose UI tests in headless browser
./gradlew -q --console=plain apiCheck                            # API compat (all targets)
```

`jvmTest`, `iosSimulatorArm64Test`, and `wasmJsBrowserTest` all run the compose UI integration tests (SideEffect accuracy, recomposition counting) from `commonTest`. Android excludes compose UI tests from `testDebugUnitTest` (they require Robolectric); Android compose coverage comes from instrumented tests in the demo app.

## Project Structure

- `commonMain` — core tracer, data classes, query APIs, test assertions, expect declarations
- `androidMain` — Android runtime (Activity lifecycle, Choreographer, snapshot observer, tag mapping via Group tree)
- `jvmMain` — Desktop JVM actuals (Composer.setTracer, ThreadLocal, stdout logging)
- `iosMain` — iOS actuals (NSDate, NSLog, simple value holder for PlatformThreadLocal)
- `wasmJsMain` — WasmJs actuals (JS Date.now(), console.log/warn)
- `commonTest` — unit tests + compose UI integration tests (SideEffect accuracy)

### `compose-experimental` module

Separate Gradle module (`:compose-experimental`) that stages recomposition tests for experimental /
newest-Compose APIs before they graduate into the core accuracy suite. KMP targets use the pinned
Compose Multiplatform baseline; Android builds and runs this module at every supported Compose 1.11–1.12
BOM checkpoint. Convention: when an API graduates to stable and the `:dejavu` BOM floor includes
it, promote its test into `dejavu/src/commonTest` and delete it here. See
`compose-experimental/README.md`.

## Key Architecture

- `DejavuTracer` implements `CompositionTracer` — intercepts every `traceEventStart`/`traceEventEnd`
- First composition of a key → tracked but not counted as recomposition. Subsequent → counted.
- Tag mapping runs on all targets. Android uses its tooling Group tree with a common fallback and
  frame-driven per-instance tracking. Other targets walk `CompositionGroup` directly; unresolved
  multi-instance counts can fall back to the shared function count.
- The inspection collection has stable object identity because Compose registers it in a hash set
  of mutable collections. Do not replace it with a content-hashed set.
- KMP UI test helpers must return Compose's `TestResult`. Keep assertions and tracer lifecycle
  inside the suspendable test body so Wasm awaits completion and observes failures.
- Locking uses `kotlinx-atomicfu` `SynchronizedObject` (not `kotlin.synchronized` which is JVM-only)
- `@kotlin.concurrent.Volatile` in common/native code (not `@Volatile` which is `kotlin.jvm.Volatile`)

## Reset Semantics

- `reset()` — full clear of all state including composition history. Use between independent tests.
- `resetCounts()` — clears recomposition counts but preserves composition history. Use mid-test when a live composition is still running.
- `ComposeTestRule.resetRecompositionCounts()` calls `resetCounts()` (mid-test use case).

## Gradle

Always run with `-q --console=plain`.

For release readiness, start a clean emulator, set `ANDROID_SERIAL`, and run
`./test.sh --all-boms`. This enforces every supported Android BOM and runs both UI suites in
addition to the JVM, iOS, Wasm, API, lint, and demo build checks. Compose 1.10 consumers remain on
Dejavu 0.3.1; 0.4.0 retains the Compose Multiplatform 1.11 / compile SDK 36 baseline. DejaVu 0.5.x
builds against Compose Multiplatform 1.12.0 and supports Android BOM 2026.05.00 through 2026.08.00.
Android consumers require compile SDK 37 and must enforce the BOM to retain an older Compose line.
For documentation-only edits, run the documentation checks in CONTRIBUTING.md; UI suites are
required for runtime changes, not prose or website changes.

## Bundled Claude skills

This repo ships four skills under `.claude/skills/` for AI agents working with Dejavu:

- `dejavu-onboarding` — add Dejavu to a project from scratch (gradle dependency, first test).
- `dejavu-test-writer` — author Compose UI recomposition tests using Dejavu's APIs.
- `dejavu-error-triage` — one-shot diagnosis of a single failing `UnexpectedRecompositionsError`.
- `dejavu-perf-loop` — closed-loop optimization of a composable's recomposition behavior, using Dejavu as the validator. Invokes `dejavu-test-writer` to establish the baseline test.

The four skills cross-reference each other so the agent can flow between them: onboarding → test-writer → (error-triage | perf-loop) depending on whether the user wants a one-shot fix or an iteration loop.

All skills point at the canonical docs in `docs/` and the canonical test patterns in `dejavu/src/commonTest/kotlin/dejavu/*PatternTest.kt` rather than duplicating them. They auto-load for sessions opened in this repo.

### Plugin layout

The same skills are also packaged as a Claude Code plugin so users outside this repo can install them globally:

- `.claude-plugin/plugin.json` — plugin manifest (`name: dejavu`).
- `.claude-plugin/marketplace.json` — single-plugin marketplace listing.
- `skills/<skill-name>/` — symlinks into `.claude/skills/` so the canonical SKILL.md files have one source of truth. Edit the canonical files under `.claude/skills/`; the plugin layout picks up the change via symlink.

Install instructions for end-users live in `README.md`.
