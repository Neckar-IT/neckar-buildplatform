package it.neckar.gradle

import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType
import org.gradle.testing.base.TestingExtension

/**
 * Registers a [JvmTestSuite] that is excluded from `check`/`build` and only runs when explicitly invoked
 * (e.g., `./gradlew :module:integrationTest`).
 *
 * Applies common defaults:
 * - JUnit Jupiter test framework
 * - `shouldRunAfter(test)` ordering
 * - `outputs.upToDateWhen { false }` (always re-run)
 * - Excluded from the `check` lifecycle
 * - Excluded from Kover coverage instrumentation
 * - Runs without the timeout guards of the build — see [removeTimeoutGuards]
 *
 * Rejects a [name] that fails [TestSourceSetNames.hasTestSuffix].
 *
 * Use [configure] for module-specific setup (dependencies, additional testTask configuration).
 *
 * Usage:
 * ```kotlin
 * registerIntegrationTestSuite("integrationTest") {
 *   dependencies {
 *     implementation(project())
 *     implementation("it.neckar.open:test-utils")
 *   }
 * }
 * ```
 */
@Suppress("UnstableApiUsage")
fun Project.registerIntegrationTestSuite(name: String, configure: JvmTestSuite.() -> Unit) {
  require(TestSourceSetNames.hasTestSuffix(name)) {
    "Integration test suite <$name> in $path must end in Test or Tests, otherwise detekt, the compiler flags and the repository checks treat it as production code"
  }

  extensions.getByType<TestingExtension>().suites.register(name, JvmTestSuite::class.java) {
    useJUnitJupiter()

    getTargets().all {
      getTestTask().configure {
        shouldRunAfter(tasks.named("test"))
        outputs.upToDateWhen { false }
        // An integration test reads a service or a database, which is no input Gradle sees; a cache hit would skip it.
        outputs.cacheIf { false }
        removeTimeoutGuards()
      }
    }

    configure()
  }

  // Keeps the suite out of check even when a plugin wires it in; Gradle 9.7.1 itself adds only the default `test` suite.
  tasks.named("check") {
    setDependsOn(dependsOn.filterNot { dependency ->
      when (dependency) {
        is TaskProvider<*> -> dependency.name == name
        is Task -> dependency.name == name
        else -> dependency.toString().contains(name)
      }
    })
  }

  // Disable Kover instrumentation for this suite — otherwise an explicit koverHtmlReport (the job
  // `⏲ verify main nightly`) would instrument and run the integration-test suite as part of coverage collection.
  extensions.getByType<KoverProjectExtension>().currentProject.instrumentation.disabledForTestTasks.add(name)
}
