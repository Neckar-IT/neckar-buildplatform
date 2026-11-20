package it.neckar.gradle.fence

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Fails the configuration when a Kotlin source set depends on another project of [frontendProjectPaths] (`ProjectRole.Frontend`);
 * a frontend project's dependency on itself, every other configuration and a task dependency stay allowed.
 */
fun Project.forbidDependenciesOnFrontendProjects(frontendProjectPaths: Collection<String>) {
  val otherFrontendProjectPaths: List<String> = frontendProjectPaths.filter { frontendProjectPath -> frontendProjectPath != path }
  if (otherFrontendProjectPaths.isEmpty()) {
    return
  }

  plugins.apply(DependencyFencePlugin::class.java)
  extensions.configure<DependencyFenceExtension> {
    forbid {
      reason = "A Kotlin source set depends on a frontend project (ProjectRole.Frontend); move the shared code into a library."
      forbidProjectPaths(*otherFrontendProjectPaths.toTypedArray())
      alsoInTestConfigurations()
    }
  }
}
