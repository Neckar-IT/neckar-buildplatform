package it.neckar.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * The JUnit Jupiter property that holds the timeout for test methods — `@Test`, `@ParameterizedTest`
 * and the other testable methods, but **not** `@BeforeEach` / `@BeforeAll` / `@AfterEach`.
 *
 * Set by [configureJunit] as a hang guard, removed again by [removeTimeoutGuards].
 */
const val JunitPerTestTimeoutProperty: String = "junit.jupiter.execution.timeout.testable.method.default"

/**
 * The JUnit Jupiter property that holds the timeout for lifecycle methods (`@BeforeEach`,
 * `@BeforeAll`, `@AfterEach`, `@AfterAll`).
 *
 * Separate from [JunitPerTestTimeoutProperty] because the two guard different things. A test method
 * that runs long is a hanging test. A lifecycle method that runs long is usually infrastructure
 * coming up — a Testcontainers container, and on a cold Docker cache that includes pulling its image.
 * That happens on the runner hosts every day: their maintenance cron runs
 * `docker image prune -a --force --filter 'until=24h'`, which removes tagged images no container is
 * using, and a test image is only in use while a test runs.
 *
 * Sharing one 120s budget made an image pull abort the first Testcontainers test of the day, and the
 * resulting `TimeoutException` pointed at the test rather than at the pull.
 */
const val JunitLifecycleTimeoutProperty: String = "junit.jupiter.execution.timeout.lifecycle.method.default"

/**
 * The JUnit Jupiter property that prints the stack of every thread on `System.out` before the
 * timeout interrupts the test. Gradle keeps that output in the JUnit XML of the test task.
 */
const val JunitThreadDumpOnTimeoutProperty: String = "junit.jupiter.execution.timeout.threaddump.enabled"

/** The JUnit Jupiter property that decides whether a debug session suspends the JUnit guards. */
const val JunitTimeoutModeProperty: String = "junit.jupiter.execution.timeout.mode"

/**
 * The kotlinx.coroutines property that holds the wall-clock budget of a `runTest` body, read once
 * per test JVM. A `runTest` call with an explicit `timeout` argument ignores it.
 */
const val CoroutinesTestTimeoutProperty: String = "kotlinx.coroutines.test.default_timeout"

/**
 * The budget of a test method, written in the grammar of `TimeoutDurationParser`: one integer and an
 * optional unit. JUnit logs any other spelling as invalid and falls back to
 * `junit.jupiter.execution.timeout.default`, which [configureJunit] leaves unset.
 */
const val JunitPerTestTimeout: String = "120s"

/** The budget of a lifecycle method, in the spelling [JunitPerTestTimeout] describes. */
const val JunitLifecycleTimeout: String = "600s"

/**
 * The budget of a `runTest` body, below [JunitPerTestTimeout] so that `runTest` reports a body that
 * did not run to completion or coroutines still active after it finished. Wider than the 60s of the
 * library, which a runner under load reaches with a test that takes seconds on an idle one.
 */
const val CoroutinesTestTimeout: String = "110s"

/** The value `Duration.parse` reads as `Duration.INFINITE`, which leaves `runTest` unbounded. */
const val CoroutinesTestTimeoutInfinite: String = "Infinity"

/** The JVM flag that lets code on the classpath call restricted methods such as `System::load` without a warning. */
const val EnableNativeAccessForClasspathFlag: String = "--enable-native-access=ALL-UNNAMED"

/**
 * Lets the test JVM call restricted methods from the classpath. The GraalVM Polyglot engine calls
 * `System::load`; without the grant, every test JVM that loads it prints a restricted-method warning.
 */
fun Test.allowNativeAccessFromClasspath() {
  jvmArgs(EnableNativeAccessForClasspathFlag)
}

/**
 * Takes every timeout budget off a [Test] task.
 *
 * Separate test suites are the designated place for slow work — browsers, external services,
 * performance measurements — and legitimately run longer than the guards. Aborting them is
 * always a false positive, so the suites opt out instead of raising the guard for everyone.
 */
fun Test.removeTimeoutGuards() {
  require(systemProperties.containsKey(JunitPerTestTimeoutProperty)) {
    "$JunitPerTestTimeoutProperty is unset on $path. Call removeTimeoutGuards() once, from a configure block that runs after configureJunit(), which writes $CoroutinesTestTimeout into $CoroutinesTestTimeoutProperty."
  }

  systemProperties.remove(JunitPerTestTimeoutProperty)
  systemProperties.remove(JunitLifecycleTimeoutProperty)
  systemProperty(CoroutinesTestTimeoutProperty, CoroutinesTestTimeoutInfinite)
}

/** The JUnit tag of `@SlowTest` in `internal/open/commons/test-utils`. */
const val SlowTestTag: String = "slow-test"

/** The [Test] task that runs only the tests tagged [SlowTestTag], registered by [configureJunit]. */
const val SlowTestTaskName: String = "slowTest"

/** The property (`-PskipSlowTests`) that drops [SlowTestTaskName] from `check`. */
const val SkipSlowTestsProperty: String = "skipSlowTests"

/**
 * Configures JUnit and registers [SlowTestTaskName] next to `test` or `jvmTest`.
 *
 * `test` and `jvmTest` always exclude [SlowTestTag], so their cache key is the same with and without
 * `-PskipSlowTests`. The property only drops [SlowTestTaskName] from `check`.
 */
fun Project.configureJunit(skipPropertyLookup: SkipPropertyLookup = SkipPropertyLookup.GradlePropertyOrEnvironment) {
  val skipSlowTests = isSkipRequested(SkipSlowTestsProperty, "check leaves out $SlowTestTaskName across all modules", skipPropertyLookup)

  tasks.withType<Test>().configureEach {
    useJUnitPlatform {
      includeEngines(
        "junit-jupiter", //JUnit 5 engine
      )
    }

    //Gradle's 512 MB default is too small for OpenAPI tests; 1 GB is what the runner budget in gitlab-ci.d/docs/memory-settings.md holds.
    maxHeapSize = "1g"

    //Enable HTML reports
    reports.html.required.set(true)

    filter {
      includeTestsMatching("*Test")
      includeTestsMatching("*Tests")
      includeTestsMatching("*IT")
      isFailOnNoMatchingTests = false //if there are no tests defined in a project, do *not* fail
    }

    //Show the stack traces of the failing tests on the console
    testLogging {
      events.add(org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED)
      exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
      showExceptions = true
      showCauses = true
      showStackTraces = true
      showStandardStreams = false //do not show standard streams (stdout/stderr) by default
    }

    //Per-test-method timeout: a safety net against a single hanging test blocking the whole
    //build (a runaway test used to stall the main pipeline for tens of minutes). 120s is far
    //above any legitimate unit/functional test — anything slower belongs in a separate
    //test suite, which opts out via `removeTimeoutGuards()`.
    //`disabled_on_debug` suspends the JUnit guards in an interactive debug session.
    systemProperty(JunitPerTestTimeoutProperty, JunitPerTestTimeout)

    //Lifecycle methods (@BeforeEach/@BeforeAll/@AfterEach/@AfterAll) get their own, larger budget:
    //they are where infrastructure starts, and a Testcontainers container on a cold Docker cache has
    //to pull its image first. The runner hosts prune unused tagged images daily, so that cold start
    //is the normal case for the first container test after 06:00 — a pull that the 120s test budget
    //aborted, reporting a TimeoutException on the test instead of the pull.
    //Still bounded: a lifecycle method that hangs indefinitely must not stall the build either.
    systemProperty(JunitLifecycleTimeoutProperty, JunitLifecycleTimeout)
    systemProperty(JunitTimeoutModeProperty, "disabled_on_debug")

    systemProperty(CoroutinesTestTimeoutProperty, CoroutinesTestTimeout)
    systemProperty(JunitThreadDumpOnTimeoutProperty, "true")

    //Set the JVM properties for the tests
    //Set Coroutines Debugging - see https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-d-e-b-u-g_-p-r-o-p-e-r-t-y_-n-a-m-e.html
    systemProperty("kotlinx.coroutines.debug", "on")
    jvmArgs("-Dkotlinx.coroutines.debug=on")

    allowNativeAccessFromClasspath()
    redirectJvmCrashReportsToBuildDirectory()

    //Testcontainers: Disable Ryuk resource reaper.
    //With the Singleton Container pattern, containers are shared across test classes
    //and cleaned up at JVM shutdown. Ryuk is not needed and can cause issues in CI.
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
    environment("TESTCONTAINERS_RYUK_VERBOSE", "true")
  }

  withUnitTestTask { unitTest ->
    registerSlowTest(unitTest, skipSlowTests)
  }
}

/**
 * Moves the tests tagged [SlowTestTag] from [unitTest] into [SlowTestTaskName] on the same test classes.
 * [SlowTestTaskName] inherits the hang guards and every other setting [configureJunit] gives each [Test] task.
 */
private fun Project.registerSlowTest(unitTest: TaskProvider<Test>, skipSlowTests: Boolean) {
  unitTest.configure {
    useJUnitPlatform {
      excludeTags(SlowTestTag)
    }
  }

  val slowTest = tasks.register<Test>(SlowTestTaskName) {
    description = "Runs the tests tagged @SlowTest, which ${unitTest.name} leaves out"
    group = VerificationGroup
    useTestClassesOf(unitTest)
    useJUnitPlatform {
      includeTags(SlowTestTag)
    }
    shouldRunAfter(unitTest)
  }

  if (skipSlowTests.not()) {
    tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) {
      dependsOn(slowTest)
    }
  }
}

/**
 * Answers whether a skip property such as `-PskipSlowTests` is set for a project.
 * [GradlePropertyOrEnvironment] serves a build outside the monorepo; the monorepo also reads its `.env` file.
 */
fun interface SkipPropertyLookup {
  fun isSet(project: Project, property: String): Boolean

  companion object {
    /** Set as Gradle property (`-PskipSlowTests`, `gradle.properties`, extra property) or as environment variable. */
    val GradlePropertyOrEnvironment: SkipPropertyLookup = SkipPropertyLookup { project, property ->
      project.findProperty(property) != null || System.getenv(property) != null
    }
  }
}

/**
 * Logs [message] once per Gradle invocation when the skip [property] is set, and returns whether it is set.
 *
 * Called once per project and property; a `rootProject.extra` flag per property keeps the message to one per build.
 */
internal fun Project.isSkipRequested(property: String, message: String, skipPropertyLookup: SkipPropertyLookup): Boolean {
  if (skipPropertyLookup.isSet(this, property).not()) return false

  val key = "${property}Logged"
  if (rootProject.extra.has(key).not()) {
    rootProject.extra[key] = true
    rootProject.logger.lifecycle("$message (remove -P$property to include it)")
  }
  return true
}
