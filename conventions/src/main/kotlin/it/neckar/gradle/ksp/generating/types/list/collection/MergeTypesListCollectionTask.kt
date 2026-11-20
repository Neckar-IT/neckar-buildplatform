package it.neckar.gradle.ksp.generating.types.list.collection

import it.neckar.gradle.requireDirExists
import org.gradle.kotlin.dsl.assign
import it.neckar.gradle.ksp.generating.types.list.model.TypesListForProject
import it.neckar.gradle.ksp.generating.types.list.model.TypesListForProjectCollection
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Creates a single file with all types - for all collected types-list files
 */
abstract class MergeTypesListCollectionTask : DefaultTask() {
  /**
   * The directory where the single files have been collected
   */
  @get:InputDirectory
  abstract val collectedDir: DirectoryProperty

  /**
   * The output file where the merged types-list collection will be written to
   */
  @get:OutputFile
  abstract val outputFile: RegularFileProperty

  private val json: Json = Json {
    prettyPrint = true
  }

  @TaskAction
  fun createMergedTypesList() {
    val outputFile = outputFile.get().asFile

    val collectedDir = collectedDir.get().asFile
    collectedDir.requireDirExists { file -> "Dir for collected type lists not found in ${file?.absolutePath}" }

    val typesListForProjectFiles = collectedDir.listFiles { _, name ->
      name.endsWith(".json")
    }?.toList() ?: throw IllegalStateException("Could not list files in ${collectedDir.absolutePath}")


    val typesListForProjects: List<TypesListForProject> = typesListForProjectFiles.map { typesListForProjectFile ->
      val text: String = typesListForProjectFile.readText()
      try {
        Json.decodeFromString(TypesListForProject.serializer(), text)
      } catch (e: kotlinx.serialization.SerializationException) {
        throw IllegalStateException(
          "Could not deserialize types-list file for project in ${typesListForProjectFile.absolutePath}",
          e,
        )
      }
    }

    val typesListForProjectCollection = TypesListForProjectCollection(typesListForProjects)

    outputFile.writeText(json.encodeToString(TypesListForProjectCollection.serializer(), typesListForProjectCollection))
  }
}
