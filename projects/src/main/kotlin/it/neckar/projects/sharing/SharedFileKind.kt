package it.neckar.projects.sharing

/**
 * A file or directory one Gradle project publishes for another, so the coupling is a project dependency the affected-modules
 * resolver sees, or a coordinate dependency in a build of its own. An enum entry implements it: `OpenSharedFiles` lists the
 * entries of `internal/open`, `SharedFiles` of build-logic those of the monorepo. The producer calls `publishFiles`; the
 * consumer calls `resolvedFiles` and copies the files into its resources, for a test with `processTestResources { from(configuration) }`.
 */
interface SharedFileKind {
  /** The name of the entry in UpperCamelCase: `DnsZones`. */
  val name: String

  /** The value of the `Usage` and `Category` attributes: `it.neckar.shared.dns-zones` for `DnsZones`. */
  val usage: String
    get() = "it.neckar.shared." + name.replace(Regex("(?<!^)(?=[A-Z])"), "-").lowercase()

  /** The consumable configuration of the producer, part of a published module's variant name: `dnsZonesElements` for `DnsZones`. */
  val elementsConfigurationName: String
    get() = name.replaceFirstChar { it.lowercase() } + "Elements"
}
