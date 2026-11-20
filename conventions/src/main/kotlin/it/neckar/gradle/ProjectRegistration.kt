package it.neckar.gradle

/**
 * Where a project is registered: the registry file and the expression a build script writes for its
 * entry, e.g. the accessor `open.commons.kotlinLang` of the registry `Projects`. The registry answers it; an error message names both.
 */
data class ProjectRegistration(
  /** The registry file, relative to the repository root. */
  val file: String,
  val accessor: String,
)
