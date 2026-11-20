package it.neckar.gradle.artifact

import java.io.File
import java.util.zip.ZipFile

/**
 * One part an image is assembled from, named by [label], its path below the module or its file name. A directory contributes itself and
 * everything below it, an absent one counts as an empty one.
 */
data class ArtifactPart(val label: String, val file: File)

/**
 * The collectors of the content-addressed artifacts: they read what the build assembled, so their key needs no list of the sources
 * and exists only after the build.
 */
object ContentAddressedInputs {
  /** Every entry of the finished [jar] except [ArtifactKey.ResourcePath], by name. */
  fun ofJar(jar: File): ArtifactInputs {
    val inputs = ZipFile(jar).use { zip ->
      zip.entries().asSequence().filter { it.name != ArtifactKey.ResourcePath }.sortedBy { it.name }.map { entry ->
        if (entry.isDirectory) {
          ArtifactInput.Directory(entry.name)
        } else {
          ArtifactInput.File(entry.name, zip.getInputStream(entry).use { FileContentHash.ofOwnBytes(it) })
        }
      }.toList()
    }
    return ArtifactInputs(inputs)
  }

  /** The [settings] of an image by name, then its [parts] in image order. */
  fun ofImage(settings: List<ArtifactInput.Setting>, parts: List<ArtifactPart>): ArtifactInputs {
    return ArtifactInputs(settings.sortedBy { it.name } + parts.flatMap { contentOf(it) })
  }

  private fun contentOf(part: ArtifactPart): List<ArtifactInput.Content> {
    val root = part.file
    if (root.isFile) {
      return listOf(ArtifactInput.File(part.label, root.inputStream().use { FileContentHash.ofOwnBytes(it) }))
    }
    val below = root.walkTopDown().filter { it != root }.map { it.relativeTo(root).invariantSeparatorsPath to it }.sortedBy { it.first }
      .map { (relativePath, entry) ->
        if (entry.isDirectory) {
          ArtifactInput.Directory("${part.label}/$relativePath")
        } else {
          ArtifactInput.File("${part.label}/$relativePath", entry.inputStream().use { FileContentHash.ofOwnBytes(it) })
        }
      }
    return listOf(ArtifactInput.Directory(part.label)) + below
  }
}
