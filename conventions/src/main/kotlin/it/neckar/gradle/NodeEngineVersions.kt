package it.neckar.gradle

import org.gradle.api.Project

/**
 * The Node.js (`"node"`) or pnpm (`"pnpm"`) version of the `engines` block of the root `package.json`, which pnpm enforces through
 * `engineStrict`; any other alias throws, because application dependencies are read from their `package.json`.
 */
fun Project.npmVersion(alias: String): String {
  val packageJsonContent = rootProject.file("package.json").readText()
  return when (alias) {
    "node" -> parseNodeEngineVersion(packageJsonContent)
    "pnpm" -> parsePnpmEngineVersion(packageJsonContent)
    else -> throw IllegalArgumentException(
      "npmVersion('$alias') is not supported: only 'node' and 'pnpm' are read from the engines of the root package.json. " +
        "Read application dependencies from the relevant package.json directly."
    )
  }
}

private val NodeEngineRegex = Regex(""""node"\s*:\s*"([^"]+)"""")

/** Parses `engines.node` from root `package.json` content. */
fun parseNodeEngineVersion(packageJsonContent: String): String {
  val match = NodeEngineRegex.find(packageJsonContent)
    ?: throw IllegalStateException("Cannot find engines.node version in package.json")
  return match.groupValues[1]
}

private val PnpmEngineRegex = Regex(""""pnpm"\s*:\s*"([^"]+)"""")

/** Parses `engines.pnpm` from root `package.json` content. */
fun parsePnpmEngineVersion(packageJsonContent: String): String {
  val match = PnpmEngineRegex.find(packageJsonContent)
    ?: throw IllegalStateException("Cannot find engines.pnpm version in package.json")
  return match.groupValues[1]
}
