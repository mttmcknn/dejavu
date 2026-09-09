# Contributing to Dejavu

Dejavu validates recomposition behavior on Android, JVM desktop, iOS, and Wasm. Some sample
composables intentionally recompose too often. Preserve those fixtures: a correct test proves
that Dejavu matches independent unkeyed `SideEffect` counters and rejects an incorrect budget.
A keyed `SideEffect` does not count every recomposition.

## Development setup

```bash
git clone https://github.com/mttmcknn/dejavu.git
cd dejavu
```

Use the Gradle wrapper and JDK 17 or later. The current build pins Kotlin 2.4.0, AGP 9.4.0,
Compose Multiplatform 1.12.0, and Android compile SDK 37. Install Android SDK platform 37 and
accept its licenses. Android Studio must support the pinned AGP version; the Gradle wrapper is
the command-line source of truth. Android test devices must run API 24 or later.

JVM tests need a desktop environment. Wasm browser tests need Chrome. iOS tests require macOS,
Xcode, and an available arm64 simulator. See the [release validation record](docs/releases/0.5.0.md)
for the exact environments used for the current release; minimum device support is not a claim
that every supported OS was freshly tested.

## Running tests

Run targeted checks during development, then broaden to affected platforms:

```bash
# Android unit tests, without a device
./gradlew -q --console=plain :dejavu:testDebugUnitTest

# Actual Compose UI tests on each non-Android platform
./gradlew -q --console=plain :dejavu:jvmTest :compose-experimental:jvmTest
./gradlew -q --console=plain :dejavu:iosSimulatorArm64Test :compose-experimental:iosSimulatorArm64Test
./gradlew -q --console=plain :dejavu:wasmJsBrowserTest :compose-experimental:wasmJsBrowserTest

# Android UI tests on an explicitly selected emulator
ANDROID_SERIAL=emulator-5554 ./gradlew -q --console=plain \
  :demo:connectedDebugAndroidTest :compose-experimental:connectedDebugAndroidTest

./gradlew -q --console=plain apiCheck :dejavu:lintDebug
```

For release readiness, use a clean emulator and `ANDROID_SERIAL=emulator-5554 ./test.sh --all-boms`.
The script reads the supported BOMs from `gradle/libs.versions.toml`, forces fresh tests, saves
reports, and rejects missing, failed, or skipped tests. Runtime changes need real UI tests;
Android unit tests alone cannot verify recomposition accuracy. For new Compose APIs, see
[compose-experimental](compose-experimental/README.md).

Hosted CI may be limited by budget. Equivalent passing local checks, with source revision,
commands, platform details, counts, and exclusions recorded, can satisfy the release gate.
Reproduced product failures still need fixes. Packaged release verification is described in
[RELEASING.md](RELEASING.md) and the [standalone consumer guide](validation/consumer/README.md).

## Documentation changes

User guides live in `docs/`; the API site is generated from source and `dejavu/Module.md`.
Use the pinned tools in a virtual environment:

```bash
python3 -m venv /tmp/dejavu-docs-env
/tmp/dejavu-docs-env/bin/pip install -r .github/workflows/mkdocs-requirements.txt
./gradlew -q --console=plain :dejavu:dokkaGeneratePublicationHtml
python3 validation/docs.py repair-api docs/api
/tmp/dejavu-docs-env/bin/mkdocs build --strict
python3 validation/docs.py verify site
```

Update `extra.dejavu_release` in `mkdocs.yml`, dependency examples, compatibility guidance, and
release links together when publishing a new version. Historical release records retain their
original versions. `snapshot` is development documentation; `latest` is the stable release.
The release checklist covers docs-only corrections and redirects from old unversioned URLs.
Prose-only edits require a strict documentation build and link checks, not the full UI matrix.

## Pull requests and API changes

Branch from `main`. Explain the concrete behavior change and include the checks you ran and
any limitations. Add regressions for fixes and new behavior; preserve intentional inefficient
fixtures. Follow existing Kotlin style and add KDoc to new public APIs.

When changing the public API, run `./gradlew -q --console=plain apiDump`, review the generated
changes, commit the API files, and run `apiCheck`. Do not accept an API diff solely to make a
failing check green.

Use the [bug template](https://github.com/mttmcknn/dejavu/issues/new?template=bug_report.md) for
reproducible problems and the [feature template](https://github.com/mttmcknn/dejavu/issues/new?template=feature_request.md)
for proposals. Include the DejaVu version, Compose BOM or Multiplatform version, platform, and
an independent expected recomposition count. Follow [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)
and [SECURITY.md](SECURITY.md).

## Agent skills and their evaluations

Canonical skills live in real directories under `skills/`; edit them there and keep
their bundled references portable. The `.claude/skills/` and `.agents/skills/` links
share those files. The Claude Code plugin and cross-agent Skills CLI distribute
the same bundles. See the [installation guide](docs/agent-skills.md).
Run the offline checks after editing skills, packaging or the evaluation corpus:

```bash
python3 validation/skills.py
python3 evals/run.py validate
python3 -m unittest discover -s evals/tests -p 'test_*.py'
```

See [the evaluator guide](evals/README.md) for isolated no-skill, forced and automatic
runs, comparing old/new skill revisions under fixed model settings, budget caps,
and human auditing. Model results are advisory and are never CI release gates.
The initial source-excerpt corpus checks agent decisions; library changes still
require the actual UI test matrix above.
