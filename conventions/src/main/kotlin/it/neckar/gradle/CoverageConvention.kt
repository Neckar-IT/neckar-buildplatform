package it.neckar.gradle

import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import kotlinx.kover.gradle.plugin.dsl.tasks.KoverReport
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

/** Gradle property that attaches the Kover agent to the test JVMs: `-Pcoverage=true`. */
const val CoverageGradleProperty: String = "coverage"

/**
 * Attaches the Kover agent, which doubles the cold start of a test JVM, only with `-Pcoverage=true`;
 * a Kover report without the property fails instead of writing an empty report.
 */
fun Project.configureCoverage() {
  val coverageEnabled = coverageEnabled()
  extensions.getByType<KoverProjectExtension>().run {
    detachCoverageFromCheck()
    currentProject.instrumentation.disabledForAll.set(coverageEnabled.map { it.not() })
  }
  tasks.withType<KoverReport>().configureEach {
    doFirst {
      check(coverageEnabled.get()) { "$path reads coverage data, which only a run with -P$CoverageGradleProperty=true collects" }
    }
  }
}

/**
 * In `check` a Kover report would fail every `build` without `-Pcoverage=true`, and with it run every
 * instrumented test task of the subprojects, `slowTest` included.
 */
private fun KoverProjectExtension.detachCoverageFromCheck() {
  reports {
    total {
      html { onCheck.set(false) }
      xml { onCheck.set(false) }
      binary { onCheck.set(false) }
      verify { onCheck.set(false) }
      log { onCheck.set(false) }
    }
  }
}

/** Whether the build runs with [CoverageGradleProperty]; any value except `false` enables it. */
fun Project.coverageEnabled(): Provider<Boolean> = providers.gradleProperty(CoverageGradleProperty).map { it != "false" }.orElse(false)
