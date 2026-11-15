package it.neckar.gradle

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.messageContains
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Covers the file helpers of `SvgFiles.kt` — the input side of the icon generation
 * ([listSvgFilesRecursively], [baseNames]) and the directory listing it builds on.
 */
class SvgFilesTest {

  @TempDir
  lateinit var tempDir: File

  /** The helpers under test select by file name, so the content is irrelevant. */
  private fun createFile(relativePath: String) {
    val file = tempDir.resolve(relativePath)
    file.parentFile.mkdirs()
    file.writeText("content")
  }

  @Test
  fun `listFiles returns the matching files sorted by name`() {
    createFile("zebra.svg")
    createFile("apple.svg")
    createFile("notes.txt")

    assertThat(tempDir.listFiles(".svg").map { it.name }).containsExactly("apple.svg", "zebra.svg")
  }

  @Test
  fun `listFiles matches the suffix case insensitively`() {
    createFile("Upper.SVG")

    assertThat(tempDir.listFiles(".svg").map { it.name }).containsExactly("Upper.SVG")
  }

  /**
   * Only the file name is lowercased, not the suffix argument — an upper-case suffix silently
   * matches nothing instead of failing, so callers have to pass it lower-case.
   */
  @Test
  fun `listFiles expects the suffix in lower case`() {
    createFile("icon.svg")

    assertThat(tempDir.listFiles(".SVG")).isEmpty()
  }

  @Test
  fun `listFiles returns an empty list when nothing matches`() {
    createFile("notes.txt")

    assertThat(tempDir.listFiles(".svg")).isEmpty()
  }

  @Test
  fun `listFiles fails for a directory that does not exist`() {
    assertFailure { tempDir.resolve("missing").listFiles(".svg") }
      .messageContains("Could not find source folder")
  }

  @Test
  fun `listSvgFilesRecursively descends into subdirectories`() {
    createFile("root.svg")
    createFile("nested/child.svg")
    createFile("nested/deeper/grandchild.svg")
    createFile("nested/ignored.txt")

    assertThat(tempDir.listSvgFilesRecursively().map { it.name }.sorted())
      .containsExactly("child.svg", "grandchild.svg", "root.svg")
  }

  @Test
  fun `baseNames strips the extension`() {
    assertThat(listOf(File("/tmp/icon.svg"), File("/tmp/archive.tar.gz"), File("/tmp/plain")).baseNames())
      .containsExactly("icon", "archive.tar", "plain")
  }

  @Test
  fun `child resolves a relative path below this file`() {
    assertThat(tempDir.child("sub/file.txt")).isEqualTo(File(tempDir, "sub/file.txt"))
  }
}
