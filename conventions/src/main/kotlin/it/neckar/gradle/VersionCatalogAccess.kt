package it.neckar.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider

/**
 * The version catalog `libs` of the build, for Kotlin source of a Gradle plugin.
 *
 * In `.gradle.kts` scripts, use the `libs.*` accessors. Plugin source uses `versionCatalog.library("alias")`:
 * the generated `LibrariesForLibs` of the consuming build is a different class than one compiled into the plugin.
 *
 * IMPORTANT: Do not name this `libs` — it would shadow the generated
 * type-safe accessors in `.gradle.kts` scripts.
 */
val Project.versionCatalog: VersionCatalog
  get() = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

/** The library [alias] of this catalog; Gradle reads dashes and dots in an alias alike. */
fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> {
  return findLibrary(alias).orElseThrow {
    IllegalArgumentException("Library alias '$alias' not found in version catalog $name")
  }
}
