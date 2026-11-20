package it.neckar.gradle

import it.neckar.projects.common.MavenCoordinate
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * `group:name` of the artifact this project produces, the [MavenCoordinate.module] a coordinate naming it carries. Unlike the registry's
 * `mavenCoordinate()` it carries no `-parent` suffix: Gradle resolves a dependency by `group:name`.
 */
val Project.mavenModule: String
  get() = "$group:$name"

/** The group of every artifact of the included build `build-logic`, published to the private package registry of the monorepo alone. */
const val BuildLogicGroup: String = "it.neckar.build-logic"

/** The group below which the module `internal/infrastructure/thirdparty` publishes the assets of GitHub releases to the Package Registry. */
const val ThirdPartyGroup: String = "it.neckar.thirdparty"

/**
 * The groups of the included builds `build-logic`, `internal/buildplatform`, `internal/open/project-registry` and
 * `internal/open/meistercharts/project-registry`: Gradle substitutes their projects itself.
 */
private val IncludedBuildGroups: Set<String> = setOf(BuildLogicGroup, "it.neckar.buildplatform", "it.neckar.open.project-registry", "it.neckar.open.meistercharts.project-registry")

/** True for a group the root build resolves to one of its projects: `it.neckar` or below, outside the [IncludedBuildGroups] and [isThirdPartyGroup]. */
fun isProducedByRootBuild(group: String): Boolean {
  return when {
    MavenCoordinate.isNeckarGroup(group).not() -> false
    group in IncludedBuildGroups -> false
    isThirdPartyGroup(group) -> false
    else -> true
  }
}

/** True for [ThirdPartyGroup] and below, whose assets the Package Registry alone holds. */
private fun isThirdPartyGroup(group: String): Boolean = MavenCoordinate.isGroupAtOrBelow(group, ThirdPartyGroup)

/**
 * The project of this build producing [coordinate]; null for a coordinate that resolves from a repository, a third-party one and an
 * `it.neckar` one no project produces alike. The substitution rule fails the resolution of the latter.
 */
fun Project.projectProducing(coordinate: MavenCoordinate): Project? {
  val producers = producingProjects().producersOf(rootProject, coordinate.module)
  check(producers.size <= 1) { "$coordinate names the projects ${producers.map { it.path }}; a coordinate names one producing project" }
  return producers.singleOrNull()
}

/**
 * The project the root build substitutes [dependency] by; null for a third-party coordinate, one of the [IncludedBuildGroups] and an
 * `it.neckar` one no project produces, whose resolution the substitution fails.
 */
fun Project.projectProducing(dependency: ExternalModuleDependency): Project? {
  val group: String = dependency.group.requireNotNull { "$path declares $dependency without a group" }
  if (isProducedByRootBuild(group).not()) {
    return null
  }
  return projectProducing(MavenCoordinate(group, dependency.name))
}

/** The projects of a build by the module they produce. */
private class ProducingProjects(projects: List<Project>) {
  private val producersByModule: Map<String, List<Project>> = projects.groupBy { it.mavenModule }

  fun producersOf(module: String): List<Project> = producersByModule[module].orEmpty()
}

/**
 * One per build: the index of the producing projects, built once every project is evaluated and every `group` is set; before that each
 * call reads the projects anew. The substitution rule asks per dependency, from the resolving threads of several projects at once.
 */
internal abstract class ProducingProjectsService : BuildService<BuildServiceParameters.None> {
  @Volatile
  private var index: ProducingProjects? = null

  internal fun producersOf(root: Project, module: String): List<Project> {
    index?.let { return it.producersOf(module) }
    val fresh = ProducingProjects(root.allprojects.toList())
    if (root.allprojects.all { it.state.executed }) {
      index = fresh
    }
    return fresh.producersOf(module)
  }

  companion object {
    const val Name: String = "neckarHubProducingProjects"
  }
}

private fun Project.producingProjects(): ProducingProjectsService {
  return gradle.sharedServices.registerIfAbsent(ProducingProjectsService.Name, ProducingProjectsService::class.java) {}.get()
}
