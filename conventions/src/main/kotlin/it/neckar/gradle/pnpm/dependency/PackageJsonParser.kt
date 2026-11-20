package it.neckar.gradle.pnpm.dependency

import it.neckar.gradle.requireFileExists
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.gradle.api.GradleException
import java.io.File

/**
 * Represents an npm package name.
 */
@JvmInline
value class NpmPackageName(val name: String) {
  override fun toString(): String = name
}

/**
 * Parses package.json files to extract package metadata and workspace dependencies.
 */
class PackageJsonParser {
  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  /** Reads and parses [packageJsonFile]; every value is then taken from the result. */
  fun parse(packageJsonFile: File): ParsedPackageJson {
    packageJsonFile.requireFileExists { "No package.json at ${it?.absolutePath}" }

    return parse(packageJsonFile.readText(), packageJsonFile)
  }

  /** Parses [text], read from [source] by the caller — through a Gradle provider at configuration time. */
  fun parse(text: String, source: File): ParsedPackageJson {
    val rootElement = try {
      json.parseToJsonElement(text)
    } catch (e: SerializationException) {
      // The kotlinx message names an offset and a path, never the file — one of fifty manifests.
      throw GradleException("Malformed package.json at ${source.absolutePath}: ${e.message}", e)
    }

    return ParsedPackageJson(source, rootElement.asObject(source, "The manifest"))
  }
}

/**
 * A package.json read and parsed once. Every value is derived here and not on each access, so a
 * caller may read the same one repeatedly.
 *
 * A section of the wrong JSON type fails naming [source] and the section — kotlinx reports neither.
 */
class ParsedPackageJson internal constructor(private val source: File, jsonObject: JsonObject) {
  private val scripts: JsonObject? = jsonObject["scripts"]?.asObject(source, "'scripts'")

  /** Null where the manifest declares no `name`; whether that is a defect is the caller's to decide. */
  val name: NpmPackageName? = jsonObject["name"]?.asString(source, "'name'")?.let { NpmPackageName(it) }

  val scriptNames: Set<String> = scripts?.keys.orEmpty()

  /** Dependencies whose version specifier starts with `workspace:`, by section. */
  val workspaceDependencies: WorkspaceDependencies = WorkspaceDependencies(
    dependencies = jsonObject.workspaceDependenciesOf(source, "dependencies"),
    devDependencies = jsonObject.workspaceDependenciesOf(source, "devDependencies"),
  )

  /** Every package of `dependencies` and `devDependencies`, whatever its version specifier. */
  val dependencyNames: Set<NpmPackageName> by lazy {
    // Lazy: every configuration parses the manifests, only verifyAstroTypecheck asks this.
    listOf("dependencies", "devDependencies").flatMap { section ->
      jsonObject[section]?.asObject(source, "'$section'")?.keys.orEmpty().map { NpmPackageName(it) }
    }.toSet()
  }

  /**
   * Every dependency whose version is written into the manifest itself, as `section name=version`.
   * `catalog:` and `workspace:` name no version, `link:`, `file:` and `portal:` point at a local path.
   */
  val hardcodedVersionDependencies: List<String> by lazy {
    // Lazy: every configuration parses the manifests, only verifyPnpmWorkspaceDependencies asks this.
    listOf("dependencies", "devDependencies", "optionalDependencies", "peerDependencies").flatMap { section ->
      jsonObject[section]?.asObject(source, "'$section'")?.entries.orEmpty().mapNotNull { (packageName, versionElement) ->
        val version = versionElement.asString(source, "the version of '$packageName' in '$section'")
        "$section $packageName=$version".takeIf { nonVersionSpecifierPrefixes.none { version.startsWith(it) } }
      }
    }
  }

  /** Every path `main`, `types` and `exports` name, in manifest order. */
  val entryPoints: List<PackageEntryPoint> by lazy {
    listOf("main", "types").mapNotNull { field ->
      jsonObject[field]?.let { PackageEntryPoint("\"$field\"", it.asString(source, "'$field'")) }
    } + jsonObject["exports"]?.exportEntryPoints(source, "\"exports\"").orEmpty()
  }

  fun script(scriptName: String): String? = scripts?.get(scriptName)?.asString(source, "script '$scriptName'")
}

/**
 * A path the manifest exposes to importers, with the [field] it sits in: `"main"`, or
 * `"exports" > "./vite-plugin" > "import"` for a nested condition.
 */
data class PackageEntryPoint(val field: String, val path: String)

/** `null` is how `exports` hides a subpath; it names no path. */
private fun JsonElement.exportEntryPoints(source: File, field: String): List<PackageEntryPoint> = when (this) {
  is JsonNull -> emptyList()
  is JsonPrimitive -> listOf(PackageEntryPoint(field, asString(source, field)))
  is JsonArray -> flatMap { it.exportEntryPoints(source, field) }
  is JsonObject -> entries.flatMap { (key, value) -> value.exportEntryPoints(source, "$field > \"$key\"") }
}

private val nonVersionSpecifierPrefixes: List<String> = listOf("catalog:", "workspace:", "link:", "file:", "portal:")

private fun JsonObject.workspaceDependenciesOf(source: File, section: String): List<NpmPackageName> {
  val deps = this[section]?.asObject(source, "'$section'") ?: return emptyList()

  return deps.entries
    .filter { (packageName, versionElement) ->
      versionElement.asString(source, "the version of '$packageName' in '$section'").startsWith("workspace:")
    }
    .map { (packageName, _) -> NpmPackageName(packageName) }
}

private fun JsonElement.asObject(source: File, what: String): JsonObject {
  return this as? JsonObject
    ?: throw GradleException("$what in ${source.absolutePath} must be an object, but is ${this::class.simpleName}")
}

private fun JsonElement.asString(source: File, what: String): String {
  return (this as? JsonPrimitive)?.takeIf { it.isString }?.content
    ?: throw GradleException("$what in ${source.absolutePath} must be a string, but is $this")
}

/**
 * Holds workspace dependencies separated by type.
 */
data class WorkspaceDependencies(
  /** Runtime dependencies from the "dependencies" section */
  val dependencies: List<NpmPackageName>,
  /** Development dependencies from the "devDependencies" section */
  val devDependencies: List<NpmPackageName>,
) {
  /** Both sections in one list, each package once. */
  val all: List<NpmPackageName> = (dependencies + devDependencies).distinct()
}
