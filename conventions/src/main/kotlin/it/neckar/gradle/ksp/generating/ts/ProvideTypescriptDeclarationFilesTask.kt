package it.neckar.gradle.ksp.generating.ts

import it.neckar.gradle.requireDirExists
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/**
 * Copies the TypeScript declaration files from the KSP generation dir to the target directory
 */
@DisableCachingByDefault(because = "Not worth it")
abstract class ProvideTypescriptDeclarationFilesTask : DefaultTask() {
  /**
   * The directory where the KSP-generated files are located
   */
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val kspGeneratedParentDir: DirectoryProperty

  /**
   * Where to save the d.ts files for further usage
   */
  @get:OutputDirectory
  abstract val targetDirectory: DirectoryProperty

  @get:Inject
  abstract val fileSystemOperations: FileSystemOperations

  @TaskAction
  fun provideTypescriptDeclarationFile() {
    val sourceDir = kspGeneratedParentDir.get().asFile

    sourceDir.requireDirExists { file ->
      "The source directory is not a dir: ${file?.absolutePath}"
    }

    logger.info("Providing the TypeScript declaration file @ ${targetDirectory.get().asFile.absolutePath}")

    // `sync` rather than `copy`: the target directory is owned exclusively by this task, so a
    // declaration file that KSP no longer generates has to disappear instead of lingering as a stale
    // copy that the consuming projects would keep collecting.
    fileSystemOperations.sync {
      from(sourceDir)
      into(targetDirectory)

      include("*.ts")
    }
  }
}
