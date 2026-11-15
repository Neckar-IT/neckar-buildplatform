package it.neckar.gradle.artifact

/**
 * Everything an artifact is built from; [key] is `in-` and the SHA-256 over [serialization]. Only the collector differs between the kinds
 * of artifact: `docs/gradle/artifact-key.md`.
 */
data class ArtifactInputs(val inputs: List<ArtifactInput>) {
  /** One line per input; the encoding is ours, so no library update moves a key. */
  val serialization: String = inputs.joinToString(separator = "") { it.toLine() + "\n" }

  val key: ArtifactKey = ArtifactKey(ArtifactKey.Prefix + sha256Hex(serialization.toByteArray(Charsets.UTF_8)))

  companion object {
    /** The inputs [serialization] holds, the text [ArtifactKeyReport] writes. */
    fun parse(serialization: String): ArtifactInputs {
      return ArtifactInputs(serialization.lines().filter { it.isNotEmpty() }.map { ArtifactInput.parse(it) })
    }
  }
}

/** One input of an artifact, one line of [ArtifactInputs.serialization]; a label is a relative path or a file name, never absolute. */
sealed interface ArtifactInput {
  fun toLine(): String

  /** A value that is no file, e.g. a build argument, the base image or the version of a tool. */
  data class Setting(val name: String, val value: String) : ArtifactInput {
    init {
      requireName(name)
      requirePrintable(name, value)
    }

    override fun toLine(): String = "$SettingPrefix$name=$value"
  }

  /** Another artifact this one builds on, e.g. the stage a bake target copies from. */
  data class Reference(val name: String, val key: ArtifactKey) : ArtifactInput {
    init {
      requireName(name)
    }

    override fun toLine(): String = "$ReferencePrefix$name=$key"
  }

  /** What the artifact contains, as opposed to how it is built. */
  sealed interface Content : ArtifactInput

  /** A directory; an absent one counts as an empty one. */
  data class Directory(val label: String) : Content {
    init {
      requireLabel(label)
    }

    override fun toLine(): String = "$DirectoryPrefix$label"
  }

  /** A file and the SHA-256 of its content. */
  data class File(val label: String, val contentHash: FileContentHash) : Content {
    init {
      requireLabel(label)
    }

    override fun toLine(): String = "$FilePrefix$label=$contentHash"
  }

  companion object {
    private const val SettingPrefix: String = "setting:"
    private const val ReferencePrefix: String = "ref:"
    private const val DirectoryPrefix: String = "dir:"
    private const val FilePrefix: String = "file:"

    /** The input [line] holds; a name ends at its first `=`, a label at its last. */
    fun parse(line: String): ArtifactInput {
      return when {
        line.startsWith(SettingPrefix) -> line.removePrefix(SettingPrefix).let { Setting(it.substringBefore('='), it.substringAfter('=')) }
        line.startsWith(ReferencePrefix) -> line.removePrefix(ReferencePrefix).let { Reference(it.substringBefore('='), ArtifactKey(it.substringAfter('='))) }
        line.startsWith(DirectoryPrefix) -> Directory(line.removePrefix(DirectoryPrefix))
        line.startsWith(FilePrefix) -> line.removePrefix(FilePrefix).let { File(it.substringBeforeLast('='), FileContentHash(it.substringAfterLast('='))) }
        else -> throw IllegalArgumentException("Unknown artifact input line: <$line>")
      }
    }

    private fun requireName(name: String) {
      requirePrintable(name, name)
      require(('=' in name).not()) { "Input name <$name> holds a '='" }
    }

    private fun requireLabel(label: String) {
      requirePrintable(label, label)
      require(label.startsWith("/").not()) { "Input label <$label> is an absolute path" }
    }

    /** [text] of the input [name] stays one line readable by `diff`: no line break, no other control character. */
    private fun requirePrintable(name: String, text: String) {
      require(text.none { it.isISOControl() }) { "Input <$name> holds a control character: <$text>" }
    }
  }
}
