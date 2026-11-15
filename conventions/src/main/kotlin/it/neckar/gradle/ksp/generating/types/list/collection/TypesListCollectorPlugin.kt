package it.neckar.gradle.ksp.generating.types.list.collection

import it.neckar.gradle.ansiConsole
import it.neckar.gradle.findAllProjectDependencies
import it.neckar.gradle.isRootProject
import it.neckar.gradle.ksp.generating.types.list.CreateTypesListJsonTask
import it.neckar.gradle.ksp.generating.types.list.TypesListGeneratorPlugin
import it.neckar.gradle.ksp.generating.types.list.collection.TypesListCollectorPlugin.Paths
import it.neckar.gradle.ksp.generating.types.list.model.TypesListForProjectCollection
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.assign

/**
 * Plugin that collects the type lists from all:
 * * subprojects - if this is applied to the root project
 * * dependencies - if this is applied to a "normale" project"
 */
class TypesListCollectorPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    /**
     * The task that collects the type lists (from subprojects or dependencies)
     */
    val collectTypeListsTask: TaskProvider<out AbstractCollectTypesListTask> = when {
      target.isRootProject() -> {
        target.tasks.register<CollectTypeListsFromSubprojectsTask>(CollectTypeListsFromSubprojectsTaskName) {
          group = "KSP"
          description = "Collects the type lists from all subprojects"

          collectedTypeListsDirectory = target.layout.buildDirectory.dir(Paths.CollectedDir)

          // Declared as a tracked input - not merely as a `dependsOn` - so a changed set of exported
          // types in a subproject invalidates this task instead of leaving it UP-TO-DATE with a stale
          // copy. Each directory is the `@OutputDirectory` of the subproject's task, which carries the
          // task dependency along with it. The provider is lazy, so no configuration-time resolution.
          sourceDirectories.from(
            target.provider {
              target.subprojects.mapNotNull { subproject ->
                (subproject.tasks.findByName(TypesListGeneratorPlugin.CreateTypesListJsonTaskName) as? CreateTypesListJsonTask)?.targetDirectory
              }
            }
          )
        }
      }

      else -> {
        target.tasks.register<CollectTypeListsFromDependenciesTask>(CollectTypeListsFromDependenciesTaskName) {
          group = "KSP"
          description = "Collects the type lists from all dependencies"

          collectedTypeListsDirectory = target.layout.buildDirectory.dir(Paths.CollectedDir)

          // Declared as a tracked input - not merely as a `dependsOn` - so a changed set of exported
          // types in a dependency invalidates this task instead of leaving it UP-TO-DATE with a stale
          // copy. Each directory is the `@OutputDirectory` of the dependency's task, which carries the
          // task dependency along with it. The provider is lazy, so it resolves the dependency
          // configurations only when the inputs are queried, not during task-graph construction.
          sourceDirectories.from(
            target.provider {
              target.findAllProjectDependencies(CollectTypeListsFromDependenciesTask.configurationNames)
                .mapNotNull { dependency ->
                  (dependency.tasks.findByName(TypesListGeneratorPlugin.CreateTypesListJsonTaskName) as? CreateTypesListJsonTask)?.targetDirectory
                }
            }
          )
        }
      }
    }

    val mergeTypesListCollectionTask = target.tasks.register<MergeTypesListCollectionTask>(MergeTypesListTaskName) {
      group = "KSP"
      description = "Merges all types-lists into a single file"

      dependsOn(collectTypeListsTask)

      this.collectedDir = collectTypeListsTask.flatMap { it.collectedTypeListsDirectory }
      this.outputFile = target.layout.buildDirectory.file(Paths.TypesCollectionJson)
    }

    val printTypesListForProjectCollectionTask = target.tasks.register<PrintTypesListForProjectCollectionTask>(PrintTypesListForProjectCollectionTaskName) {
      group = "KSP"
      description = "Prints the types list for project collection"

      typesListForProjectFile = mergeTypesListCollectionTask.flatMap { it.outputFile }
      console.set(target.ansiConsole)
    }
  }

  companion object {
    /**
     * Task name for merging the types-list collection - from subprojects
     */
    const val CollectTypeListsFromSubprojectsTaskName: String = "collectTypeListsFromSubprojects"

    /**
     * Task name for merging the types-list collection - from dependencies
     */
    const val CollectTypeListsFromDependenciesTaskName: String = "collectTypeListsFromDependencies"

    /**
     * Task name for merging the types-list collection
     */
    const val MergeTypesListTaskName: String = "mergeTypesList"

    /**
     * Prints the types-list for the current project
     */
    const val PrintTypesListForProjectCollectionTaskName: String = "printTypesListForProjectCollection"
  }

  /**
   * Contains the paths that are used by the plugin.
   */
  object Paths {
    /**
     * Contains the collected types (from subprojects or dependencies)
     */
    const val CollectedDir: String = "types-lists/modules/collected"

    /**
     * Contains the (merged) types collection json file (for subprojects or dependencies)
     */
    const val TypesCollectionJson: String = "types-lists/modules/types-collection-from-modules.json"
  }
}
