package it.neckar.gradle.ksp.generating.ts

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

/**
 * Copies the TypeScript declaration files of all JS dependencies into a single directory.
 *
 * [sourceDirectories] must stay a declared input: resolving the dependencies inside the task action
 * instead - as this task once did - leaves the task without any input, and Gradle then keeps it
 * UP-TO-DATE while a dependency's `.d.ts` changes underneath it. `dependsOn` orders execution, it does
 * not track content.
 */
abstract class CollectTypescriptDeclarationFilesFromDependenciesTask : DefaultTask() {
  /**
   * The `provideTypescriptDeclarationFiles` output directory of every JS dependency, wired by
   * [TypescriptDefinitionGenerationPlugin]. Being a task output, each directory carries its producing
   * task, so this property also establishes the task dependency.
   */
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val sourceDirectories: ConfigurableFileCollection

  /**
   * Where to copy the TypeScript files
   */
  @get:OutputDirectory
  abstract val targetDirectory: DirectoryProperty

  @get:Inject
  abstract val fileSystemOperations: FileSystemOperations

  @TaskAction
  fun copyTypescriptFiles() {
    logger.info("Copying TypeScript Declaration from dependencies to [${targetDirectory.get().asFile.absolutePath}]")

    // `sync` rather than `copy`: the target directory is owned exclusively by this task, and a
    // declaration file whose dependency was dropped has to disappear with it.
    fileSystemOperations.sync {
      sourceDirectories.forEach { sourceDirectory ->
        logger.info("From dependency directory: $sourceDirectory")

        from(sourceDirectory) {
          include("*.d.ts")
          include("*.ts")
        }
      }

      into(targetDirectory)

      duplicatesStrategy = DuplicatesStrategy.FAIL
    }
  }
}
