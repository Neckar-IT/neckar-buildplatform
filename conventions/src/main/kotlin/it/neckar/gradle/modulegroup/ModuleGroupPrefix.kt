package it.neckar.gradle.modulegroup

/**
 * The prefix every direct module of a module group carries before a `-`, e.g. `elektromeister` for
 * `internal/closed/elektromeister/elektromeister-service`.
 */
@JvmInline
value class ModuleGroupPrefix(val value: String) {
  init {
    require(value.isNotBlank()) { "ModuleGroupPrefix must not be blank" }
  }

  override fun toString(): String = value

  companion object {
    /**
     * [directoryName] without its domain (`auto-merge.neckar.it`) or visibility suffix (`meistercharts-closed`):
     * modules carry the product, hence `meistercharts-closed/meistercharts-pv-planning`.
     */
    fun fromDirectoryName(directoryName: String): ModuleGroupPrefix {
      return ModuleGroupPrefix(directoryName.substringBefore('.').removeSuffix("-closed"))
    }
  }
}
