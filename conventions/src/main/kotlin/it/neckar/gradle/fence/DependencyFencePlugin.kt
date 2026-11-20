package it.neckar.gradle.fence

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create

/**
 * Forbids dependencies in selected configurations. Each `forbid { }` block is one rule with its
 * own scope, forbidden lists, and `reason`. Multiple blocks per module stack independently.
 * Violations fail the configuration phase with a grouped, reason-tagged message.
 *
 * ```
 * dependencyFence {
 *   forbid {
 *     reason = "Migration modules must not see current business / REST classes."
 *     forbidProjectPaths(":business:rest")
 *   }
 * }
 * ```
 */
class DependencyFencePlugin : Plugin<Project> {
  override fun apply(target: Project) {
    val extension = target.extensions.create<DependencyFenceExtension>(ExtensionName, target)

    target.afterEvaluate {
      extension.excludeFromCompileClasspaths()
      val violations: List<Violation> = extension.findViolations()
      if (violations.isEmpty()) return@afterEvaluate

      throw GradleException(renderViolations(target, violations))
    }
  }

  companion object {
    const val ExtensionName: String = "dependencyFence"

    internal fun renderViolations(project: Project, violations: List<Violation>): String = buildString {
      appendLine("dependencyFence violation in ${project.path}")
      violations.groupBy { it.reason }.forEach { (reason, group) ->
        appendLine()
        group.forEach { violation ->
          appendLine("  ${violation.configurationName}(${violation.dependencyDisplay})")
          if (violation.sourceSet != null) {
            appendLine("    source set: ${violation.sourceSet}")
          }
          appendLine("    forbidden by ${violation.matchedBy}")
        }
        appendLine()
        appendLine("  reason: ${reason.lineSequence().joinToString(separator = "\n          ")}")
      }
    }
  }
}

internal data class Violation(
  val configurationName: String,
  val dependencyDisplay: String,
  val matchedBy: String,
  val reason: String,
  val sourceSet: String?,
)
