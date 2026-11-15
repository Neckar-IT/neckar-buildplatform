package it.neckar.gradle.openapi

import it.neckar.gradle.BuildplatformPlugins
import it.neckar.gradle.VerificationGroup
import it.neckar.gradle.useTestClassesOf
import it.neckar.gradle.withUnitTestTask
import it.neckar.projects.ConfiguredProject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.register

/**
 * The `openapiSpec` task path of this project — for consumer modules whose build depends on the
 * generated OpenAPI spec (Orval type generation, pnpm frontend builds):
 *
 * ```
 * dependsOn(huddleService.openapiSpecTask) // huddleService: the registry entry of the backend
 * ```
 *
 * Lives next to [OpenApiGenerationConfigPlugin], which registers the task — the task name is
 * spelled in exactly one place.
 */
val ConfiguredProject.openapiSpecTask: String
  get() = task(OpenApiGenerationConfigPlugin.OpenapiSpecTaskName)

/**
 * Configures the project for OpenAPI generation (from JUnit tests).
 *
 * Registers a dedicated `openapiSpec` task of type [Test] that runs only the
 * OpenAPI-producing test classes (`*OpenApiTest` on Kotlin/JVM services,
 * `*OpenApiGeneratorTest` on Kotlin/MPP common libraries) and declares
 * `outputs.dir("generated/openapi")` as its sole producer.
 *
 * Intended for dev-loop usage (`./gradlew :module:openapiSpec -t`) and for
 * downstream tasks like the Orval conversion in UI modules.
 *
 * Registered on the test classes of `test` (Kotlin/JVM) or `jvmTest` (Kotlin/MPP),
 * see [withUnitTestTask].
 */
class OpenApiGenerationConfigPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    //Always apply the KSP serialization plugin - verify that serialization is done correctly
    target.plugins.apply(BuildplatformPlugins.KspSerialization)

    target.withUnitTestTask { unitTest ->
      target.tasks.register<Test>(OpenapiSpecTaskName) {
        configureOpenapiSpec()
        useTestClassesOf(unitTest)
        outputs.dir(target.layout.buildDirectory.dir("generated/openapi"))
      }
    }
  }

  companion object {
    const val OpenapiSpecTaskName: String = "openapiSpec"
  }

  /**
   * Shared Test task configuration. File-level `include` on compiled test classes;
   * `filter.includeTestsMatching()` did not reliably restrict the discovered test
   * set across all modules, so this stops non-OpenAPI tests from being loaded.
   */
  private fun Test.configureOpenapiSpec() {
    description = "Regenerates the OpenAPI JSON spec by running only OpenAPI-producing test classes"
    group = VerificationGroup
    useJUnitPlatform()
    include("**/*OpenApiTest.class", "**/*OpenApiGeneratorTest.class")
  }
}
