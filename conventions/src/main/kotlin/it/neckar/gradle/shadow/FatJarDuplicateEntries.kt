package it.neckar.gradle.shadow

import org.gradle.api.GradleException
import java.io.File
import java.util.zip.ZipFile

/**
 * Finds paths that occur more than once inside a fat jar.
 *
 * A duplicate is never harmless: the JVM serves whichever copy the zip iteration order happens to
 * hit first, so the effective content depends on classpath order rather than on a decision anybody
 * made. [FatJarMetadataPlugin] turns any finding into a build failure.
 */
object FatJarDuplicateEntries {
  /**
   * Throws a [GradleException] listing every duplicated path when [fatJar] contains any, otherwise
   * returns normally. [cleanTaskPath] is the path of the building module's `clean` task, named in
   * the failure message so the stale-output case can be ruled out with one copyable command.
   *
   * Lives here rather than in [FatJarMetadataPlugin] so the `doLast` action that calls it captures
   * only this object, the archive provider and the task path, never the plugin instance — which
   * keeps the task action configuration-cache safe.
   */
  fun verify(fatJar: File, cleanTaskPath: String) {
    val duplicateCounts: Map<String, Int> = find(fatJar)

    if (duplicateCounts.isEmpty()) {
      return
    }

    throw GradleException(
      "${fatJar.name} contains ${duplicateCounts.size} duplicated entries:\n${describe(duplicateCounts)}\n" +
        "Which copy wins is decided by classpath order, not by a decision. Give the path a single owner: " +
        "resolve the dependency conflict, merge the metadata via FatJarMetadataPlugin, or move the resource " +
        "into the one module that owns it. Suppressing the check is not an option.\n" +
        "When the duplicated paths are this repository's own resources, rule out a stale build directory " +
        "first: Gradle cleans up only the outputs it still knows about, so files a since-removed task copied " +
        "into another task's output directory stay there. Run `./gradlew $cleanTaskPath` and build again."
    )
  }

  /**
   * Returns the duplicated paths of [fatJar] together with how often each occurs. Empty when every
   * path occurs exactly once. Directory entries are ignored - a jar legitimately repeats those.
   */
  fun find(fatJar: File): Map<String, Int> {
    return ZipFile(fatJar).use { zipFile ->
      zipFile.entries()
        .asSequence()
        .filter { zipEntry -> zipEntry.isDirectory.not() }
        .groupingBy { zipEntry -> zipEntry.name }
        .eachCount()
        .filterValues { occurrences -> occurrences > 1 }
    }
  }

  /**
   * Formats the result of [find] as the message body of the build failure: one line per duplicated
   * path, most frequent first.
   */
  fun describe(duplicateCounts: Map<String, Int>): String {
    return duplicateCounts.entries
      .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
      .joinToString(separator = "\n") { (entryName, occurrences) -> "  $entryName ($occurrences times)" }
  }
}
