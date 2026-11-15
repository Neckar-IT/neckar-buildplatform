package it.neckar.gradle

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Appends every write to [file] and closes the file right after it. An exec spec leaves its `standardOutput` open, so
 * a stream holding the file open would leak one handle per task run.
 */
internal class FileAppendingOutputStream(private val file: File) : OutputStream() {
  override fun write(byte: Int) {
    FileOutputStream(file, true).use { it.write(byte) }
  }

  override fun write(bytes: ByteArray, offset: Int, length: Int) {
    FileOutputStream(file, true).use { it.write(bytes, offset, length) }
  }
}

/**
 * Returns what a [FileAppendingOutputStream] wrote to this file. The stream creates the file on its first write, so a
 * missing file means the process wrote nothing.
 */
internal fun File.readCapturedOutput(): String {
  return if (isFile) readText(Charsets.UTF_8) else ""
}
