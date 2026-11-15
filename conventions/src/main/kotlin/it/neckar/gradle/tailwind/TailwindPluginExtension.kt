package it.neckar.gradle.tailwind

import org.gradle.api.file.RegularFileProperty

/** The `tailwind {}` block of a project applying [TailwindPlugin]. */
interface TailwindPluginExtension {
  /** The tailwind input file. */
  val input: RegularFileProperty

  /** The tailwind output file. */
  val output: RegularFileProperty
}
