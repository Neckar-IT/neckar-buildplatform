package it.neckar.gradle.fence

import it.neckar.gradle.BuildplatformPlugins
import it.neckar.gradle.MainSourceSetName
import it.neckar.projects.ConfiguredProject
import it.neckar.gradle.TestSourceSetNames
import it.neckar.gradle.projectProducing
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation

/**
 * One scoped forbid block: scope via [inConfigurations], targets via [forbidProjects] /
 * [forbidProjectsMatching] / [forbidExternalDependencies], mandatory [reason]. Default scope when
 * [inConfigurations] is not called: JVM-only → api/implementation/compileOnly/compileOnlyApi/
 * runtimeOnly; KMP → [allMainSourceSets]. [alsoInTestConfigurations] widens that default onto every
 * test scope.
 *
 * Every forbidden project also leaves the main compile classpaths, and the test ones after [alsoInTestConfigurations], where an allowed dependency
 * brings it in transitively: an import of its classes fails to compile instead of passing the fence.
 */
open class ForbidRule(private val project: Project) {
  /** Mandatory — appears in the failure message. */
  var reason: String = ""

  private var configurationNames: Set<String>? = null
  private var includeTestConfigurations: Boolean = false
  private val forbiddenProjectPaths: MutableSet<String> = mutableSetOf()
  private val forbiddenProjectPatterns: MutableList<Regex> = mutableListOf()
  private val forbiddenExternalPatterns: MutableList<Regex> = mutableListOf()

  fun inConfigurations(vararg names: String) {
    configurationNames = names.toSet()
  }

  fun inConfigurations(names: Collection<String>) {
    configurationNames = names.toSet()
  }

  /**
   * Widens the default scope onto the test configurations. For a rule that draws a layering line a
   * test dependency is legitimate; for one that draws a publish boundary it is not — a test that
   * compiles against a forbidden module still makes the module unbuildable on its own.
   */
  fun alsoInTestConfigurations() {
    includeTestConfigurations = true
  }

  /** Refactor-safe — pass [ConfiguredProject] entries of the project registry rather than raw paths. */
  fun forbidProjects(vararg projects: ConfiguredProject) {
    forbiddenProjectPaths.addAll(projects.map { it.path.path })
  }

  fun forbidProjectPaths(vararg paths: String) {
    forbiddenProjectPaths.addAll(paths)
  }

  fun forbidProjectsMatching(pattern: Regex) {
    forbiddenProjectPatterns.add(pattern)
  }

  /** Pattern matches `${group}:${name}` — version excluded. */
  fun forbidExternalDependencies(pattern: Regex) {
    forbiddenExternalPatterns.add(pattern)
  }

  /**
   * KMP helper. `sourceSets("commonMain", "jvmMain")` →
   * `[commonMainApi, commonMainImplementation, commonMainCompileOnly, jvmMainApi, …]`.
   */
  fun sourceSets(vararg names: String): Set<String> =
    names.flatMap { sourceSetName -> productionConfigurationSuffixes.map { suffix -> "$sourceSetName$suffix" } }.toSet()

  /** All `*Main` production configurations the KMP plugin registered on this project. */
  fun allMainSourceSets(): Set<String> = sourceSetConfigurations { it.endsWith("Main") }

  /** All test source set configurations the KMP plugin registered on this project. */
  fun allTestSourceSets(): Set<String> = sourceSetConfigurations { TestSourceSetNames.isTestSourceSet(it) }

  private fun sourceSetConfigurations(nameFilter: (String) -> Boolean): Set<String> {
    val kotlinExtension = project.extensions.findByType(KotlinProjectExtension::class.java) ?: return emptySet()
    return kotlinExtension.sourceSets
      .filter { nameFilter(it.name) }
      .flatMap { sourceSet -> productionConfigurationSuffixes.map { suffix -> "${sourceSet.name}$suffix" } }
      .toSet()
  }

  internal fun validate() {
    require(reason.isNotBlank()) {
      "dependencyFence: every forbid { } block must set a non-blank 'reason' (project ${project.path})"
    }
    require(forbiddenProjectPaths.isNotEmpty() || forbiddenProjectPatterns.isNotEmpty() || forbiddenExternalPatterns.isNotEmpty()) {
      "dependencyFence: forbid { } block in ${project.path} declares no forbidden projects or externals — at least one of " +
        "forbidProjects / forbidProjectPaths / forbidProjectsMatching / forbidExternalDependencies is required"
    }
  }

  internal fun excludeFromCompileClasspaths() {
    val classpathNames: List<String> = compileClasspathNames()
    val forbiddenProjects: List<Project> = project.rootProject.allprojects.filter { candidate ->
      candidate != project && (candidate.path in forbiddenProjectPaths || forbiddenProjectPatterns.any { it.matches(candidate.path) })
    }
    forbiddenProjects.forEach { forbidden ->
      classpathNames.forEach { name ->
        project.configurations.getByName(name).exclude(mapOf("group" to forbidden.group.toString(), "module" to forbidden.name))
      }
    }
  }

  /** The compile classpaths of the main compilations, plus those of the test compilations after [alsoInTestConfigurations]. */
  private fun compileClasspathNames(): List<String> {
    val multiplatform: KotlinMultiplatformExtension? = project.extensions.findByType(KotlinMultiplatformExtension::class.java)
    if (multiplatform != null) {
      return multiplatform.targets
        .flatMap { it.compilations }
        .filter { it.name == KotlinCompilation.MAIN_COMPILATION_NAME || includeTestConfigurations }
        .map { it.compileDependencyConfigurationName }
    }
    val sourceSets: SourceSetContainer = project.extensions.findByType(SourceSetContainer::class.java) ?: return emptyList()
    return sourceSets
      .filter { it.name == MainSourceSetName || includeTestConfigurations }
      .map { it.compileClasspathConfigurationName }
  }

  internal fun findViolations(): List<Violation> {
    val configurationsToCheck: Set<String> = configurationNames ?: defaultConfigurations()
    val violations: MutableList<Violation> = mutableListOf()

    for (configurationName in configurationsToCheck) {
      val configuration: Configuration = project.configurations.findByName(configurationName) ?: continue
      configuration.dependencies.forEach { dependency ->
        when (dependency) {
          is ProjectDependency -> checkProjectDependency(dependency, configurationName, violations)
          is ExternalModuleDependency -> checkExternalDependency(dependency, configurationName, violations)
        }
      }
    }
    return violations
  }

  private fun checkProjectDependency(dependency: ProjectDependency, configurationName: String, violations: MutableList<Violation>) {
    checkProjectPath(dependency.path, "project(\"${dependency.path}\")", configurationName, violations)
  }

  /** [dependencyPath] against [forbidProjects] and [forbidProjectsMatching]; [dependencyDisplay] names the dependency as declared. */
  private fun checkProjectPath(dependencyPath: String, dependencyDisplay: String, configurationName: String, violations: MutableList<Violation>) {
    if (dependencyPath in forbiddenProjectPaths) {
      violations.add(
        Violation(
          configurationName = configurationName,
          dependencyDisplay = dependencyDisplay,
          matchedBy = "exact match (forbidProjects / forbidProjectPaths)",
          reason = reason,
          sourceSet = sourceSetOf(configurationName),
        )
      )
    }
    forbiddenProjectPatterns.forEach { pattern ->
      if (pattern.matches(dependencyPath)) {
        violations.add(
          Violation(
            configurationName = configurationName,
            dependencyDisplay = dependencyDisplay,
            matchedBy = "pattern $pattern (forbidProjectsMatching)",
            reason = reason,
            sourceSet = sourceSetOf(configurationName),
          )
        )
      }
    }
  }

  private fun checkExternalDependency(dependency: ExternalModuleDependency, configurationName: String, violations: MutableList<Violation>) {
    val coordinate = "${dependency.group}:${dependency.name}"
    val display: String = "\"$coordinate${if (dependency.version != null) ":${dependency.version}" else ""}\""
    // The root build substitutes an it.neckar coordinate by the project producing it, so the project rules apply to it as well.
    val producer: Project? = project.projectProducing(dependency)
    if (producer != null) {
      checkProjectPath(producer.path, "$display (project ${producer.path})", configurationName, violations)
    }
    forbiddenExternalPatterns.forEach { pattern ->
      if (pattern.matches(coordinate)) {
        violations.add(
          Violation(
            configurationName = configurationName,
            dependencyDisplay = display,
            matchedBy = "pattern $pattern (forbidExternalDependencies)",
            reason = reason,
            sourceSet = sourceSetOf(configurationName),
          )
        )
      }
    }
  }

  private fun defaultConfigurations(): Set<String> {
    val kotlinExtension = project.extensions.findByType(KotlinProjectExtension::class.java)
    val isMultiPlatform: Boolean = kotlinExtension != null && project.plugins.hasPlugin(BuildplatformPlugins.KotlinMultiPlatform)
    val production: Set<String> = if (isMultiPlatform) allMainSourceSets() else jvmDefaultConfigurations
    if (includeTestConfigurations.not()) {
      return production
    }
    val test: Set<String> = if (isMultiPlatform) allTestSourceSets() else jvmNonMainConfigurations()
    return production + test
  }

  /**
   * Every declarable configuration of a JVM project's non-`main` source sets, read from the project
   * rather than listed: each `registerIntegrationTestSuite` call names its own suite, and the
   * repository already has five different names. A written list would silently miss the sixth.
   */
  private fun jvmNonMainConfigurations(): Set<String> {
    val sourceSets = project.extensions.findByType(SourceSetContainer::class.java) ?: return emptySet()
    return sourceSets
      .filter { it.name != MainSourceSetName }
      .flatMap { sourceSet ->
        listOf(
          sourceSet.apiConfigurationName,
          sourceSet.implementationConfigurationName,
          sourceSet.compileOnlyApiConfigurationName,
          sourceSet.compileOnlyConfigurationName,
          sourceSet.runtimeOnlyConfigurationName,
        )
      }
      .toSet()
  }

  /** KMP `<sourceSet><Suffix>` → `<sourceSet>`. Null for plain JVM configurations. */
  private fun sourceSetOf(configurationName: String): String? {
    return productionConfigurationSuffixes
      .firstOrNull { suffix -> configurationName.endsWith(suffix) && configurationName != suffix }
      ?.let { suffix -> configurationName.removeSuffix(suffix) }
  }

  companion object {
    /** Longer suffixes first so `CompileOnlyApi` wins over `CompileOnly` and `Api`. */
    private val productionConfigurationSuffixes: List<String> = listOf(
      "CompileOnlyApi",
      "Implementation",
      "CompileOnly",
      "RuntimeOnly",
      "Api",
    )

    /** JVM defaults — test scopes excluded unless the rule calls [alsoInTestConfigurations]. */
    private val jvmDefaultConfigurations: Set<String> = setOf(
      "api",
      "implementation",
      "compileOnly",
      "compileOnlyApi",
      "runtimeOnly",
    )
  }
}
