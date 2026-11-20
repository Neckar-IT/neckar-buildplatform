package it.neckar.gradle.fence

import org.gradle.api.Project

/**
 * DSL container for [DependencyFencePlugin]. Holds the [ForbidRule] entries collected at apply time.
 *
 * `open class` (not `interface`): an imperative rule collector, not a managed-property bag — it needs
 * the [project] to resolve configurations and a mutable [rules] list. `open` so Gradle can decorate it.
 */
open class DependencyFenceExtension(private val project: Project) {
  private val rules: MutableList<ForbidRule> = mutableListOf()

  fun forbid(configure: ForbidRule.() -> Unit) {
    val rule = ForbidRule(project).apply(configure)
    rule.validate()
    rules.add(rule)
  }

  internal fun excludeFromCompileClasspaths(): Unit = rules.forEach { it.excludeFromCompileClasspaths() }

  internal fun findViolations(): List<Violation> = rules.flatMap { it.findViolations() }
}
