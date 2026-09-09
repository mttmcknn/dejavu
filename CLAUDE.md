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

## Bundled agent skills

Four skills live under `skills/`: `dejavu-onboarding`, `dejavu-test-writer`,
`dejavu-error-triage` and `dejavu-perf-loop`. Edit these canonical files.
The Claude Code plugin reads `skills/` directly; `.claude/skills/` and `.agents/skills/`
supply repository discovery links. References needed outside this repository are bundled inside each skill.
Companion skills are optional; use them when their scope fits the request.

After skill changes, run `python3 validation/skills.py`, `python3 evals/run.py validate`
and `python3 -m unittest discover -s evals/tests -p 'test_*.py'`. Model evaluations
are opt-in and advisory, with previews and call caps. See `evals/README.md`.
A source-level skill evaluation does not replace actual UI verification for runtime changes.

Plugin metadata lives in `.claude-plugin/`; keep its plugin and marketplace
versions equal. The skill bundle's version is independent of the library version.
