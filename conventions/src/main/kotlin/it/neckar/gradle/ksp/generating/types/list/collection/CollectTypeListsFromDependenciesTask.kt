package it.neckar.gradle.ksp.generating.types.list.collection

import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.TaskAction

/**
 * Collects the type lists from all dependencies.
 * Should be run *before* compiling the project.
 *
 * The dependencies' types-list JSON directories are declared as [sourceDirectories] (a tracked
 * input), so a changed set of exported types in a dependency actually invalidates this task instead
 * of leaving it UP-TO-DATE with a stale copy.
 */
abstract class CollectTypeListsFromDependenciesTask : AbstractCollectTypesListTask() {
  @TaskAction
  fun collect() {
    // `sync` rather than `copy`: the collected directory is owned exclusively by this task, so a
    // types-list JSON whose dependency was dropped has to disappear with it.
    fileSystemOperations.sync {
      sourceDirectories.forEach { sourceDirectory ->
        from(sourceDirectory) {
          include("*.json")
        }
      }

      into(collectedTypeListsDirectory)

      duplicatesStrategy = DuplicatesStrategy.FAIL
    }
  }

  companion object {
    /**
     * The configuration names that are used to find the dependencies
     */
    val configurationNames: List<String> = listOf("jsRuntimeClasspath", "jvmRuntimeClasspath")
  }
}
