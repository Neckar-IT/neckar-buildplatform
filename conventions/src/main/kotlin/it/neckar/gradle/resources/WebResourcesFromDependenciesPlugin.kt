package it.neckar.gradle.resources

import it.neckar.gradle.CheckTaskName
import it.neckar.gradle.VerificationGroup
import it.neckar.gradle.hasKotlinMultiplatformPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.named
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import java.io.File

/**
 * Adds the resources of every dependency to the resources of this project's web targets (JS, Wasm).
 *
 * The web bundlers need every resource in one directory: a browser bundle has no classpath to
 * resolve `elevator/bird.png` from a dependency at runtime.
 *
 * The resources come out of the dependency graph, resolved by the Kotlin Gradle Plugin's own
 * resources publication — the producing side of which every multiplatform module gets from
 * [publishResourcesOfWebTargets]. The alternative, collecting the resource directories of the
 * dependency *projects*, only ever saw project dependencies and needed a copy task whose output
 * directory belonged to `processResources`; files it left behind survived every build, because
 * Gradle deletes nothing in a directory another task owns.
 *
 * Opt-in rather than applied to every module: a klib packs whatever is in the target's resources, so
 * a module that pulls in its dependencies' resources without building a bundle only bloats its own
 * artifact.
 *
 * Deliberately nothing for JVM targets: a JVM runtime classpath already carries every dependency's
 * resources, so copying them into the consumer's resources only creates a second copy of every file,
 * and in a fat jar both copies land on the same path.
 */
class WebResourcesFromDependenciesPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    if (target.hasKotlinMultiplatformPlugin().not()) {
      throw IllegalStateException(
        "[${target.path}] applies webResourcesFromDependencies but has no Kotlin Multiplatform plugin. " +
          "The plugin only serves the web targets (JS, Wasm) - for JVM-only projects the runtime classpath " +
          "already provides every dependency's resources, so there is nothing to collect. Remove the plugin."
      )
    }

    //Both the targets and the Kotlin resources extension exist only after the build script has run
    target.afterEvaluate {
      webTargets().forEach { webTarget ->
        val resolvedResources: Provider<File> = resolveResourcesOfWebTarget(webTarget)

        addToProcessResources(webTarget, resolvedResources)
        registerArrivalCheck(webTarget, resolvedResources)
      }
    }
  }
}

private fun Project.addToProcessResources(webTarget: KotlinTarget, resolvedResources: Provider<File>) {
  tasks.named<Copy>("${webTarget.name}ProcessResources") {
    //The target's own resources are declared first, so with EXCLUDE they win over a dependency's
    //copy of the same path
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(resolvedResources)
  }
}

/**
 * A collection that silently resolves nothing keeps the build green and shows up as a 404 in the
 * browser, so `check` asserts that something arrived.
 */
private fun Project.registerArrivalCheck(webTarget: KotlinTarget, resolvedResources: Provider<File>) {
  val verifyResourcesFromDependencies = tasks.register(
    "verify${webTarget.name.replaceFirstChar { firstCharacter -> firstCharacter.uppercase() }}ResourcesFromDependencies",
    VerifyResourcesFromDependenciesTask::class.java,
  ) {
    group = VerificationGroup
    description = "Verifies that the ${webTarget.name} resources of the dependencies reached this project"

    resolvedResourcesDirectory.set(layout.dir(resolvedResources))
    ownResourceDirectories.from(resourceDirectoriesOf(webTarget))
    projectPath.set(this@registerArrivalCheck.path)
  }

  tasks.named(CheckTaskName).configure {
    dependsOn(verifyResourcesFromDependencies)
  }
}
