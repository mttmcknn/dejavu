# Setup contract for DejaVu 0.5.0

This is a versioned reference, not a claim that 0.5.0 is always the newest release.
Check the requested release's documentation when working with another version.

| DejaVu | Compose Multiplatform | Android guidance |
|---|---|---|
| 0.5.0 | 1.12.0; validated Kotlin 2.4.0 | compile SDK 37, min SDK 24; BOM 2026.05.00, 2026.06.01 or 2026.08.00 enforced in app and androidTest |
| 0.4.0 | 1.11.1 | compile SDK 36 baseline; use its versioned setup |
| 0.3.1 | 1.10 | use its versioned setup |

Do not infer KMP 1.11 compatibility from Android 1.11 compatibility: they are
separate support claims. Keep a compatible newer release already installed.
The Compose compiler plugin must match Kotlin. Enable compiler source information
in modules containing tracked composables. Use JVM 17+ for Android/Desktop.

## Android

Merge into existing configuration; preserve catalog aliases and the chosen BOM.
DejaVu's Compose test APIs are compile-only, so declare the test dependencies.

```kotlin
android {
    compileSdk = 37
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}
composeCompiler { includeSourceInformation = true }
dependencies {
    val composeBom = enforcedPlatform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    androidTestImplementation("me.mmckenna.dejavu:dejavu:0.5.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

For an older supported Android BOM, use that value in the same enforced-platform
declaration. A normal platform can resolve newer transitive Compose artifacts.
The 0.5.0 Android artifact still needs compile SDK 37.

Use `@get:Rule val rule = createRecompositionTrackingRule()` for test-owned
content, or the generic activity factory for a screen launched by that activity.
Import extensions from `dejavu` explicitly. Preserve the existing host and clock;
both activity-backed and plain Compose rules offer clock controls, but their
lifecycle and content ownership differ.

## KMP

With the Compose Multiplatform plugin and its dependency accessors:

```kotlin
kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("me.mmckenna.dejavu:dejavu:0.5.0")
            implementation(compose.foundation)
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
        jvmTest.dependencies { implementation(compose.desktop.currentOs) }
    }
}
composeCompiler { includeSourceInformation = true }
```

Use the project's actual desktop source-set name. Tests import
`dejavu.runRecompositionTrackingUiTest`, `dejavu.setTrackedContent`, assertion
extensions and `kotlin.test.Test`; opt in to `androidx.compose.ui.test.ExperimentalTestApi`.

```kotlin
@Test
fun updates() = runRecompositionTrackingUiTest {
    setTrackedContent { /* existing composable */ }
    // Settle, reset counts, interact, settle, assert UI and recomposition counts here.
}
```

The expression body returns `TestResult`, which is necessary on Wasm. Do not
wrap it in a Unit-returning function or move assertions outside its body.

Public [setup](https://dejavu.mmckenna.me/0.5.0/getting-started/) and
[release history](https://dejavu.mmckenna.me/latest/releases/).
