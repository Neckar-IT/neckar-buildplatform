package it.neckar.gradle.shadow

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class FatJarDuplicateEntriesTest {
  @Test
  fun `a jar without duplicates yields no findings`(@TempDir tempDir: Path) {
    val fatJar = tempDir.writeJar(
      "META-INF/LICENSE" to "Apache License",
      "it/neckar/Demo.class" to "class bytes",
    )

    assertThat(FatJarDuplicateEntries.find(fatJar)).isEmpty()
  }

  @Test
  fun `repeated paths are reported with their occurrence count`(@TempDir tempDir: Path) {
    val fatJar = tempDir.writeJar(
      "META-INF/LICENSE" to "Apache License",
      "META-INF/LICENSE" to "MIT License",
      "META-INF/LICENSE" to "BSD License",
      "org/jetbrains/annotations/NotNull.class" to "from org.jetbrains:annotations",
      "org/jetbrains/annotations/NotNull.class" to "from com.intellij:annotations",
      "it/neckar/Demo.class" to "class bytes",
    )

    assertThat(FatJarDuplicateEntries.find(fatJar)).isEqualTo(
      mapOf(
        "META-INF/LICENSE" to 3,
        "org/jetbrains/annotations/NotNull.class" to 2,
      )
    )
  }

  @Test
  fun `directory entries are not duplicates`(@TempDir tempDir: Path) {
    val fatJar = tempDir.resolve("directories.jar").toFile()
    ZipOutputStream(fatJar).use { zipOutputStream ->
      repeat(2) {
        zipOutputStream.putNextEntry(ZipEntry("META-INF/"))
        zipOutputStream.closeEntry()
      }
    }

    assertThat(FatJarDuplicateEntries.find(fatJar)).isEmpty()
  }

  @Test
  fun `a jar without duplicates passes the verification`(@TempDir tempDir: Path) {
    val fatJar = tempDir.writeJar(
      "META-INF/LICENSE" to "Apache License",
      "it/neckar/Demo.class" to "class bytes",
    )

    FatJarDuplicateEntries.verify(fatJar, ":demos:demos-fx:clean")
  }

  @Test
  fun `the failure names the clean task of the module that built the jar`(@TempDir tempDir: Path) {
    val fatJar = tempDir.writeJar(
      "tank/waves.png" to "the module's own copy",
      "tank/waves.png" to "a dependency's copy",
    )

    val failure = assertThrows<GradleException> {
      FatJarDuplicateEntries.verify(fatJar, ":demos:demos-fx:clean")
    }

    assertThat(failure.message.orEmpty()).contains("./gradlew :demos:demos-fx:clean")
  }

  @Test
  fun `the description lists the most frequent path first`() {
    val description = FatJarDuplicateEntries.describe(
      mapOf(
        "org/jetbrains/annotations/NotNull.class" to 2,
        "META-INF/LICENSE" to 17,
        "META-INF/NOTICE" to 2,
      )
    )

    assertThat(description.lines()).containsExactly(
      "  META-INF/LICENSE (17 times)",
      "  META-INF/NOTICE (2 times)",
      "  org/jetbrains/annotations/NotNull.class (2 times)",
    )
  }

  /**
   * Writes a jar with the given entries. Ant's [ZipOutputStream] is used because
   * `java.util.zip.ZipOutputStream` rejects duplicate names outright - the very situation under test.
   * Shadow writes its fat jars with the same Ant implementation.
   */
  private fun Path.writeJar(vararg entries: Pair<String, String>): File {
    val fatJar = resolve("fat.jar").toFile()

    ZipOutputStream(fatJar).use { zipOutputStream ->
      entries.forEach { (entryName, content) ->
        zipOutputStream.putNextEntry(ZipEntry(entryName))
        zipOutputStream.write(content.toByteArray())
        zipOutputStream.closeEntry()
      }
    }

    return fatJar
  }
}
