package it.neckar.gradle.artifact

import kotlin.jvm.JvmInline

/**
 * The identity `in-<SHA-256>` of an artifact over [ArtifactInputs.serialization]; a fat jar carries it in [ResourcePath], an image as tag.
 * The hash stays unshortened: a shortened one would let two different input sets share a key.
 */
@JvmInline
value class ArtifactKey(val sha256: Sha256) {
  /** `in-<sha256>`, the form in the lock file, the fat jar and the image tag. */
  val value: String
    get() = Prefix + sha256.value

  override fun toString(): String {
    return value
  }

  companion object {
    const val Prefix: String = "in-"

    val Pattern: Regex = Regex("^$Prefix" + Sha256.Pattern.pattern.removePrefix("^"))

    /** The key [text] spells as `in-<sha256>`. */
    fun parse(text: String): ArtifactKey {
      require(Pattern.matches(text)) { "Artifact key must match ${Pattern.pattern}: <$text>" }
      return ArtifactKey(Sha256(text.removePrefix(Prefix)))
    }

    /** The fat jar entry holding the key; `VersionInformation` reads it at runtime. */
    const val ResourcePath: String = "META-INF/app-artifact-key.properties"

    /** The property in [ResourcePath]. */
    const val PropertyName: String = "artifactKey"
  }
}
