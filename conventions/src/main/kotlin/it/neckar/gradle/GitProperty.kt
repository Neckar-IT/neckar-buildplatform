package it.neckar.gradle

/**
 * The per-commit git values a deploy injects; `version-info` generates its
 * runtime `GitProperty` enum 1:1 from these constants, so injection and resolution cannot drift.
 */
enum class GitProperty(
  /** The key of the value in `window.__APP_GIT_INFO__`. */
  val propertyKey: String,
  /** The env variable that names the commit to a process, e.g. a frontend whose deploy sets it. */
  val envVar: String,
  /**
   * The JVM system property that overrides the environment variable (highest resolver priority).
   */
  val systemProperty: String,
) {
  Hash("gitHash", "APP_GIT_HASH", "app.git.hash"),
  CommitDateTime("gitCommitDateTime", "APP_GIT_COMMIT_DATETIME", "app.git.commitDateTime"),
  ;
}
