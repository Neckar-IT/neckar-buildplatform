package it.neckar.gradle.artifact

import java.io.InputStream
import kotlin.jvm.JvmInline

/**
 * The SHA-256 of a file. A source file read by [of] counts with its real content: a Git-LFS pointer yields its `oid sha256:`, so a
 * checkout with and without LFS smudge produces the same key of a bake image. A file read by [ofOwnBytes] counts with its own bytes.
 */
@JvmInline
value class FileContentHash(val value: String) {
  init {
    require(Sha256HexPattern.matches(value)) { "File content hash must be 64 lowercase hex digits: <$value>" }
  }

  override fun toString(): String {
    return value
  }

  companion object {
    /** Git LFS writes pointers well below this size; a larger file is never read as one. */
    private const val MaxLfsPointerSize: Int = 1024

    private const val LfsPointerFirstLine: String = "version https://git-lfs.github.com/spec/v1"

    private val LfsOidLine: Regex = Regex("^oid sha256:([0-9a-f]{64})$", RegexOption.MULTILINE)

    /** The hash of the bytes in [stream], read in blocks: a Git-LFS pointer counts as itself, as in an artifact a build assembled. */
    fun ofOwnBytes(stream: InputStream): FileContentHash {
      return FileContentHash(sha256Hex(stream))
    }

    fun of(content: ByteArray): FileContentHash {
      return FileContentHash(lfsPointerOidOrNull(content) ?: sha256Hex(content))
    }

    private fun lfsPointerOidOrNull(content: ByteArray): String? {
      if (content.size > MaxLfsPointerSize) {
        return null
      }
      val text = content.toString(Charsets.UTF_8)
      if (text.startsWith("$LfsPointerFirstLine\n").not()) {
        return null
      }
      return LfsOidLine.find(text)?.groupValues?.get(1)
        ?: throw IllegalArgumentException("Git-LFS pointer without an <oid sha256:> line: <$text>")
    }
  }
}
