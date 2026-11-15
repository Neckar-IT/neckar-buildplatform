package it.neckar.gradle

import java.util.Locale

/**
 * Converts a string to camel case format.
 *
 * Example: "hello_world" -> "helloWorld"
 *
 * @return The converted camel case string.
 */
fun String.toCamelCase(): String {
  return this.split("_", "-", " ")
    .joinToString(separator = "") {
      it.replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
      }
    }
    .replaceFirstChar { it.lowercase() }
}
