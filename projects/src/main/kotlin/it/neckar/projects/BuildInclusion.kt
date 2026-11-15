package it.neckar.projects

/**
 * Which builds of the monorepo run the tasks of a registered module. Independent of whether the module also builds outside
 * the checkout (its own `settings.gradle.kts`). See `docs/gradle/create-project.md`.
 */
enum class BuildInclusion {
  /** Every build that asks for the task runs it: `./gradlew build` builds the module. */
  Always,

  /**
   * A build runs a task of the module only when it names the module, starts in its directory or a task outside every unrequested
   * such module needs the task; it shows every other task as `SKIPPED`.
   */
  OnRequest,
}
