plugins {
  id("com.android.application")
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "demo.app"
  compileSdk = 37
  defaultConfig {
    applicationId = "demo.app"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  buildTypes {
    release {
      isMinifyEnabled = false
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    freeCompilerArgs.addAll(
      "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
      "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
    )
  }
}

dependencies {
  // Compatibility runs enforce the requested BOM so transitive Compose Multiplatform
  // dependencies cannot make an old-BOM test silently run on the release baseline.
  val composeBomVersion = project.findProperty("composeBomVersion") as? String
  val composeBom = if (composeBomVersion != null) {
    enforcedPlatform("androidx.compose:compose-bom:$composeBomVersion")
  } else {
    platform(libs.androidx.compose.bom)
  }
  implementation(composeBom)
  implementation(project(":demo-shared"))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.ui)
  implementation(libs.androidx.material3)
  implementation(libs.kotlinx.coroutines.android)

  // Dejavu recomposition tracker
  debugImplementation(project(":dejavu"))
  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)

  // Android instrumented tests
  androidTestImplementation(project(":dejavu"))
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(composeBom)
  androidTestImplementation(libs.androidx.ui.test.junit4)
  androidTestImplementation(libs.kotlinx.coroutines.test)
  androidTestImplementation(libs.truth)
}
