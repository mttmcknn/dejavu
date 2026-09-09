plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.dokka)
  alias(libs.plugins.vanniktech.maven.publish)
}

group = "me.mmckenna.dejavu"
version = "0.6.0-SNAPSHOT"

kotlin {
  explicitApi()

  androidTarget {
    compilations.all {
      compileTaskProvider.configure {
        compilerOptions {
          jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
      }
    }
    publishLibraryVariants("release")
  }

  jvm {
    compilations.all {
      compileTaskProvider.configure {
        compilerOptions {
          jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
      }
    }
  }

  iosArm64()
  iosSimulatorArm64()

  wasmJs {
    browser()
    // Compose 1.12 requires webpack bundling to load Skiko for browser UI tests.
    binaries.executable()
  }

  sourceSets {
    val iosMain by creating {
      dependsOn(commonMain.get())
    }
    val iosArm64Main by getting { dependsOn(iosMain) }
    val iosSimulatorArm64Main by getting { dependsOn(iosMain) }

    val iosTest by creating {
      dependsOn(commonTest.get())
    }
    val iosArm64Test by getting { dependsOn(iosTest) }
    val iosSimulatorArm64Test by getting { dependsOn(iosTest) }

    commonMain.dependencies {
      compileOnly(compose.runtime)
      compileOnly(compose.ui)
      @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
      compileOnly(compose.uiTest)
      implementation(libs.kotlinx.atomicfu)
    }

    commonTest.dependencies {
      implementation(kotlin("test"))
      implementation(compose.runtime)
      implementation(compose.ui)
      implementation(compose.foundation)
      implementation(compose.animation)
      @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
      implementation(compose.uiTest)
    }

    androidMain.dependencies {
      compileOnly(libs.androidx.ui.tooling)
      implementation(libs.kotlinx.coroutines.android)
      implementation("androidx.compose.ui:ui-tooling-data")
      // Needed for DejavuComposeTestRule (Android-specific JUnit4 rule)
      compileOnly("androidx.compose.ui:ui-test-junit4")
      // Pinned to avoid Gradle resolution conflict with BOM-managed transitive version; compileOnly, never shipped
      compileOnly("androidx.activity:activity-compose:1.13.0")
      // Pinned to avoid Gradle resolution conflict with BOM-managed transitive version; compileOnly, never shipped
      compileOnly("androidx.test.ext:junit:1.1.5")
    }

    val jvmTest by getting {
      dependencies {
        implementation(compose.runtime)
        implementation(compose.desktop.currentOs)
      }
    }

    val androidUnitTest by getting {
      dependencies {
        implementation(libs.junit)
        implementation(libs.truth)
        implementation(libs.robolectric)
        implementation(compose.runtime)
      }
    }
  }
}

android {
  namespace = "dejavu"
  compileSdk = 37
  defaultConfig {
    minSdk = 24
    consumerProguardFiles("consumer-rules.pro")
  }
  buildTypes {
    release {
      isMinifyEnabled = false
    }
  }
  lint {
    warningsAsErrors = true
    abortOnError = true
    // Suppress upgrade-nag and version-catalog migration checks
    disable += setOf(
      "GradleDependency",
      "NewerVersionAvailable",
      "AndroidGradlePluginVersion",
      "UseTomlInstead",
    )
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      all {
        // Compose UI tests use runComposeUiTest which requires Robolectric on Android;
        // these tests run on JVM desktop instead. Android UI tests run as instrumented tests.
        // Compose UI tests (runComposeUiTest) live in the dejavu/ package;
        // unit tests live in dejavu/internal/. Glob excludes all UI tests
        // automatically — no need to add each new test class manually.
        it.exclude("dejavu/*Test.class")
      }
    }
  }
}

// The normal build uses Compose's recommended platform. Compatibility checks use an
// enforced platform so a newer Compose Multiplatform dependency cannot silently win over
// the older BOM that CI was explicitly asked to validate.
dependencies {
  val composeBomVersion = project.findProperty("composeBomVersion") as? String
  val composeBom = if (composeBomVersion != null) {
    enforcedPlatform("androidx.compose:compose-bom:$composeBomVersion")
  } else {
    platform(libs.androidx.compose.bom)
  }
  "androidMainCompileOnly"(composeBom)
  "androidMainImplementation"(composeBom)
  "androidUnitTestImplementation"(composeBom)
}

composeCompiler {
  includeSourceInformation = true
}

// ── Aggregate verification tasks ────────────────────────────────────────
//
// ./gradlew :dejavu:compileAll      — compile every supported KMP target (no tests)
// ./gradlew :dejavu:testAll         — run compose UI + unit tests on every runnable target
// ./gradlew :dejavu:verifyAll       — compileAll + testAll + apiCheck + lint

tasks.register("compileAll") {
  group = "verification"
  description = "Compile all KMP targets (Android, JVM, iOS arm64/simulatorArm64, WasmJs)"
  dependsOn(
    "compileDebugKotlinAndroid",
    "compileKotlinJvm",
    "compileKotlinIosArm64",
    "compileKotlinIosSimulatorArm64",
    "compileKotlinWasmJs",
  )
}

tasks.register("testAll") {
  group = "verification"
  description = "Run tests on all runnable targets (Android unit, JVM desktop, iOS simulator, WasmJs browser)"
  dependsOn(
    "testDebugUnitTest",         // Android unit tests (excludes compose UI tests)
    "jvmTest",                   // Desktop JVM — unit + compose UI tests
    "iosSimulatorArm64Test",     // iOS — compose UI tests on ARM simulator
    "wasmJsBrowserTest",         // WasmJs — compose UI tests in headless browser
  )
}

tasks.register("verifyAll") {
  group = "verification"
  description = "Full verification: compile all targets, run all tests, API check, and lint"
  dependsOn("compileAll", "testAll")
}

// Wire apiCheck and lintDebug into verifyAll after the tasks are resolved
afterEvaluate {
  tasks.named("verifyAll") {
    dependsOn("apiCheck", "lintDebug")
  }
}

mavenPublishing {
  publishToMavenCentral(automaticRelease = true)

  // Only sign when GPG credentials are available (skips signing for CI mavenLocal publishes)
  if (project.hasProperty("signing.keyId") || System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null) {
    signAllPublications()
  }

  coordinates("me.mmckenna.dejavu", "dejavu", version.toString())

  pom {
    name.set("Dejavu")
    description.set("Implicit recomposition tracking for Jetpack Compose UI tests")
    url.set("https://github.com/mttmcknn/dejavu")
    licenses {
      license {
        name.set("The Apache License, Version 2.0")
        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
      }
    }
    developers {
      developer {
        id.set("mttmcknn")
        name.set("Matt McKenna")
        url.set("https://blog.mmckenna.me")
      }
    }
    scm {
      url.set("https://github.com/mttmcknn/dejavu")
      connection.set("scm:git:git://github.com/mttmcknn/dejavu.git")
      developerConnection.set("scm:git:ssh://github.com/mttmcknn/dejavu.git")
    }
  }
}

dokka {
  dokkaPublications.html {
    moduleVersion.set(providers.gradleProperty("docsVersion").orElse(project.version.toString()))
    includes.from("Module.md")
    outputDirectory.set(rootProject.layout.projectDirectory.dir("docs/api"))
  }
  dokkaSourceSets.configureEach {
    sourceLink {
      localDirectory.set(projectDir.resolve("src"))
      val sourceRef = providers.gradleProperty("docsSourceRef").orElse("main").get()
      remoteUrl("https://github.com/mttmcknn/dejavu/blob/$sourceRef/dejavu/src")
      remoteLineSuffix.set("#L")
    }
    documentedVisibilities(
      org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier.Public
    )
    perPackageOption {
      matchingRegex.set(".*\\.internal.*")
      suppress.set(true)
    }
    reportUndocumented.set(true)
  }
}
