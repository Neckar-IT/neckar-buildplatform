package it.neckar.gradle.tailwind

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/**
 * Compiles tailwind with the `tailwindcss` CLI that `@tailwindcss/cli` in the root `package.json` installs.
 *
 * Declares no output, so it runs on every call and is never cached: Tailwind reads files Gradle cannot list,
 * the templates its automatic source detection finds below [projectDirectory] or its `@source` rules name,
 * and every stylesheet the input imports.
 */
@DisableCachingByDefault(because = "Tailwind reads templates and imported stylesheets Gradle cannot list")
abstract class TailwindCompileTask
@Inject constructor(
  private val execOperations: ExecOperations,
) : DefaultTask() {
  /** `node_modules/.bin/tailwindcss` of the repository root. */
  @get:Internal
  abstract val executable: RegularFileProperty

  /** The Tailwind input CSS file. */
  @get:Internal
  abstract val input: RegularFileProperty

  /** The Tailwind output CSS file; `@OutputFile` would let Gradle skip the task although a template changed. */
  @get:Internal
  abstract val output: RegularFileProperty

  /** The working directory of the Tailwind CLI, the base of its automatic source detection. */
  @get:Internal
  abstract val projectDirectory: DirectoryProperty

  @TaskAction
  fun compileTailwind() {
    val inputFile: RegularFile = input.get()
    val outputFile: RegularFile = output.get()

    logger.lifecycle("Compiling TailwindCSS from ${inputFile.asFile.absolutePath} to ${outputFile.asFile.absolutePath}")

    execOperations.exec {
      workingDir = projectDirectory.get().asFile
      executable = this@TailwindCompileTask.executable.get().asFile.absolutePath
      args = listOf("--input", inputFile.asFile.absolutePath, "--output", outputFile.asFile.absolutePath)
    }

    logger.lifecycle("TailwindCSS compiled successfully to ${outputFile.asFile.absolutePath}")
  }
}
