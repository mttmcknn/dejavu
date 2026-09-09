# Releases and migration

**Latest stable: [DejaVu 0.5.0](https://github.com/mttmcknn/dejavu/releases/tag/v0.5.0).**
All six Maven Central publications are available. Dependency examples use the stable release.

| DejaVu | Compose Multiplatform baseline | Android Compose | Android compile SDK |
|---|---|---|---|
| [0.5.0](https://github.com/mttmcknn/dejavu/releases/tag/v0.5.0) | 1.12.0 | 1.11–1.12 | 37 |
| [0.4.0](https://github.com/mttmcknn/dejavu/releases/tag/v0.4.0) | 1.11.1 | 1.11 | 36 |
| [0.3.1](https://github.com/mttmcknn/dejavu/releases/tag/v0.3.1) | 1.10.3 | 1.10 | See versioned docs |

Use the [compatibility matrix](../how-it-works.md#compatibility) for exact Android BOM checkpoints.
With 0.5.0, retain an older Android BOM using `enforcedPlatform` in both app and instrumentation
dependencies; a regular platform can resolve the newer transitive baseline. The Android artifact
still requires compile SDK 37. The new `hasPendingWork` and `runWithoutImplicitWait` rule methods
require Compose 1.12.

Since 0.4.0, return `runRecompositionTrackingUiTest` directly from KMP tests and keep assertions
inside its suspending body. This lets Wasm await completion and observe failures. Recompile test
modules when upgrading from older helper signatures.

0.5.0 fixes tracking lifecycle transitions when Android rules and the KMP helper share one
instrumentation process. The limitation remains documented for 0.4.0.

## Validation and history

- [0.5.0 validation](0.5.0.md): Android, JVM, iOS, Wasm, and packaged consumer checks.
- [0.4.0 validation](0.4.0.md): restored async Wasm and lazy-layout coverage.
- [Changelog](https://github.com/mttmcknn/dejavu/blob/main/CHANGELOG.md).
- [All GitHub releases](https://github.com/mttmcknn/dejavu/releases).

The site version selector keeps released documentation available. `latest` follows the current
stable release; `snapshot` follows unreleased development and can describe APIs not in Maven
Central yet. Historical release pages intentionally keep their original dependency versions.

## Release policy

DejaVu uses independent semantic versions, pins one stable Compose Multiplatform baseline per
release, and tests an Android BOM compatibility window. We follow stable Compose releases;
alpha and beta updates do not automatically trigger DejaVu releases. Moving the compatibility
floor requires an explicit release and migration note.

See the [maintainer release checklist](https://github.com/mttmcknn/dejavu/blob/main/RELEASING.md)
for local validation, artifact verification, and documentation publication.
