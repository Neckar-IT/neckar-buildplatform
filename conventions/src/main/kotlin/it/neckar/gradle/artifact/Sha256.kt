package it.neckar.gradle.artifact

import java.io.InputStream
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.jvm.JvmInline

/** A SHA-256 as 64 lowercase hex digits, the form of the fingerprint and of the checksum asset of a GitHub release. */
@JvmInline
value class Sha256(val value: String) {
  init {
    require(Pattern.matches(value)) { "A SHA-256 is 64 lowercase hex digits: <$value>" }
  }

  override fun toString(): String {
    return value
  }

  companion object {
    val Pattern: Regex = Regex("^[0-9a-f]{64}$")

    private const val BlockSize: Int = 64 * 1024

    /** The SHA-256 of [text] in UTF-8. */
    fun of(text: String): Sha256 {
      return of(text.toByteArray(Charsets.UTF_8))
    }

    fun of(bytes: ByteArray): Sha256 {
      return Sha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)))
    }

    /** The SHA-256 of everything [stream] holds, read in blocks. */
    fun of(stream: InputStream): Sha256 {
      val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
      val buffer = ByteArray(BlockSize)
      while (true) {
        val read: Int = stream.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
      }
      return Sha256(HexFormat.of().formatHex(digest.digest()))
    }
  }
}
