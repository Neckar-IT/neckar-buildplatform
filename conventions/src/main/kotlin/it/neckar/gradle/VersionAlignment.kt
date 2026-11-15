package it.neckar.gradle

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin

/**
 * The BOMs, as aliases of the version catalog, that align the libraries of every JVM project of neckar-hub: a transitive
 * dependency resolves the version the BOM names, the same in the root build and in a build of its own.
 */
private val VersionAlignmentPlatformAliases: List<String> = listOf(
  "jackson-bom",
  "junit-bom",
  "kotlinx-coroutines-bom",
  "kotlinx-serialization-bom",
  "ktor-bom",
  "mongodb-driver-bom",
  "netty-bom",
  "opentelemetry-bom",
  // The stable SDK links against opentelemetry-api-incubator; without this the instrumentation BOM pins it behind.
  "opentelemetry-alpha-bom",
  "opentelemetry-instrumentation-bom-alpha",
  "testcontainers-bom",
)

/**
 * Adds every BOM of [VersionAlignmentPlatformAliases] to `implementation` once the project applies `java`. The catalog is the
 * root project's: a project applying `java` from a parent's `allprojects` block has no catalog extension of its own yet.
 */
fun Project.alignVersionsThroughPlatforms() {
  plugins.withType(JavaPlugin::class.java) {
    VersionAlignmentPlatformAliases.forEach { alias ->
      dependencies.add(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, dependencies.platform(rootProject.versionCatalog.library(alias)))
    }
  }
}
