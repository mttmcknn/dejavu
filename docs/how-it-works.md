# How It Works

Dejavu hooks into the Compose runtime's `CompositionTracer` API (available since compose-runtime 1.2.0):

1. **Intercepts trace calls** — `Composer.setTracer()` receives callbacks for every composable enter/exit
2. **Maps testTag to composable** — walks the `CompositionData` group tree to find which composable encloses each `Modifier.testTag()`
3. **Counts recompositions** — maintains a thread-safe counter per composable, incrementing on recomposition (not initial composition)
4. **Tracks causality** — `Snapshot.registerApplyObserver` detects state changes; dirty bits detect parameter-driven recompositions
5. **Reports on failure** — assembles source location, timeline, tracked composables, and causality into a structured error

On Android, tracking runs in the app process and is accessible to instrumented tests. JVM, iOS,
and Wasm tests use the shared tracer through `runRecompositionTrackingUiTest`.

## Compatibility

**Minimum supported Compose for Dejavu 0.5.x: 1.11 (BOM 2026.05.00).** The 0.5.x test harness uses
the Compose testing v2 APIs introduced with this line. For Compose 1.10, use Dejavu 0.3.1. This
keeps that older Compose line available without letting newer transitive artifacts mask an
unsupported combination. Validated with Kotlin 2.4.0 and its Compose compiler plugin.

| Compose BOM | Compose | Kotlin tested | Status |
|---|---|---|---|
| 2026.05.00 | 1.11.x | 2.4.0 | Minimum |
| 2026.06.01 | 1.11.x | 2.4.0 | Latest 1.11 checkpoint |
| 2026.08.00 | 1.12.x | 2.4.0 | Release baseline |

The release baseline is Compose Multiplatform 1.12.0 and Android Compose BOM 2026.08.00; the floor
is Compose 1.11 (BOM 2026.05.00). CI derives its matrix from the `composeBomCompat*` checkpoints and
`composeBom` baseline in `gradle/libs.versions.toml`, currently 2026.05.00, 2026.06.01, and
2026.08.00. Compatibility runs enforce the selected BOM so newer transitive Compose
Multiplatform artifacts cannot silently replace the runtime under test. `CompositionObserver`
support is unconditional; there is no degraded or observer-excluded build path.

Android 0.5.x artifacts require compile SDK 37 and min SDK 24. To retain an older Android Compose line,
use `enforcedPlatform` for the selected BOM in both application and instrumentation dependencies;
a regular platform can allow the newer transitive baseline to win. DejaVu 0.4.0 remains the
Compose Multiplatform 1.11 / compile SDK 36 baseline.

## Compose Testing v2

Dejavu's test harness uses the Compose testing **v2** APIs (`runComposeUiTest` /
`createAndroidComposeRule` from the `androidx.compose.ui.test.v2` packages). These default to
`StandardTestDispatcher` rather than v1's `UnconfinedTestDispatcher`. Recomposition counts were
verified to be unchanged on JVM under the new dispatcher, so no rebaselining of test expectations
was required.

## New Compose API Coverage

The `compose-experimental` module is a staging area for recomposition coverage of experimental /
newest-Compose APIs before they graduate into the core accuracy suite. It exercises Dejavu against
Compose 1.11's new composables and runtime paths: the experimental `Grid` and `FlexBox` layouts,
`derivedMediaQuery` / `mediaQuery` adaptive breakpoints, the Styles API
(`androidx.compose.foundation.style`), `movableContentOf`, and the experimental LinkBuffer composer
runtime path (`ComposeRuntimeFlags.isLinkBufferComposerEnabled`). These tests run on JVM, iOS,
Wasm, and Android instrumented; Android runs every supported BOM checkpoint.

### Compose 1.12 Coverage

The Compose 1.12 baseline additionally validates keyed `SideEffect`, shrinking vararg effect and
`remember` keys, assertions using `runWithoutImplicitWait`, and nested movable content under
LinkBuffer. Exact counters continue using unkeyed `SideEffect`, including a deliberately inefficient
fixture whose keyed callback stays quiet during four recompositions. The experimental suite runs
26 tests on 1.12 and retains 20 on the supported Android 1.11 checkpoints.

`DejavuComposeTestRule` delegates the new `hasPendingWork` and `runWithoutImplicitWait` methods on
Compose 1.12. These methods require 1.12; the existing rule API remains covered on Android 1.11.

## Exact lambda counts (development snapshot)

The **0.6.0 development snapshot** adds committed execution counts for anonymous
composable instances in content owned by `runRecompositionTrackingUiTest` and
`setTrackedContent`. This behavior is not in published 0.5.x releases.

Each count uses the restart scope's tooling identity together with the compiler's
body key. A trace records a count operation on the owning composer's change list,
using the same primitive as an unkeyed `SideEffect`. Compose applies that operation
only when it commits the work. Initial composition is excluded, and `resetCounts()`
establishes a new baseline for live instances. Reading a count does not sample or
increment it, so three executions remain three whether assertions run after every
update or only once at the end. Skipped bodies and discarded work do not count.

The implementation uses `IdentifiableRecomposeScope`, composition observers and
`Composer.recordSideEffect`. The last API is marked **InternalComposeApi**; this is
an additional runtime compatibility dependency, not a new compiler plugin or an
annotation required on application components. Recording an effect also adds
measurement overhead, so these counts are intended for tests, not profiling costs.

The helper captures its own root and content composers. This does **not** capture
arbitrary activity-owned compositions or separate composers created by lazy layouts,
dialogs, popups or user `SubcomposeLayout` instances. During the helper session, an
anonymous tag without a verified captured scope fails explicitly. A parent composer's
count cannot stand in for an uncaptured child. Named-function counts and their existing
multi-instance approximation limits are unchanged.

## Known Limitations

- **Uncaptured anonymous owners** — published 0.5.x releases cannot provide exact lambda-instance counts. The unpublished development snapshot supports the captured helper path described above and rejects unsupported paths, including an ordinary activity-owned lambda inside a named renderer. Use the tracked helper or an independent unkeyed `SideEffect` counter in the actual body. If a tag changes from a lambda owner to a named owner during a measurement window, let the new component settle and reset counts before measuring it.
- **Off-screen lazy items** — `LazyColumn`/`LazyRow` only compose items that are visible. Items that haven't been composed don't exist in the composition tree, so Dejavu has nothing to track. Scroll them into view before asserting.
- **Non-Android instance diagnostics** — unresolved tags can share a function-level count when multiple instances use the same composable. Android has the most complete per-instance diagnostics.
- **Activity-owned Recomposer clock** — `createAndroidComposeRule` uses the Activity's real `Recomposer`, not a test-controlled one. This means `mainClock.advanceTimeBy()` can't drive infinite animations forward. Use `createComposeRule` (without an Activity) if you need a controllable clock.
- **Parameter change tracking precision** — parameter diffs use `Group.parameters` from the Compose tooling data API, which was designed for Layout Inspector rather than programmatic diffing. Parameter names may be unavailable, and values are compared via `hashCode`/`toString`, so custom types without meaningful `toString` show opaque values.
- **iOS x64** — Compose Multiplatform 1.11 removes Apple x64 target support, so Dejavu supports `iosArm64` and `iosSimulatorArm64` for the 1.11 baseline.
