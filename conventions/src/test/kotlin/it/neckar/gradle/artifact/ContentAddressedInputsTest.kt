package it.neckar.gradle.artifact

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ContentAddressedInputsTest {

  @TempDir
  lateinit var directory: File

  @Test
  fun `two checkouts with the same parts have the same key`() {
    val first = layer("checkout-a", "index.html" to "hello")
    val second = layer("checkout-b", "index.html" to "hello")

    assertThat(key(Settings, second)).isEqualTo(key(Settings, first))
  }

  @Test
  fun `a changed file, a renamed file and a changed setting change the key`() {
    val key = key(Settings, layer("a", "index.html" to "hello"))

    assertThat(key(Settings, layer("b", "index.html" to "hello!"))).isNotEqualTo(key)
    assertThat(key(Settings, layer("c", "start.html" to "hello"))).isNotEqualTo(key)
    assertThat(key(listOf(ArtifactInput.Setting("from", "nginx:in-${"b".repeat(64)}")), layer("d", "index.html" to "hello"))).isNotEqualTo(key)
  }

  @Test
  fun `the jar key ignores the order of the entries and the key entry`() {
    val ordered = jar("ordered.jar", listOf("a.class" to "A", "b.class" to "B"))
    val reversed = jar("reversed.jar", listOf("b.class" to "B", "a.class" to "A", ArtifactKey.ResourcePath to "artifactKey=in-${"c".repeat(64)}\n"))

    assertThat(ContentAddressedInputs.ofJar(reversed).key).isEqualTo(ContentAddressedInputs.ofJar(ordered).key)
  }

  @Test
  fun `a changed class changes the jar key`() {
    val original = jar("original.jar", listOf("a.class" to "A"))
    val changed = jar("changed.jar", listOf("a.class" to "A2"))

    assertThat(ContentAddressedInputs.ofJar(changed).key).isNotEqualTo(ContentAddressedInputs.ofJar(original).key)
  }

  @Test
  fun `the serialization names each part by its label and hashes to the key`() {
    val inputs = ContentAddressedInputs.ofImage(Settings, listOf(ArtifactPart("dist", layer("e", "index.html" to "hello")), ArtifactPart("build/classes/java/main", directory.resolve("absent"))))

    assertThat(inputs.serialization).isEqualTo(
      "setting:from=nginx:in-${"a".repeat(64)}\n" +
        "dir:dist\n" +
        "file:dist/index.html=2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824\n" +
        "dir:build/classes/java/main\n"
    )
    assertThat(inputs.key.value).isEqualTo("in-" + sha256(inputs.serialization))
  }

  @Test
  fun `an absent directory has the key of an empty one`() {
    val empty = directory.resolve("empty").apply { mkdirs() }

    assertThat(key(Settings, directory.resolve("absent"))).isEqualTo(key(Settings, empty))
  }

  @Test
  fun `settings are sorted by name and the files of a directory by path`() {
    val inputs = ContentAddressedInputs.ofImage(
      listOf(ArtifactInput.Setting("z", "1"), ArtifactInput.Setting("a", "2")),
      listOf(ArtifactPart("dist", layer("f", "b.html" to "b", "a.html" to "a"))),
    )

    assertThat(inputs.serialization.lines().map { it.substringBefore('=') }).isEqualTo(
      listOf("setting:a", "setting:z", "dir:dist", "file:dist/a.html", "file:dist/b.html", "")
    )
  }

  @Test
  fun `a Git-LFS pointer in an assembled part counts with its own bytes`() {
    val pointer = "version https://git-lfs.github.com/spec/v1\noid sha256:${"d".repeat(64)}\nsize 12\n"

    val inputs = ContentAddressedInputs.ofImage(emptyList(), listOf(ArtifactPart("model.bin", directory.resolve("model.bin").apply { writeText(pointer) })))

    assertThat(inputs.serialization).isEqualTo("file:model.bin=${sha256(pointer)}\n")
  }

  @Test
  fun `an empty subdirectory is part of the key`() {
    val plain = layer("g", "index.html" to "hello")
    val withEmptyDirectory = layer("h", "index.html" to "hello").apply { resolve("cache").mkdirs() }

    assertThat(key(Settings, withEmptyDirectory)).isNotEqualTo(key(Settings, plain))
    assertThat(ContentAddressedInputs.ofImage(Settings, listOf(ArtifactPart("dist", withEmptyDirectory))).serialization.lines()).contains("dir:dist/cache")
  }

  private fun key(settings: List<ArtifactInput.Setting>, root: File): ArtifactKey {
    return ContentAddressedInputs.ofImage(settings, listOf(ArtifactPart("dist", root))).key
  }

  private fun sha256(text: String): String {
    return java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
  }

  private fun layer(name: String, vararg files: Pair<String, String>): File {
    val root = directory.resolve(name).apply { mkdirs() }
    files.forEach { (path, content) -> root.resolve(path).writeText(content) }
    return root
  }

  private fun jar(name: String, entries: List<Pair<String, String>>): File {
    val jar = directory.resolve(name)
    ZipOutputStream(jar.outputStream()).use { zip ->
      entries.forEach { (path, content) ->
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray())
        zip.closeEntry()
      }
    }
    return jar
  }

  private companion object {
    val Settings: List<ArtifactInput.Setting> = listOf(ArtifactInput.Setting("from", "nginx:in-${"a".repeat(64)}"))
  }
}
