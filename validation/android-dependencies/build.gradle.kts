import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val candidateRepository = providers.gradleProperty("validationRepository").get()
val candidateVersion = providers.gradleProperty("dejavuVersion").get()
val androidBom = providers.gradleProperty("composeBomVersion").get()
val expectedComposeVersion = providers.gradleProperty("expectedComposeVersion").get()
val pomOnly = providers.gradleProperty("pomOnly").map(String::toBoolean).getOrElse(false)
val candidateArtifact = if (pomOnly) "dejavu-android" else "dejavu"
val candidate = "dev.mttmcknn:$candidateArtifact:$candidateVersion"
repositories {
    // Prevent a missing candidate from silently falling back to a remote release.
    exclusiveContent {
        forRepository {
            maven {
                url = uri(file(candidateRepository))
                if (pomOnly) metadataSources {
                    mavenPom()
                    ignoreGradleMetadataRedirection()
                }
            }
        }
        filter { includeGroup("dev.mttmcknn") }
    }
    google()
    mavenCentral()
}

android {
    namespace = "dejavu.consumer.dependencies"
    compileSdk = 37
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions.unitTests.isIncludeAndroidResources = true
}
kotlin { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
composeCompiler { includeSourceInformation = true }

val withoutConsumerBom by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment.ANDROID))
    }
}

dependencies {
    // Deliberately regular: enforcing a BOM would conceal a leaked publication baseline.
    val bom = platform("androidx.compose:compose-bom:$androidBom")
    testImplementation(bom)
    debugImplementation(bom)
    testImplementation(candidate)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.compose.foundation:foundation")
    testImplementation(libs.androidx.activity.compose)
    testImplementation(libs.robolectric)
    testImplementation(libs.junit)
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // No direct tooling dependency: the published artifact must supply it.
    add(withoutConsumerBom.name, candidate)
}

tasks.register("verifyDependencyIsolation") {
    group = "verification"
    doLast {
        listOf("debugUnitTestCompileClasspath", "debugUnitTestRuntimeClasspath", withoutConsumerBom.name).forEach { name ->
            val result = configurations.getByName(name).incoming.resolutionResult
            val unresolved = result.allDependencies.filterIsInstance<UnresolvedDependencyResult>()
            check(unresolved.isEmpty()) { "$name has unresolved dependencies: $unresolved" }
            val modules = result.allComponents.mapNotNull { it.moduleVersion }.associateBy { "${it.group}:${it.name}" }
            check("dev.mttmcknn:dejavu-android" in modules) { "$name did not select the published Android artifact" }
            if (name == withoutConsumerBom.name) {
                check("androidx.compose:compose-bom" !in modules) { "The publication still exports its Compose BOM" }
                check(modules["androidx.compose.ui:ui-tooling-data-android"]?.version == libs.versions.composeUiMinimum.get()) {
                    "Runtime tooling must resolve to the supported floor without a consumer BOM"
                }
            } else {
                check(modules["androidx.compose:compose-bom"]?.version == androidBom) { "$name replaced consumer BOM $androidBom" }
                listOf("androidx.compose.runtime:runtime-android", "androidx.compose.ui:ui-android", "androidx.compose.ui:ui-test-junit4-android").forEach { module ->
                    check(modules[module]?.version == expectedComposeVersion) {
                        "$name selected ${modules[module]} instead of $module:$expectedComposeVersion"
                    }
                }
                if (name.endsWith("RuntimeClasspath")) {
                    check(modules["androidx.compose.ui:ui-tooling-data-android"]?.version == expectedComposeVersion) {
                        "Runtime tooling does not match the consumer's Compose version"
                    }
                }
            }
            println("PASS $name: " + modules.values.filter {
                it.group.startsWith("androidx.compose") || it.group == "dev.mttmcknn"
            }.sortedBy { it.toString() }.joinToString())
        }
    }
}

tasks.withType<Test>().configureEach {
    dependsOn("verifyDependencyIsolation")
    maxHeapSize = "2g"
    maxParallelForks = 1
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
    testLogging { events("passed", "failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
