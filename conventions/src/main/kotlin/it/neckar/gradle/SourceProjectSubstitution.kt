package it.neckar.gradle

import it.neckar.projects.common.MavenCoordinate
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencySubstitutions
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector

/**
 * Resolves each `it.neckar.*` coordinate to the project of this build with that `group` ([mavenGroupOf]) and `name`: the coordinates
 * of a standalone-capable build script and the Detekt rules and KSP processors the convention plugins add then build from the sources.
 * A third-party coordinate and one of an included build stay; an `it.neckar.*` coordinate no project produces fails the resolution.
 * [isSubstituted] narrows the groups: the export of MeisterCharts substitutes its own group and resolves `it.neckar.open` from Maven Central.
 */
fun Project.substituteNeckarCoordinatesWithSourceProjects(isSubstituted: (group: String) -> Boolean = ::isProducedByRootBuild) {
  val consumerPath: String = path

  configurations.all {
    resolutionStrategy.dependencySubstitution {
      val substitutions: DependencySubstitutions = this
      all {
        val selector: ComponentSelector = requested
        if (selector !is ModuleComponentSelector || isSubstituted(selector.group).not()) {
          return@all
        }

        // A convention plugin names its coordinates as text; one no project produces would compile against a published artifact.
        val producer: Project = projectProducing(MavenCoordinate(selector.group, selector.module))
          ?: throw GradleException("$consumerPath resolves ${selector.group}:${selector.module}, which no project of this build produces")
        if (producer.path == consumerPath) {
          return@all
        }

        // A plain project target drops the requested capabilities, and testFixtures(…) would resolve the main variant.
        val target: ComponentSelector = substitutions.variant(substitutions.project(producer.path)) {
          capabilities {
            selector.requestedCapabilities.forEach { capability -> requireCapability("${capability.group}:${capability.name}") }
          }
        }

        useTarget(target, "the sources of this build instead of the published artifact")
      }
    }
  }
}
