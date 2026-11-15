package it.neckar.gradle

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Project
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.kotlin.dsl.findByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.File

/**
 * Returns true, if this is the root project
 */
fun Project.isRootProject(): Boolean {
  return this.path == this.rootProject.path
}

/**
 * Returns the fqName of the given project that can be used for file names (e.g., jar files)
 *
 * Example:
 * - :internal:open:common.kotlin-lang -> neckarIT-internal-open-common-kotlin-lang
 *
 *
 * ATTENTION: The same implementation is used in `it.neckar.ksp.ts.model.TsModuleReference`
 * When changing this implementation, please also change the other implementation accordingly!
 */
fun Project.pathAsBaseFileName(): String {
  val rootProjectName = rootProject.name

  if (this.isRootProject()) {
    return rootProjectName
  }

  return rootProjectName + "-" + this.path.trimStart(':').replace(':', '-')
}

/**
 * Returns true if this project is a kotlin multiplatform project.
 */
fun Project.hasKotlinMultiplatformPlugin(): Boolean {
  return hasPlugin(BuildplatformPlugins.KotlinMultiPlatform)
}

/**
 * Returns true if this is a multiplatform project
 */
fun Project.isMultiplatform(): Boolean {
  return extensions.findByType<KotlinMultiplatformExtension>() != null
}

fun Project.hasPlugin(kotlinMultiPlatform: String): Boolean {
  return this.pluginManager.findPlugin(kotlinMultiPlatform) != null
}

/**
 * Returns all project dependencies (including transitive dependencies)
 * for the configurations with the given names.
 *
 * Does *not* include the project itself.
 */
fun Project.findAllProjectDependencies(
  /**
   * The configuration names to search for
   */
  configurationNames: List<String>,
  /**
   * Contains a list of already visited project paths that will be skipped
   */
  alreadyVisitedProjectPaths: MutableSet<String> = mutableSetOf(),
): Set<Project> {
  //Add the own path to the already visited projects, return if the path was already visited
  if (alreadyVisitedProjectPaths.add(this.path).not()) return emptySet()

  //Find the projects for the given configuration names
  val found = configurationNames.flatMap { configurationName ->
    val configuration = configurations.findByName(configurationName)

    configuration
      ?.findDirectProjectDependencies(this)
      ?.flatMap { it.findAllProjectDependencies(configurationNames, alreadyVisitedProjectPaths) + it }
      ?: emptyList()
  }

  //Project.equals() is not implemented correctly, it is necessary to filter the projects by path manually
  return found
    .groupBy { it.path }
    .map { (_, dependencies) -> dependencies.first() }
    .toSet()
}

/**
 * Returns all project dependencies for the configuration
 */
fun Configuration.findDirectProjectDependencies(project: Project): List<Project> {
  return allDependencies
    .filterIsInstance<ProjectDependency>()
    .map { project.project(it.path) }
}

/**
 * Returns the named object or null
 */
fun <T : Any> NamedDomainObjectSet<T>.findNamed(name: String): NamedDomainObjectProvider<T>? {
  return try {
    this.named(name)
  } catch (_: UnknownDomainObjectException) {
    null
  }
}

/**
 * Requires that the file exists and returns it.
 *
 * A directory fails too: every caller goes on to read the file, where a directory would end in an
 * `IsADirectoryException` naming nothing.
 *
 * @param messageProvider names what was expected, where the generic wording does not tell the reader
 * which file of many is missing
 * @throws GradleException if the file is null or is not a file
 */
fun File?.requireFileExists(messageProvider: (File?) -> String = { file -> "Required file not found: ${file?.absolutePath}" }): File {
  if (this == null) {
    throw GradleException(messageProvider(null))
  }
  if (isFile.not()) {
    throw GradleException(messageProvider(this))
  }
  return this
}

/**
 * Requires that the directory exists and returns it. The counterpart to [requireFileExists]; a plain
 * file fails here just as a directory fails there.
 *
 * @param messageProvider names what was expected, where the generic wording does not tell the reader
 * which directory of many is missing
 * @throws GradleException if the directory is null or is not a directory
 */
fun File?.requireDirExists(messageProvider: (File?) -> String = { file -> "Required directory not found: ${file?.absolutePath}" }): File {
  if (this == null) {
    throw GradleException(messageProvider(null))
  }
  if (isDirectory.not()) {
    throw GradleException(messageProvider(this))
  }
  return this
}

/**
 * Returns the parsed `package.json` file.
 *
 * Throws an exception if the file does not exist.
 */
internal fun Project.parsePackageJson(): JsonElement {
  val packageJson = file("package.json")

  packageJson.requireFileExists { file -> "Expected ${file?.absolutePath} to be a file" }

  return Json.parseToJsonElement(packageJson.readText())
}
