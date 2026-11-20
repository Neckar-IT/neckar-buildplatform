package it.neckar.gradle

import it.neckar.projects.common.MavenCoordinate
import org.gradle.api.GradleException
import org.gradle.api.Project

/** The property naming the open release whose `it.neckar.open` artifacts the conventions add, e.g. `10.1.0`. */
const val NeckarOpenVersionProperty: String = "neckarOpenVersion"

/**
 * The version of the property [NeckarOpenVersionProperty], read as Gradle property or as extra property of this project or a parent.
 * The root build of the monorepo sets the extra property; a build outside the monorepo sets it in `gradle.properties`.
 */
fun Project.neckarOpenVersion(): String {
  return findProperty(NeckarOpenVersionProperty)?.toString()
    ?: throw GradleException("$path needs the property $NeckarOpenVersionProperty, the version of the it.neckar.open artifacts, e.g. $NeckarOpenVersionProperty=10.1.0 in gradle.properties")
}

/** The dependency notation of [coordinate] in [neckarOpenVersion], e.g. `it.neckar.open.ksp:spec-harvest:10.1.0`. */
fun Project.neckarOpenNotation(coordinate: MavenCoordinate): String {
  return coordinate.withDefaultVersion(neckarOpenVersion()).toString()
}
