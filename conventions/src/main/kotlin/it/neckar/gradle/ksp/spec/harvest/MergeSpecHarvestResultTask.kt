package it.neckar.gradle.ksp.spec.harvest

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Merges the harvested specification results from all KSP runs into a single file.
 */
@ExperimentalSerializationApi
abstract class MergeSpecHarvestResultTask : DefaultTask() {
  /**
   * The directory where the single results are harvested to (by the KSP processor)
   */
  @get:InputFiles
  @get:Optional
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val singleResultsHarvestFiles: ConfigurableFileCollection

  /**
   * Where to write the merged results to
   */
  @get:OutputFile
  abstract val mergedResultsFile: RegularFileProperty

  private final val json = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    encodeDefaults = true
    explicitNulls = false
  }

  @TaskAction
  fun merge() {
    if (singleResultsHarvestFiles.isEmpty) {
      logger.warn("No harvested specification result files found in directory: ${singleResultsHarvestFiles.files.joinToString(", ") { it.absolutePath }}. Skipping merge.")
      return
    }

    val mergedResultsFile = mergedResultsFile.get().asFile
    logger.info("Merging harvested specifications from into file: ${mergedResultsFile.absolutePath}")

    //Create the same structure as in SpecHarvestResult (which is not used here directly to avoid dependency issues)
    val mergedJsonResult = buildJsonObject {
      put("classSpecs", buildJsonObject {
        //Sorted by file name: FileTree iteration follows readdir order, which is
        //filesystem-dependent — CI and local builds produced the same entries in different
        //order, making the merged spec (and every jar embedding it) non-reproducible
        singleResultsHarvestFiles.asFileTree.matching { include("*.json") }.files.sortedBy { it.name }.forEach {
          logger.debug("Found harvest result file: ${it.absolutePath}")
          val key = it.nameWithoutExtension

          val element = it.inputStream().buffered().use {
            Json.decodeFromStream<JsonElement>(it)
          }

          //Add the element under the given file name (without extension)
          put(key, element)
        }
      })
    }

    mergedResultsFile.parentFile?.let {
      if (!it.exists()) {
        it.mkdirs()
      }
    }

    mergedResultsFile.outputStream().buffered().use {
      json.encodeToStream(mergedJsonResult, it)
    }
  }
}
