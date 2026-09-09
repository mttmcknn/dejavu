import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.library)
}
repositories { mavenLocal(); google(); mavenCentral() }
val androidBom = providers.gradleProperty("composeBomVersion").orElse(libs.versions.composeBom).get()
val releaseVersion = providers.gradleProperty("dejavuVersion").get()
kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    androidTarget {
        instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            }
        }
    }
    jvm()
    iosSimulatorArm64()
    wasmJs { browser(); binaries.executable() }
    sourceSets {
        commonMain.dependencies { implementation(compose.ui) }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("me.mmckenna.dejavu:dejavu:$releaseVersion")
            implementation(compose.foundation)
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
        jvmTest.dependencies { implementation(compose.desktop.currentOs) }
        androidInstrumentedTest.dependencies {
            implementation("androidx.compose.ui:ui-test-junit4")
            implementation("androidx.test.ext:junit:1.3.0")
        }
        if (androidBom >= "2026.08.00") {
            androidInstrumentedTest.get().kotlin.srcDir("src/android112Test/kotlin")
        }
    }
}
android {
    namespace = "dejavu.consumer"
    compileSdk = 37
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}
dependencies {
    val bom = enforcedPlatform("androidx.compose:compose-bom:$androidBom")
    "androidMainImplementation"(bom)
    "androidInstrumentedTestImplementation"(bom)
    "debugImplementation"("androidx.compose.ui:ui-test-manifest")
}
composeCompiler { includeSourceInformation = true }
