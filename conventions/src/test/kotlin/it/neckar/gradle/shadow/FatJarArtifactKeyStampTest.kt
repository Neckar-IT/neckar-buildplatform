package it.neckar.gradle.shadow

import assertk.assertThat
import assertk.assertions.isEqualTo
import it.neckar.gradle.artifact.ArtifactKey
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.MessageDigest
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.Duration.Companion.milliseconds

class FatJarArtifactKeyStampTest {

  @TempDir
  lateinit var directory: File

  @Test
  fun `the stamped jar names the artifact key`() {
    val jar = packedJar("app.jar")

    FatJarArtifactKeyStamp.write(jar, Key)

    val text = JarFile(jar).use { it.getInputStream(it.getEntry(ArtifactKey.ResourcePath)).readBytes().decodeToString() }
    assertThat(text).isEqualTo("artifactKey=in-3668efd5ddda0006ec4e4a1b2c3d4e5f3668efd5ddda0006ec4e4a1b2c3d4e5f\n")
  }

  @Test
  fun `two stamps of the same jar at different times have the same bytes`() {
    val first = packedJar("first.jar")
    val second = packedJar("second.jar")

    FatJarArtifactKeyStamp.write(first, Key)
    // Longer than the 2 s resolution of a zip entry time.
    Thread.sleep(2100.milliseconds.inWholeMilliseconds)
    FatJarArtifactKeyStamp.write(second, Key)

    assertThat(sha256(second)).isEqualTo(sha256(first))
  }

  @Test
  fun `a second stamp replaces the first`() {
    val jar = packedJar("restamped.jar")
    FatJarArtifactKeyStamp.write(jar, ArtifactKey("in-" + "1".repeat(64)))

    FatJarArtifactKeyStamp.write(jar, Key)

    val entries = JarFile(jar).use { file -> file.entries().asSequence().map { it.name }.toList() }
    assertThat(entries.count { it == ArtifactKey.ResourcePath }).isEqualTo(1)
    assertThat(JarFile(jar).use { it.getInputStream(it.getEntry(ArtifactKey.ResourcePath)).readBytes().decodeToString() }).isEqualTo("artifactKey=$Key\n")
  }

  private fun packedJar(name: String): File {
    val jar = directory.resolve(name)
    // Directory entries as a Shadow jar holds them: the stamp rewrites the jar and keeps them.
    ZipOutputStream(jar.outputStream()).use { zip ->
      zip.putNextEntry(ZipEntry("META-INF/").apply { time = 0L })
      zip.closeEntry()
      zip.putNextEntry(ZipEntry("META-INF/MANIFEST.MF").apply { time = 0L })
      zip.write("Manifest-Version: 1.0\n".toByteArray())
      zip.closeEntry()
      zip.putNextEntry(ZipEntry("app/").apply { time = 0L })
      zip.closeEntry()
      zip.putNextEntry(ZipEntry("app/Main.class").apply { time = 0L })
      zip.write(byteArrayOf(1, 2, 3))
      zip.closeEntry()
    }
    return jar
  }

  private fun sha256(file: File): String {
    return MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
  }

  private companion object {
    val Key: ArtifactKey = ArtifactKey("in-3668efd5ddda0006ec4e4a1b2c3d4e5f3668efd5ddda0006ec4e4a1b2c3d4e5f")
  }
}
