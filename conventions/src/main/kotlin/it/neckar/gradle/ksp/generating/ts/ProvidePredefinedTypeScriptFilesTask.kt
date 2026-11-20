package it.neckar.gradle.ksp.generating.ts

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Provides the predefined TypeScript (declaration) files.
 * See [PredefinedTypeScriptDeclarationFiles] for available files
 */
@DisableCachingByDefault(because = "Not worth it")
abstract class ProvidePredefinedTypeScriptFilesTask : DefaultTask() {
  /**
   * Where to save the d.ts files for further usage
   */
  @get:OutputDirectory
  abstract val targetDirectory: DirectoryProperty

  @TaskAction
  fun provideTypescriptFiles() {
    val targetDirectory = targetDirectory.get().asFile

    logger.info("Providing the predefined TypeScript declaration file in @ ${targetDirectory.absolutePath}")
    saveFile(targetDir = targetDirectory, predefinedDTsFile = PredefinedTypeScriptDeclarationFiles.kotlinStdlibDeclaration)
    saveFile(targetDir = targetDirectory, predefinedDTsFile = PredefinedTypeScriptDeclarationFiles.kotlinNativeTypesTs)
  }

  fun saveFile(targetDir: File, predefinedDTsFile: PredefinedDTsFile) {
    val targetFile = File(targetDir, predefinedDTsFile.name)
    targetFile.writeText(predefinedDTsFile.content)
  }
}
