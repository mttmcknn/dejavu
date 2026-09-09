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
    val bom = enforcedPlatform("androidx.compose:compose-bom:2026.06.01")
    implementation(bom)
    androidTestImplementation(bom)
    androidTestImplementation("me.mmckenna.dejavu:dejavu:0.5.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
