plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    compileSdk = 37
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}
composeCompiler { includeSourceInformation = true }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
}
