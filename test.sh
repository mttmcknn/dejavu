#!/usr/bin/env bash
set -euo pipefail

all_boms=false
skip_instrumentation=false

for arg in "$@"; do
  case "$arg" in
    --all-boms) all_boms=true ;;
    --skip-instrumentation-tests) skip_instrumentation=true ;;
    --help|-h)
      cat <<'EOF'
Usage: ./test.sh [options]

Options:
  --all-boms                    Run Android build and UI tests at every supported BOM checkpoint.
  --skip-instrumentation-tests  Skip connected Android UI tests.
  --help, -h                    Show this help message.

Set ANDROID_SERIAL when instrumentation tests are enabled so the run cannot select another device.
EOF
      exit 0
      ;;
    *)
      echo "Unknown option: $arg" >&2
      exit 1
      ;;
  esac
done

if [[ "$skip_instrumentation" == false ]]; then
  : "${ANDROID_SERIAL:?Set ANDROID_SERIAL to the emulator that should run UI tests}"
  adb -s "$ANDROID_SERIAL" get-state >/dev/null
fi

validation_dir="${DEJAVU_VALIDATION_DIR:-build/release-validation/$(date -u +%Y%m%dT%H%M%SZ)}"
mkdir -p "$validation_dir"

baseline=$(sed -n 's/^composeBom = "\(.*\)"/\1/p' gradle/libs.versions.toml)
test -n "$baseline"

boms=("$baseline")
if [[ "$all_boms" == true ]]; then
  boms=()
  while IFS= read -r bom; do
    [[ -n "$bom" ]] && boms+=("$bom")
  done < <(sed -n 's/^composeBomCompat[^=]* = "\(.*\)"/\1/p' gradle/libs.versions.toml)
  boms+=("$baseline")
fi

./gradlew -q --console=plain -PrerunTests \
  verifyComposeBomConfiguration \
  :dejavu:verifyAll \
  :compose-experimental:jvmTest \
  :compose-experimental:iosSimulatorArm64Test \
  :compose-experimental:wasmJsBrowserTest \
  :demo-desktop:compileKotlinJvm \
  :demo-wasm:compileKotlinWasmJs

for module in dejavu compose-experimental; do
  for task in jvmTest iosSimulatorArm64Test wasmJsBrowserTest; do
    python3 validation/verify_test_results.py "$module/build/test-results/$task"
    mkdir -p "$validation_dir/$module/$task"
    cp -R "$module/build/test-results/$task/." "$validation_dir/$module/$task/"
  done
done

for bom in "${boms[@]}"; do
  echo "Validating Android Compose BOM $bom"
  ./gradlew -q --console=plain -PrerunTests \
    :dejavu:compileDebugKotlin \
    :dejavu:testDebugUnitTest \
    :demo:assembleDebug \
    :demo:assembleDebugAndroidTest \
    :compose-experimental:assembleDebug \
    :compose-experimental:assembleDebugAndroidTest \
    -PcomposeBomVersion="$bom"
  python3 validation/verify_test_results.py dejavu/build/test-results/testDebugUnitTest
  mkdir -p "$validation_dir/$bom/unit"
  cp -R dejavu/build/test-results/testDebugUnitTest/. "$validation_dir/$bom/unit/"

  if [[ "$skip_instrumentation" == false ]]; then
    ./gradlew -q --console=plain \
      :demo:connectedDebugAndroidTest \
      :compose-experimental:connectedDebugAndroidTest \
      -PcomposeBomVersion="$bom"
    for module in demo compose-experimental; do
      python3 validation/verify_test_results.py "$module/build/outputs/androidTest-results/connected"
      mkdir -p "$validation_dir/$bom/$module"
      cp -R "$module/build/outputs/androidTest-results/connected/." "$validation_dir/$bom/$module/"
    done
  fi
done

echo "Local verification passed. Reports: $validation_dir"
