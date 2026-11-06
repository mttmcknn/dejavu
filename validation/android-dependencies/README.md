# Android publication dependency regression

This standalone Android consumer checks the packaged candidate with **regular** Compose BOMs.
An enforced platform would hide a transitive upgrade caused by the library's publication.
The fixture checks compile and runtime graphs, resolves Dejavu without a consumer BOM, and runs
an actual Robolectric UI test against an independent unkeyed `SideEffect` counter. It declares
no tooling dependency: the published Android artifact must supply the required tooling classes.

Build one candidate at the release baseline into an isolated local repository, then reuse that
same binary with each supported consumer BOM. From the repository root:

```bash
candidate_repo="$PWD/build/validation-maven"
./gradlew -q --console=plain --no-daemon --max-workers=2 \
  -Pkotlin.compiler.execution.strategy=in-process \
  -PvalidationRepository="$candidate_repo" \
  :dejavu:publishAndroidReleasePublicationToValidationRepository \
  :dejavu:publishKotlinMultiplatformPublicationToValidationRepository

./gradlew -p validation/android-dependencies -q --console=plain --no-daemon --max-workers=2 \
  -Pkotlin.compiler.execution.strategy=in-process \
  -PvalidationRepository="$candidate_repo" -PdejavuVersion=0.6.0-SNAPSHOT \
  -PcomposeBomVersion=2026.05.00 -PexpectedComposeVersion=1.11.1 \
  verifyDependencyIsolation testDebugUnitTest

python3 validation/verify_test_results.py validation/android-dependencies/build/test-results/testDebugUnitTest
```

Use the candidate's actual version. Repeat for the other supported BOM checkpoints and baseline,
passing each BOM's expected Android Compose version. Preserve the XML report after each run.

| Consumer BOM | Expected Compose version |
|---|---|
| 2026.05.00 | 1.11.1 |
| 2026.06.01 | 1.11.4 |
| 2026.08.00 | 1.12.0 |

Repeat with `-PpomOnly=true` to consume the Android artifact's POM without Gradle metadata.
Kotlin's target-dependency rewriting can otherwise reintroduce the resolved build baseline in
the POM even when Gradle module metadata preserves the declared version.

The graph assertions check `runtime-android`, `ui-android`, `ui-test-junit4-android`, and runtime
`ui-tooling-data-android`, and fail if the consumer BOM was replaced. Missing candidate artifacts
fail resolution instead of falling back to remote artifacts. No Maven Local repository is used.

The smoke test uses `runRecompositionTrackingUiTest` to avoid the independently investigated
Android-rule idle-loop issue under Robolectric. It checks a nonzero exact recomposition count,
the visible UI change, and a rejected zero budget. This validates Android dependency packaging;
it does not replace the wider platform or device release matrix.
