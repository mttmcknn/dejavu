import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

// ============================================================================
// compose-experimental: staging area for experimental-API recomposition coverage.
//
// This module hosts Dejavu recomposition tests for EXPERIMENTAL / newest-Compose
// APIs (Compose 1.11 layouts/styles and Compose 1.12 effects/testing behavior,
// including LinkBuffer and movableContentOf).
//
// They live here instead of `:dejavu`'s commonTest so new API coverage can land
// immediately and graduate into the core accuracy suite once those APIs stabilize.
// KMP uses the pinned Compose Multiplatform baseline; Android builds and runs this
// module at every supported Android BOM checkpoint with matching API fixtures.
//
// PROMOTION: when an experimental API graduates to stable AND the `:dejavu` BOM
// floor includes it, its composable + SideEffect-backed test is promoted into
// `dejavu/src/commonTest` (the accuracy suite) and removed here. See README.md.
// ============================================================================

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  @OptIn(ExperimentalKotlinGradlePluginApi::class)
  androidTarget {
    instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
    compilations.all {
      compileTaskProvider.configure {
        compilerOptions {
          jvmTarget.set(JvmTarget.JVM_17)
        }
      }
    }
  }

  jvm {
    compilations.all {
      compileTaskProvider.configure {
        compilerOptions {
          jvmTarget.set(JvmTarget.JVM_17)
        }
      }
    }
  }

  iosSimulatorArm64()
  wasmJs {
    browser()
    // Compose 1.12 requires webpack bundling to load Skiko for browser UI tests.
    binaries.executable()
  }

  sourceSets {
    // Keep the old Android compatibility suite compiling when a newer Compose API changes.
    // KMP always uses the pinned release baseline; Android overrides select matching fixtures.
    val testBom = providers.gradleProperty("composeBomVersion")
      .orElse(libs.versions.composeBom).get()
    commonTest.get().kotlin.srcDir(
      if (testBom >= "2026.08.00") "src/compose112Test/kotlin" else "src/compose111Test/kotlin"
    )

    commonMain.dependencies {
      implementation(project(":dejavu"))
      implementation(compose.runtime)
      implementation(compose.ui)
      implementation(compose.foundation)
    }

    commonTest.dependencies {
      implementation(kotlin("test"))
      implementation(project(":dejavu"))
      implementation(libs.kotlinx.atomicfu)
      implementation(compose.runtime)
      implementation(compose.ui)
      implementation(compose.foundation)
      @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
      implementation(compose.uiTest)
    }

    val jvmTest by getting {
      dependencies {
        implementation(compose.desktop.currentOs)
      }
    }

    val androidInstrumentedTest by getting {
      dependencies {
        implementation(libs.androidx.junit)
        implementation(libs.androidx.espresso.core)
        implementation(libs.androidx.ui.test.junit4)
      }
    }
  }
}

android {
  namespace = "dejavu.experimental"
  compileSdk = 37

  defaultConfig {
    minSdk = 24
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

dependencies {
  val composeBomVersion = project.findProperty("composeBomVersion") as? String
  val composeBom = if (composeBomVersion != null) {
    enforcedPlatform("androidx.compose:compose-bom:$composeBomVersion")
  } else {
    platform(libs.androidx.compose.bom)
  }
  "androidMainImplementation"(composeBom)
  "androidInstrumentedTestImplementation"(composeBom)
  "debugImplementation"(libs.androidx.ui.test.manifest)
}
