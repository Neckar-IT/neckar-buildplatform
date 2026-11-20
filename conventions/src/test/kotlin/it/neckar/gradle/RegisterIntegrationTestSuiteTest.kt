package it.neckar.gradle

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.messageContains
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.testing.Test as TestTask
import org.gradle.kotlin.dsl.apply
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Integration-test suites must never run as part of `build` / `check` — they are slow, hit external
 * services and are started explicitly. `registerIntegrationTestSuite` is what enforces that, and the
 * enforcement is invisible until a pipeline suddenly starts a testcontainer.
 */
class RegisterIntegrationTestSuiteTest {

  @TempDir
  lateinit var projectDir: File

  /**
   * Mirrors what `configureJvmModule` has applied by the time a module calls
   * `registerIntegrationTestSuite`: the java plugin owning `check` and the test suites, Kover, whose
   * extension the helper uses to keep the suite out of coverage collection, and the timeout guards
   * that `removeTimeoutGuards` takes off the suite again.
   */
  private fun jvmProject(): Project = ProjectBuilder.builder().withProjectDir(projectDir).build().also {
    it.apply(plugin = "java-library")
    it.apply(plugin = BuildplatformPlugins.Kover)
    it.configureJunit()
  }

  @Test
  fun `the suite registers a test task of its own`() {
    val project = jvmProject()

    project.registerIntegrationTestSuite("integrationTest") {}

    assertThat(project.tasks.findByName("integrationTest")).isNotNull()
  }

  /**
   * An integration suite never runs with `check`; Gradle adds only the default `test` suite, and the helper keeps
   * this one out even when a plugin wires it in.
   */
  @Test
  fun `the suite is removed from the check lifecycle`() {
    val project = jvmProject()

    project.registerIntegrationTestSuite("integrationTest") {}

    assertThat(dependencyNamesOf(project.tasks.named("check").get())).doesNotContain("integrationTest")
  }

  @Test
  fun `the regular test task stays wired into check`() {
    val project = jvmProject()

    project.registerIntegrationTestSuite("integrationTest") {}

    assertThat(dependencyNamesOf(project.tasks.named("check").get())).contains("test")
  }

  /**
   * Integration tests talk to external state, so a cached "up-to-date" result would report success
   * for a run that never happened.
   */
  @Test
  fun `the suite's test task is never up to date`() {
    val project = jvmProject()

    project.registerIntegrationTestSuite("integrationTest") {}

    val integrationTest = project.tasks.named("integrationTest", TestTask::class.java).get()

    assertThat(integrationTest.outputs.upToDateSpec.isSatisfiedBy(integrationTest)).isFalse()
  }

  @Test
  fun `a suite name without a Test or Tests suffix is rejected`() {
    val project = jvmProject()

    assertFailure { project.registerIntegrationTestSuite("pdfExamples") {} }.messageContains("Integration test suite <pdfExamples>")
  }

  @Test
  fun `a suite name ending in Tests is accepted`() {
    val project = jvmProject()

    project.registerIntegrationTestSuite("chromeTests") {}

    assertThat(project.tasks.findByName("chromeTests")).isNotNull()
  }

  /**
   * Resolves the declared dependencies to real tasks: the java plugin wires test suites into `check`
   * as suite providers, whose names are only visible once the dependency is resolved.
   */
  private fun dependencyNamesOf(task: Task): List<String> =
    task.taskDependencies.getDependencies(task).map { it.name }
}
