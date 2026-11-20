package it.neckar.projects.common

/**
 * A Maven coordinate a convention plugin or a build script depends on, e.g. `MavenCoordinate("it.neckar.example", "example-service")`.
 * Without [version] the plugin adding the dependency fills in the version it was built with; the root build resolves every `it.neckar.*` coordinate to its project.
 */
data class MavenCoordinate(
  val group: String,
  val name: String,
  val version: String? = null,
) {
  init {
    require(group.isNotBlank() && ':' !in group) { "group must be a non-blank Maven group without ':', was '$group'" }
    require(name.isNotBlank() && ':' !in name) { "name must be a non-blank Maven artifact id without ':', was '$name'" }
    require(version == null || (version.isNotBlank() && ':' !in version)) { "version must be null or a non-blank version without ':', was '$version'" }
  }

  /** `group:name` without the version: the key a producing project is found by. */
  val module: String
    get() = "$group:$name"

  /** This coordinate with [version] unless it carries one already. */
  fun withDefaultVersion(version: String): MavenCoordinate = if (this.version == null) copy(version = version) else this

  /** The dependency notation Gradle reads, `group:name` or `group:name:version`. */
  override fun toString(): String = if (version == null) module else "$module:$version"

  companion object {
    /** True for `it.neckar` itself and for every group below it, the included builds' among them. */
    fun isNeckarGroup(group: String): Boolean = isGroupAtOrBelow(group, "it.neckar")

    /** True for [group] equal to [parent] or below it: `it.neckar.open.ksp` lies below `it.neckar.open`, `it.neckar.openapi` does not. */
    fun isGroupAtOrBelow(group: String, parent: String): Boolean = group == parent || group.startsWith("$parent.")
  }
}
