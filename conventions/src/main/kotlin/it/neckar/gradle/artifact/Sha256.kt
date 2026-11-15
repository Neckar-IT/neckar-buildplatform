package it.neckar.gradle.artifact

import java.io.InputStream
import java.security.MessageDigest
import java.util.HexFormat

/** A SHA-256 as the fingerprint writes it: 64 lowercase hex digits. */
internal val Sha256HexPattern: Regex = Regex("^[0-9a-f]{64}$")

internal fun sha256Hex(bytes: ByteArray): String {
  return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
}

/** [sha256Hex] of everything [stream] holds, read in blocks. */
internal fun sha256Hex(stream: InputStream): String {
  val digest = MessageDigest.getInstance("SHA-256")
  val buffer = ByteArray(BlockSize)
  while (true) {
    val read = stream.read(buffer)
    if (read < 0) break
    digest.update(buffer, 0, read)
  }
  return HexFormat.of().formatHex(digest.digest())
}

private const val BlockSize: Int = 64 * 1024
