package it.neckar.gradle.ksp.generating.types.list.collection

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import javax.inject.Inject

/**
 * Abstract base class for tasks that collect type lists.
 */
abstract class AbstractCollectTypesListTask : DefaultTask() {
  /**
   * The `createTypesListJson` output directory of every source (subproject or dependency), wired by
   * [TypesListCollectorPlugin].
   *
   * [sourceDirectories] must stay a declared input: resolving the sources inside the task action
   * instead - as this task once did - leaves the task without any input, and Gradle then keeps it
   * UP-TO-DATE while a source's types-list JSON changes underneath it. `dependsOn` orders execution,
   * it does not track content. Being a task output, each directory also carries its producing task,
   * so this property establishes the task dependency as well.
   */
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val sourceDirectories: ConfigurableFileCollection

  /**
   * The output directory where the collected type lists will be written to
   */
  @get:OutputDirectory
  abstract val collectedTypeListsDirectory: DirectoryProperty

  @get:Inject
  abstract val fileSystemOperations: FileSystemOperations
}
