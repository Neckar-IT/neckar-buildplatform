package it.neckar.gradle.ksp

import it.neckar.gradle.ksp.options.KspPluginOptions
import org.gradle.api.Project

/**
 * Extension methods for [it.neckar.gradle.ksp.options.KspPluginOptions].
 *
 * Do *NOT* move this class to the same package: The content of the `options` package is used in the KSP processors and must not contain any dependencies to the Gradle API.
 */

fun KspPluginOptions.Common.create(project: Project): List<String> {
  return listOf(
    ProjectPath.withValue(project.path),
    ProjectDir.withValue(project.projectDir.absolutePath),
  )
}
