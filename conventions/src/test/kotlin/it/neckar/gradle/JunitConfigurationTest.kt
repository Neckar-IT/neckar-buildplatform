package it.neckar.gradle

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsOnly
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isNull
import assertk.assertions.matches
import assertk.assertions.messageContains
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test as TestTask
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions
import org.gradle.kotlin.dsl.apply
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.time.Duration

class JunitConfigurationTest {
  @TempDir
  lateinit var projectDir: File

  /**
   * A `runTest` body that answers the cancellation fails after [CoroutinesTestTimeout] and JUnit
   * never interrupts it. An equal or larger coroutines budget hands every coroutine test to the
   * JUnit guard instead.
   */
  @Test
  fun `the coroutines budget stays below the junit budget`() {
    assertThat(Duration.parse(CoroutinesTestTimeout)).isLessThan(Duration.parse(JunitPerTestTimeout))
  }

  /**
   * JUnit reads both budgets with `TimeoutDurationParser`. `"2m 30s"` is a duration for Kotlin and
   * invalid for JUnit, which then runs the method without a guard.
   */
  @Test
  fun `the junit budgets are written in the junit grammar`() {
    assertThat(JunitPerTestTimeout).matches(JunitTimeoutGrammar)
    assertThat(JunitLifecycleTimeout).matches(JunitTimeoutGrammar)
  }

  @Test
  fun `the unbounded coroutines budget parses as an infinite duration`() {
    assertThat(Duration.parse(CoroutinesTestTimeoutInfinite)).isEqualTo(Duration.INFINITE)
  }

  @Test
  fun `configureJunit sets the hang guards of a test task`() {
    val testTask = configuredProject().tasks.named("test", TestTask::class.java).get()

    assertHangGuards(testTask)
  }

  @Test
  fun `configureJunit sends the crash report of a test JVM into its build directory`() {
    val testTask = configuredProject().tasks.named("test", TestTask::class.java).get()

    assertThat(testTask.allJvmArgs).contains("-XX:ErrorFile=build/jvm-crashes/test/hs_err_pid%p.log")
  }

  @Test
  fun `configureJunit allows native access from the classpath`() {
    val testTask = configuredProject().tasks.named("test", TestTask::class.java).get()

    assertThat(testTask.allJvmArgs).contains(EnableNativeAccessForClasspathFlag)
  }

  /**
   * `OpenApiGenerationConfigPlugin` registers `openapiSpec` after `configureJunit` has run, so the
   * configuration reaches a [TestTask] that does not exist yet at that point.
   */
  @Test
  fun `configureJunit sets the hang guards of a later registered test task`() {
    val project = configuredProject()

    val testTask = project.tasks.register("openapiSpec", TestTask::class.java).get()

    assertHangGuards(testTask)
  }

  /**
   * Leaving the coroutines budget at [CoroutinesTestTimeout] would hand the opted-out suites a
   * shorter budget than the guards they opted out of.
   */
  @Test
  fun `removeTimeoutGuards leaves the coroutines budget unbounded`() {
    val testTask = configuredProject().tasks.named("test", TestTask::class.java).get()

    testTask.removeTimeoutGuards()

    assertThat(testTask.systemProperties[JunitPerTestTimeoutProperty]).isNull()
    assertThat(testTask.systemProperties[JunitLifecycleTimeoutProperty]).isNull()
    assertThat(testTask.systemProperties[CoroutinesTestTimeoutProperty]).isEqualTo(CoroutinesTestTimeoutInfinite)
  }

  /**
   * Called before `configureJunit`, the removal would hit budgets that are not there yet, and
   * `configureJunit` would write [CoroutinesTestTimeout] afterwards.
   */
  @Test
  fun `removeTimeoutGuards before configureJunit fails`() {
    val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
    project.apply(plugin = BuildplatformPlugins.Java)

    val testTask = project.tasks.named("test", TestTask::class.java).get()

    assertFailure { testTask.removeTimeoutGuards() }.messageContains("configureJunit()")
  }

  /** The tag filter is a task input; if it followed `-PskipSlowTests`, it would split the cache key between merge request pipeline and main. */
  @Test
  fun `test excludes the slow tests with and without skipSlowTests`() {
    for (skipSlowTests in listOf(false, true)) {
      val testTask = configuredProject(skipSlowTests, projectDir.resolve("skipSlowTests-$skipSlowTests")).tasks.named("test", TestTask::class.java).get()

      assertThat(testTask.junitPlatformOptions().excludeTags).containsOnly(SlowTestTag)
      assertThat(testTask.junitPlatformOptions().includeTags).isEmpty()
    }
  }

  @Test
  fun `slowTest runs only the slow tests on the classes of test`() {
    val project = configuredProject()
    val testTask = project.tasks.named("test", TestTask::class.java).get()

    val slowTest = project.tasks.named(SlowTestTaskName, TestTask::class.java).get()

    assertThat(slowTest.junitPlatformOptions().includeTags).containsOnly(SlowTestTag)
    assertThat(slowTest.junitPlatformOptions().excludeTags).isEmpty()
    assertThat(slowTest.junitPlatformOptions().includeEngines).containsOnly("junit-jupiter")
    assertThat(slowTest.testClassesDirs.files).isEqualTo(testTask.testClassesDirs.files)
    assertThat(slowTest.classpath.files).isEqualTo(testTask.classpath.files)
    assertThat(slowTest.shouldRunAfter.getDependencies(slowTest)).contains(testTask)
    assertHangGuards(slowTest)
  }

  @Test
  fun `check runs slowTest without skipSlowTests`() {
    val project = configuredProject()

    assertThat(project.tasks.getByName("check").taskDependencies.getDependencies(null))
      .contains(project.tasks.getByName(SlowTestTaskName))
  }

  @Test
  fun `check leaves out slowTest with skipSlowTests`() {
    val project = configuredProject(skipSlowTests = true)

    assertThat(project.tasks.getByName("check").taskDependencies.getDependencies(null))
      .doesNotContain(project.tasks.getByName(SlowTestTaskName))
  }

  @Test
  fun `a multiplatform project runs its slow tests from the jvmTest classes`() {
    val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
    project.apply(plugin = BuildplatformPlugins.KotlinMultiPlatform)
    project.extensions.getByType(KotlinMultiplatformExtension::class.java).jvm()
    project.configureJunit()
    val jvmTest = project.tasks.named("jvmTest", TestTask::class.java).get()

    val slowTest = project.tasks.named(SlowTestTaskName, TestTask::class.java).get()

    assertThat(jvmTest.junitPlatformOptions().excludeTags).containsOnly(SlowTestTag)
    assertThat(slowTest.junitPlatformOptions().includeTags).containsOnly(SlowTestTag)
    assertThat(slowTest.junitPlatformOptions().excludeTags).isEmpty()
    assertThat(slowTest.testClassesDirs.files).isEqualTo(jvmTest.testClassesDirs.files)
    assertThat(project.tasks.getByName("check").taskDependencies.getDependencies(null)).contains(slowTest)
    assertThat(slowTest.shouldRunAfter.getDependencies(slowTest)).contains(jvmTest)
    assertHangGuards(slowTest)
  }

  @Test
  fun `a jvm target declared after configureJunit gets slowTest`() {
    val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
    project.apply(plugin = BuildplatformPlugins.KotlinMultiPlatform)
    project.configureJunit()

    project.extensions.getByType(KotlinMultiplatformExtension::class.java).jvm()

    val jvmTest = project.tasks.named("jvmTest", TestTask::class.java).get()
    val slowTest = project.tasks.named(SlowTestTaskName, TestTask::class.java).get()
    assertThat(jvmTest.junitPlatformOptions().excludeTags).containsOnly(SlowTestTag)
    assertThat(slowTest.testClassesDirs.files).isEqualTo(jvmTest.testClassesDirs.files)
  }

  private fun TestTask.junitPlatformOptions(): JUnitPlatformOptions = options as JUnitPlatformOptions

  private fun assertHangGuards(testTask: TestTask) {
    assertThat(testTask.systemProperties[JunitPerTestTimeoutProperty]).isEqualTo(JunitPerTestTimeout)
    assertThat(testTask.systemProperties[JunitLifecycleTimeoutProperty]).isEqualTo(JunitLifecycleTimeout)
    assertThat(testTask.systemProperties[JunitThreadDumpOnTimeoutProperty]).isEqualTo("true")
    assertThat(testTask.systemProperties[JunitTimeoutModeProperty]).isEqualTo("disabled_on_debug")
    assertThat(testTask.systemProperties[CoroutinesTestTimeoutProperty]).isEqualTo(CoroutinesTestTimeout)
  }

  private fun configuredProject(skipSlowTests: Boolean = false, directory: File = projectDir): Project {
    val project = ProjectBuilder.builder().withProjectDir(directory).build()
    if (skipSlowTests) {
      project.extensions.extraProperties[SkipSlowTestsProperty] = ""
    }
    project.apply(plugin = BuildplatformPlugins.Java)
    project.configureJunit()

    return project
  }

  companion object {
    /** The pattern of `org.junit.jupiter.engine.extension.TimeoutDurationParser` in JUnit 6.1.3. */
    private val JunitTimeoutGrammar = Regex("""[1-9]\d* ?(?:[nμm]?s|m|h|d)?""", RegexOption.IGNORE_CASE)
  }
}
