# Published artifact smoke tests

This standalone build consumes Maven artifacts, without a project dependency or composite build.
It checks the same packaged Android binary against supported Compose BOMs. Recompiling DejaVu
against each BOM in the main suite cannot reveal every binary compatibility problem.

Publish the candidate locally once using the release baseline, then run from the repository root:

```bash
./gradlew :dejavu:publishToMavenLocal --no-configuration-cache
ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew -p validation/consumer \
  jvmTest iosSimulatorArm64Test wasmJsBrowserTest -PdejavuVersion=0.5.0
ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SERIAL=emulator-5554 \
  ./gradlew -p validation/consumer connectedDebugAndroidTest \
  -PdejavuVersion=0.5.0 -PcomposeBomVersion=2026.05.00
python3 validation/verify_test_results.py \
  validation/consumer/build/test-results/jvmTest \
  validation/consumer/build/test-results/iosSimulatorArm64Test \
  validation/consumer/build/test-results/wasmJsBrowserTest \
  validation/consumer/build/outputs/androidTest-results/connected
```

Repeat the Android command for the other version-catalog checkpoints and baseline. Run on the
selected emulator only after the main instrumentation suite has finished. Preserve XML reports
per BOM before the next run overwrites them. Supply your SDK location and current release version.
Check the Android reports after every BOM run. A successful Gradle exit alone is insufficient:
an emulator installation failure can leave no executed test reports. The verifier requires at
least one actual test and no failures, errors, or skips in every supplied directory.

The common smoke test suspends inside the public test helper, compares an exact recomposition
count with an independent unkeyed `SideEffect`, and catches the expected over-budget failure.
Android additionally instantiates the packaged test rule. The 1.12 baseline tests the new delegated
`runWithoutImplicitWait` and `hasPendingWork` methods; older BOMs do not call APIs they lack.
