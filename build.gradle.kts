// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.multiplatform) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.compose.multiplatform) apply false
  alias(libs.plugins.dokka) apply false
  alias(libs.plugins.binary.compatibility.validator)
}

apiValidation {
  ignoredProjects += listOf("compose-experimental", "demo", "demo-shared", "demo-desktop", "demo-wasm")
  @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
  klib {
    enabled = true
  }
}

// Release verification must execute tests instead of reusing historical test reports. Keep
// compilation caches enabled; only test tasks are forced to run by the local release script.
val rerunReleaseTests = providers.gradleProperty("rerunTests").isPresent
allprojects {
  tasks.withType<org.gradle.api.tasks.testing.AbstractTestTask>().configureEach {
    if (rerunReleaseTests) outputs.upToDateWhen { false }
  }
}

val composeVersions = extensions
  .getByType<org.gradle.api.artifacts.VersionCatalogsExtension>()
  .named("libs")

tasks.register("verifyComposeBomConfiguration") {
  group = "verification"
  description = "Verify the Compose release baseline, compatibility checkpoints, and docs stay aligned"

  doLast {
    val baseline = composeVersions.findVersion("composeBom").get().requiredVersion
    val composeMultiplatform = composeVersions.findVersion("composeMultiplatform").get().requiredVersion
    val checkpoints = composeVersions.versionAliases
      .filter { it.startsWith("composeBomCompat") }
      .map { composeVersions.findVersion(it).get().requiredVersion }
      .sorted()
    val supported = (checkpoints + baseline).distinct()

    check(checkpoints.isNotEmpty()) { "At least one composeBomCompat* checkpoint is required" }
    check(checkpoints.size == checkpoints.distinct().size) { "Compose BOM checkpoints must be unique" }
    check(baseline !in checkpoints) { "Release BOM $baseline must not duplicate a compatibility checkpoint" }
    check(supported == supported.sorted()) { "Release BOM $baseline must be at or above every compatibility checkpoint" }

    val readme = file("README.md").readText()
    supported.forEach { bom ->
      check(readme.contains("| $bom |")) { "README compatibility table is missing Compose BOM $bom" }
    }
    check(readme.contains("Compose Multiplatform $composeMultiplatform")) {
      "README must name the Compose Multiplatform $composeMultiplatform release baseline"
    }

    logger.lifecycle("Validated Compose BOMs: ${supported.joinToString()} (CMP $composeMultiplatform)")
  }
}
