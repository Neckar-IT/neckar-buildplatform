package it.neckar.gradle.artifact

import kotlin.jvm.JvmInline

/**
 * The identity `in-<SHA-256>` of an artifact over [ArtifactInputs.serialization]; a fat jar carries it in [ResourcePath], an image as tag.
 * The hash stays unshortened: a shortened one would let two different input sets share a key.
 */
@JvmInline
value class ArtifactKey(val value: String) {
  init {
    require(Pattern.matches(value)) { "Artifact key must match ${Pattern.pattern}: <$value>" }
  }

  override fun toString(): String {
    return value
  }

  companion object {
    const val Prefix: String = "in-"

    val Pattern: Regex = Regex("^$Prefix" + Sha256HexPattern.pattern.removePrefix("^"))

    /** The fat jar entry holding the key; `VersionInformation` reads it at runtime. */
    const val ResourcePath: String = "META-INF/app-artifact-key.properties"

    /** The property in [ResourcePath]. */
    const val PropertyName: String = "artifactKey"
  }
}
