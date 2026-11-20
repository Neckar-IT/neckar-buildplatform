package it.neckar.gradle.ksp.generating.types.list.collection

import it.neckar.gradle.AnsiConsole
import it.neckar.gradle.ksp.generating.types.list.model.TypesListForProjectCollection
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * Prints the [it.neckar.gradle.ksp.generating.types.list.model.TypesListForProjectCollection]
 */
abstract class PrintTypesListForProjectCollectionTask : DefaultTask() {
  @get:InputFile
  abstract val typesListForProjectFile: RegularFileProperty

  @get:Internal
  abstract val console: Property<AnsiConsole>

  @TaskAction
  fun printTypesList() {
    val ansiConsole = console.get()
    val typesListForProjectCollection: TypesListForProjectCollection = this.typesListForProjectFile.get().asFile
      .inputStream().use { input ->
        TypesListForProjectCollection.load(input)
      }

    logger.lifecycle(ansiConsole.orange("--------------------------"))
    logger.lifecycle(ansiConsole.orange("Packages for projects:"))
    logger.lifecycle(ansiConsole.orange("--------------------------"))
    typesListForProjectCollection.entries.forEach { typesListForProject ->
      logger.lifecycle("Project: ${ansiConsole.green(typesListForProject.projectPath)}")
      typesListForProject.packageNames.forEach { packageName ->
        logger.lifecycle("  - ${ansiConsole.blue(packageName)}")
      }
    }

    logger.lifecycle(ansiConsole.orange("--------------------------"))
    logger.lifecycle(ansiConsole.orange("Classes for projects:"))
    logger.lifecycle(ansiConsole.orange("--------------------------"))
    typesListForProjectCollection.entries.forEach { typesListForProject ->
      logger.lifecycle("Project: ${ansiConsole.green(typesListForProject.projectPath)}")
      typesListForProject.classNames.forEach { packageName ->
        logger.lifecycle("  - ${ansiConsole.blue(packageName)}")
      }
    }
  }
}
