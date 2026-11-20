package it.neckar.gradle

/** The directory segments that carry no information in a Maven group. */
private val DroppedGroupSegments: Set<String> = setOf("internal", "closed", "commons", "units")

/**
 * The Maven group of the project at [projectPath], below the directory segments [rootSegments] of its build's root:
 * the monorepo passes none, the build rooted at `internal/open` passes `open`. Both name `:internal:open:commons:kotlin-lang`
 * and `:commons:kotlin-lang` `it.neckar.open`.
 *
 * The group mirrors the directory structure, minus segments that carry no information:
 * - `internal`: everything lives below it
 * - `closed`: the directory marks the open/closed boundary, and no closed coordinate reaches Maven Central
 * - `commons`: a "misc" grouping directory
 * - `units`: packaging around the single real `unit` module
 * - a sub-collector segment whose name repeats its parent (`meistercharts/meistercharts-api` is `it.neckar.open.meistercharts`)
 *
 * The name (last path segment) is the artifactId and never part of the group, even when it equals a dropped segment.
 */
fun mavenGroupOf(rootSegments: List<String>, projectPath: String): String {
  val groupSegments: List<String> = buildList {
    for (segment in rootSegments + projectPath.split(":").filter { it.isNotEmpty() }.dropLast(1)) {
      if (segment in DroppedGroupSegments) continue
      if (isNotEmpty() && segment.startsWith("${last()}-")) continue
      add(segment)
    }
  }
  return (listOf("it.neckar") + groupSegments).joinToString(".")
}
