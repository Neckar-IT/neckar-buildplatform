package it.neckar.gradle

import org.gradle.StartParameter
import org.gradle.api.logging.configuration.ConsoleOutput

/**
 * True when Gradle runs with `--console=plain`.
 */
val StartParameter.isPlainConsole: Boolean
  get() = consoleOutput == ConsoleOutput.Plain
