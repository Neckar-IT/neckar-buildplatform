package it.neckar.open.generated

/** What a file head says about its origin. */
data class GeneratedFileOrigin(
  val by: String,

  /** Null where the head carries neither a `Source:` line nor a non-empty `from =`. */
  val source: String?,
)
