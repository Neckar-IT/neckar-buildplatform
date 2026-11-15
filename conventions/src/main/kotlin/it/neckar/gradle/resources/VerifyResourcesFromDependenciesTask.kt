package it.neckar.gradle.resources

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Fails when the resources of the dependencies did not arrive in the target's resources.
 *
 * A module applies `webResourcesFromDependencies` because its browser bundle needs files it does not
 * own. When that collection stops working, nothing fails: the module still has its own resources, the
 * build is green, and the missing files surface as 404s in the browser. That happened once already,
 * when the collection searched a fixed list of dependency configurations and a module declaring
 * `jsMain { implementation(...) }` was invisible to it — `planner-ui` produced zero of its 24 `solar`
 * files and built successfully.
 *
 * The check is a lower bound, not a comparison against an expected list: it demands that at least one
 * file arrived that the module does not own. That is enough to catch a collection that resolves
 * nothing, and it needs no maintenance when a dependency's resources change.
 */
abstract class VerifyResourcesFromDependenciesTask : DefaultTask() {
  /**
   * The resources of this target and of its dependencies, as the Kotlin Gradle Plugin aggregated
   * them. An input, so the aggregation runs first and this task knows the directory to relate the
   * paths to.
   */
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val resolvedResourcesDirectory: DirectoryProperty

  /** The target's own resource source directories — everything below them is not a contribution. */
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val ownResourceDirectories: ConfigurableFileCollection

  /** The Gradle path of the module the failure names. */
  @get:Internal
  abstract val projectPath: Property<String>

  @TaskAction
  fun verifyResourcesFromDependenciesArrived() {
    val resolvedResources: File = resolvedResourcesDirectory.get().asFile

    val ownRelativePaths: Set<String> = ownResourceDirectories.files.flatMapTo(mutableSetOf()) { ownDirectory ->
      relativeFilePathsBelow(ownDirectory)
    }
    val relativePathsFromDependencies: List<String> = relativeFilePathsBelow(resolvedResources)
      .filter { relativePath -> ownRelativePaths.contains(relativePath).not() }

    if (relativePathsFromDependencies.isEmpty()) {
      throw GradleException(
        "[${projectPath.get()}] applies webResourcesFromDependencies, but not one file of its dependencies reached " +
          "${resolvedResources.path}. Either the module needs no resources from its dependencies - then remove the " +
          "plugin - or the collection is broken, in which case the browser bundle ships without those files and says " +
          "so only as a 404 at runtime."
      )
    }

    logger.info("${relativePathsFromDependencies.size} resources arrived from dependencies")
  }
}
