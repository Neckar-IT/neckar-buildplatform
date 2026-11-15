package it.neckar.gradle.artifact

import java.io.File

/** Writes [KeyFileName] and [InputsFileName] of one artifact into one directory, the same for every kind of artifact. */
object ArtifactKeyReport {
  const val KeyFileName: String = "key.txt"

  const val InputsFileName: String = "inputs.txt"

  /** The directory below the build directory that holds the reports named [reportName]: the name of the writing task, `bake` for the bake images. */
  fun directoryPath(reportName: String): String = "artifact-key/$reportName"

  fun write(directory: File, inputs: ArtifactInputs) {
    directory.mkdirs()
    directory.resolve(InputsFileName).writeText(inputs.serialization)
    directory.resolve(KeyFileName).writeText(inputs.key.value + "\n")
  }

  /** The lines of [InputsFileName] in [directory]. */
  fun readInputs(directory: File): ArtifactInputs {
    return ArtifactInputs.parse(directory.resolve(InputsFileName).readText())
  }

  fun readKey(directory: File): ArtifactKey {
    return ArtifactKey(directory.resolve(KeyFileName).readText().trim())
  }
}
