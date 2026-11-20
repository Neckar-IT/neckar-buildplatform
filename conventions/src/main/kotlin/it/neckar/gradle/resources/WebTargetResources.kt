@file:OptIn(ComposeKotlinGradlePluginApi::class)

package it.neckar.gradle.resources

import it.neckar.gradle.KotlinTargetResourcesExtensionName
import it.neckar.gradle.MainCompilationName
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.ComposeKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.resources.KotlinTargetResourcesPublication
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import java.io.File

/**
 * Publishes the resources of the project's web targets (JS, Wasm) as a Kotlin component, so a
 * consumer resolves them through the dependency graph.
 *
 * A browser bundle has no classpath to load `elevator/bird.png` from a dependency at runtime, so the
 * resources of every dependency have to end up in the consumer's own resource directory. The Kotlin
 * Gradle Plugin carries the machinery for that — a resources variant on the producer,
 * [KotlinTargetResourcesPublication.resolveResources] on the consumer — but calls it only for Compose
 * Multiplatform. Called from the central multiplatform configuration, this covers every module.
 */
internal fun Project.publishResourcesOfWebTargets() {
  //The extension is registered by a Kotlin project setup action, which has not run while the build
  //script is being evaluated
  afterEvaluate {
    webTargets()
      //A target without resources declares an archive that no task produces, and
      //generating the publication's Gradle metadata then fails on the missing file
      .filter { webTarget -> hasResources(webTarget) }
      .forEach { webTarget -> publishResourcesOf(webTarget) }
  }
}

private fun Project.publishResourcesOf(webTarget: KotlinTarget) {
  resourcesPublication().publishResourcesAsKotlinComponent(
    webTarget,
    { sourceSet ->
      KotlinTargetResourcesPublication.ResourceRoot(
        provider { file("src/${sourceSet.name}/resources") },
        emptyList(),
        emptyList(),
      )
    },
    //The resources keep the paths they have in the source set - a bundle resolves `elevator/bird.png`
    provider { File("") },
  )
}

/** The resources of the web target and of every dependency that publishes them. */
internal fun Project.resolveResourcesOfWebTarget(webTarget: KotlinTarget): Provider<File> {
  return resourcesPublication().resolveResources(webTarget)
}

/**
 * The JS and Wasm targets — both are a [KotlinJsIrTarget] and the only ones the Kotlin Gradle Plugin
 * can resolve resources for.
 */
internal fun Project.webTargets(): List<KotlinTarget> {
  return extensions.getByType(KotlinMultiplatformExtension::class.java).targets.filterIsInstance<KotlinJsIrTarget>()
}

/** The resource directories of the source sets of the target's main compilation. */
internal fun Project.resourceDirectoriesOf(webTarget: KotlinTarget): List<File> {
  return webTarget.compilations.getByName(MainCompilationName).allKotlinSourceSets.map { sourceSet ->
    file("src/${sourceSet.name}/resources")
  }
}

/** The paths of the files below [directory], relative to it. */
internal fun relativeFilePathsBelow(directory: File): List<String> {
  return directory.walkTopDown()
    .filter { candidate -> candidate.isFile }
    .map { resourceFile -> resourceFile.relativeTo(directory).invariantSeparatorsPath }
    .toList()
}

/**
 * The Kotlin Gradle Plugin registers its resources publication in the project's extra properties, and
 * only when `kotlin.mpp.enableResourcesPublication` keeps its default `true`.
 */
private fun Project.resourcesPublication(): KotlinTargetResourcesPublication {
  val extraProperties = extensions.extraProperties
  check(extraProperties.has(KotlinTargetResourcesExtensionName)) {
    "[$path] needs the Kotlin Gradle Plugin's resources publication to give its web targets the resources of their " +
      "dependencies, but `kotlin.mpp.enableResourcesPublication` is `false`. Without it a browser bundle silently " +
      "ships without those files."
  }

  return extraProperties.get(KotlinTargetResourcesExtensionName) as KotlinTargetResourcesPublication
}

/** Whether a source set of the target's main compilation carries a resource. */
private fun Project.hasResources(webTarget: KotlinTarget): Boolean {
  return resourceDirectoriesOf(webTarget).any { resourcesDirectory ->
    relativeFilePathsBelow(resourcesDirectory).isNotEmpty()
  }
}
