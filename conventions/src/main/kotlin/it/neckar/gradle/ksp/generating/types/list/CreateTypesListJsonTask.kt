package it.neckar.gradle.ksp.generating.types.list

import it.neckar.gradle.ksp.generating.types.list.model.TypesListForProject
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * This task creates a JSON file containing the types lists
 */
abstract class CreateTypesListJsonTask : DefaultTask() {
  @get:InputFile
  abstract val packageNamesListFile: RegularFileProperty

  @get:InputFile
  abstract val classNamesListFile: RegularFileProperty

  @get:OutputDirectory
  abstract val targetDirectory: DirectoryProperty

  @get:Input
  abstract val projectPath: Property<String>

  /** The name of the JSON file in [targetDirectory], unique per project. */
  @get:Input
  abstract val targetFileName: Property<String>

  private val json: Json = Json {
    prettyPrint = true
  }

  @TaskAction
  fun createTypesListJson() {
    val packageNamesListFile = this@CreateTypesListJsonTask.packageNamesListFile.get().asFile
    val classNamesListFile = this@CreateTypesListJsonTask.classNamesListFile.get().asFile


    val packageNames = packageNamesListFile.readLines()
      .map { it.trim() }
      .filter { it.isNotBlank() && it.startsWith("#").not() }

    val classNames = classNamesListFile.readLines()
      .map { it.trim() }
      .filter { it.isNotBlank() && it.startsWith("#").not() }

    val typesListForProject = TypesListForProject(projectPath = projectPath.get(), packageNames = packageNames, classNames = classNames)


    val targetDir = targetDirectory.get().asFile
    targetDir.mkdirs()

    val targetFile = File(targetDir, targetFileName.get())
    logger.info("Creating the types list JSON in [${targetFile.absolutePath}]")

    targetFile.writeText(json.encodeToString(TypesListForProject.serializer(), typesListForProject))
  }
}
