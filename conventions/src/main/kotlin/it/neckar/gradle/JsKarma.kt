package it.neckar.gradle


/**
 * Configures JS test runner using karma.
 * Note: When ES modules are used (via useEsModules()), Karma tests are disabled
 * because Karma doesn't support ES module syntax.
 */
fun org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsBrowserDsl.configureJsKarma(disableTests: Boolean = false, noSandbox: Boolean = runsInCi()) {
  testTask {
    if (disableTests) {
      enabled = false
    } else {
      configureJsKarma(noSandbox)
    }
  }
}

/**
 * Configures JS with karma; [noSandbox] starts Chrome without its sandbox, which a CI runner or a container lacks.
 */
fun org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest.configureJsKarma(noSandbox: Boolean = runsInCi()) {
  useKarma {
    if (noSandbox) {
      useChromeHeadlessNoSandbox()
    } else {
      useChromeHeadless()
    }
  }
}

/** True when the environment variable `CI` is set, as every common CI service does. */
private fun runsInCi(): Boolean = System.getenv("CI") != null
