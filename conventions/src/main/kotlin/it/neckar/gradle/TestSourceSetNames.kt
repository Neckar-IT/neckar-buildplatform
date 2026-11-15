package it.neckar.gradle

/**
 * Test code is recognized by its source set name: `test`, `testFixtures` and every name ending in `Test` or `Tests`.
 * `internal/buildplatform/config/detekt/detekt.yml` excludes these directories by path glob.
 */
object TestSourceSetNames {
  fun hasTestSuffix(sourceSetName: String): Boolean {
    return sourceSetName.endsWith("Test") || sourceSetName.endsWith("Tests")
  }

  fun isTestSourceSet(sourceSetName: String): Boolean {
    return sourceSetName == "test" || sourceSetName == "testFixtures" || hasTestSuffix(sourceSetName)
  }

  /** True when a repo-relative, `/`-separated file path lies in a test source set directory `src/<name>/`. */
  fun isInTestSourceSet(path: String): Boolean {
    return path.split("/").dropLast(1).zipWithNext().any { (parent, directory) -> parent == "src" && isTestSourceSet(directory) }
  }
}
