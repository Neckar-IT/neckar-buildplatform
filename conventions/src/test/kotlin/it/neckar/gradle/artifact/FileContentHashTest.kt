package it.neckar.gradle.artifact

import assertk.*
import assertk.assertions.*
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.HexFormat

class FileContentHashTest {
  @Test
  fun `a regular file hashes to its SHA-256`() {
    assertThat(FileContentHash.of("hello\n".toByteArray()).value)
      .isEqualTo("5891b5b522d5df086d0ff0b110fbd9d21bb4fc7163af34d08286a2e846f6be03")
  }

  @Test
  fun `an LFS pointer and the smudged file hash alike`() {
    val smudged = ByteArray(4096) { (it % 251).toByte() }
    val oid = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(smudged))
    val pointer = """
      |version https://git-lfs.github.com/spec/v1
      |oid sha256:$oid
      |size ${smudged.size}
      |
    """.trimMargin().toByteArray()

    assertThat(FileContentHash.of(pointer)).isEqualTo(FileContentHash.of(smudged))
  }

  @Test
  fun `an LFS pointer without an oid fails`() {
    assertFailure { FileContentHash.of("version https://git-lfs.github.com/spec/v1\nsize 12\n".toByteArray()) }
      .isInstanceOf<IllegalArgumentException>()
  }

  @Test
  fun `text that only mentions LFS is hashed as a regular file`() {
    val text = "see\nversion https://git-lfs.github.com/spec/v1\noid sha256:${"a".repeat(64)}\nsize 1\n".toByteArray()

    assertThat(FileContentHash.of(text).value).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text)))
  }
}
