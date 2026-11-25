package it.neckar.gradle.publishing

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Checks every POM of [repository] against [MavenCentralPublicationRules] and fails with every violation in one message.
 */
@DisableCachingByDefault(because = "Reads a repository the publish tasks of this build rewrite on every run")
abstract class VerifyMavenCentralPublishingTask : DefaultTask() {
  /** The Maven repository the publish tasks wrote; no input, so a missing directory reaches the rule that asks for a POM. */
  @get:Internal
  abstract val repository: DirectoryProperty

  @TaskAction
  fun verify() {
    val report: MavenCentralPublicationReport = MavenCentralPublicationRules.check(repository.get().asFile)
    if (report.violations.isNotEmpty()) {
      throw GradleException(
        "Maven Central rejects the deployment of ${report.poms.size} POMs:\n" +
          report.violations.joinToString("\n") { "  $it" }
      )
    }
    logger.lifecycle("${report.poms.size} POMs meet the rules of Maven Central:")
    report.poms.forEach { logger.lifecycle("  $it") }
  }
}
