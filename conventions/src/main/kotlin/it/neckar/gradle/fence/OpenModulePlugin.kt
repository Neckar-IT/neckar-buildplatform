package it.neckar.gradle.fence

import it.neckar.gradle.BuildLogicGroup
import it.neckar.gradle.BuildplatformPlugins
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Convention plugin for the modules under `internal/open`.
 *
 * Applies [DependencyFencePlugin] with a forbid rule that bans every project outside the open tree
 * from the module's production *and* test source sets, by `project(…)` and by Maven coordinate alike:
 * Maven Central carries the open modules alone. A test dependency counts too: a module whose tests
 * compile against another project cannot be built outside the monorepo either.
 *
 * `internal/open/build.gradle.kts` applies this to itself and every subproject, so the fence is a property of
 * where a module lies rather than something its build script opts into — a new module under
 * `internal/open` is covered the moment it exists. The open tree is the topmost project applying this plugin:
 * `:internal:open` in the monorepo, the root project `:` in the export of `internal/open`.
 *
 * The fence is structural — moving a forbidden dep to a test source set or adding the
 * `@CrossLayer` shape is not enough; the dep must not appear at all. Demos and test modules that
 * legitimately depend on other code lie outside `internal/open`.
 */
class OpenModulePlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.plugins.apply(BuildplatformPlugins.DependencyFence)
    target.extensions.configure<DependencyFenceExtension> {
      forbid {
        reason = "Open modules depend on open modules alone; Maven Central carries no other project of this repository."
        forbidProjectsMatching(Regex("^(?!${Regex.escape(openTreePrefix(target))}).+$"))
        // The included build build-logic publishes to the private package registry of the monorepo alone; no project of this build produces it.
        forbidExternalDependencies(Regex("^${Regex.escape(BuildLogicGroup)}:.+$"))
        alsoInTestConfigurations()
      }
    }
  }
}

/** `:internal:open:` for every project below `:internal:open`, `:` in a build whose root project applies [OpenModulePlugin]. */
private fun openTreePrefix(project: Project): String {
  val openTreeRoot: Project = generateSequence(project) { it.parent }.last { it.plugins.hasPlugin(OpenModulePlugin::class.java) || it == project }
  return if (openTreeRoot == openTreeRoot.rootProject) ":" else "${openTreeRoot.path}:"
}
