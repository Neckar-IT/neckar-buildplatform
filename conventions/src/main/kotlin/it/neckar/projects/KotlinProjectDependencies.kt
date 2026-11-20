package it.neckar.projects

import it.neckar.gradle.NeckarOpenVersionProperty
import it.neckar.gradle.mavenGroupOf
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Dependency
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler

/**
 * The dependency on [configuredProject] inside `kotlin { sourceSets { … { dependencies { } } } }`:
 * - the project dependency, when the build holds the project
 * - else the coordinate [releasedCoordinateOf] in the Gradle property [NeckarOpenVersionProperty]: the export of
 *   `internal/open/meistercharts` holds no project of `internal/open` outside MeisterCharts
 *
 * Fails with the path when the build holds neither the project nor the property.
 */
fun KotlinDependencyHandler.project(configuredProject: ConfiguredProject): Dependency {
  val path: GradleProjectPath = configuredProject.path
  if (project.findProject(path.path) != null) {
    return project(path.path)
  }
  val neckarOpenVersion: String? = project.providers.gradleProperty(NeckarOpenVersionProperty).orNull
  return project.dependencies.create(releasedCoordinateOf(path, neckarOpenVersion))
}

/**
 * The Maven coordinate the project at [path] is released under, in [neckarOpenVersion]: `:internal:open:annotations` and `10.1.0` are
 * `it.neckar.open:annotations:10.1.0`. The group is [mavenGroupOf] the monorepo path, the artifactId the last path segment.
 */
internal fun releasedCoordinateOf(path: GradleProjectPath, neckarOpenVersion: String?): String {
  if (neckarOpenVersion == null) {
    throw GradleException(
      "The build holds no project $path and sets no Gradle property $NeckarOpenVersionProperty for its release: " +
        "include the project in settings.gradle.kts, or set e.g. $NeckarOpenVersionProperty=10.1.0 in gradle.properties"
    )
  }
  return "${mavenGroupOf(emptyList(), path.path)}:${path.path.substringAfterLast(':')}:$neckarOpenVersion"
}
