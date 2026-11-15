package it.neckar.gradle

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FileAppendingOutputStreamTest {

  @TempDir
  lateinit var directory: File

  @Test
  fun `every write lands at the end of the file`() {
    val file = directory.resolve("stdout.txt")
    val stream = FileAppendingOutputStream(file)

    stream.write("[{".toByteArray())
    stream.write('}'.code)
    stream.write("x]x".toByteArray(), 1, 1)

    assertThat(file.readText()).isEqualTo("[{}]")
  }

  @Test
  fun `a file the stream never wrote reads as empty output`() {
    assertThat(directory.resolve("stderr.txt").readCapturedOutput()).isEqualTo("")
  }
}
