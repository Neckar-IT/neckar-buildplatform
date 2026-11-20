package it.neckar.gradle.html

import java.io.File

/** The text of one HTML file — what [InlineScriptScanner] judges. */
@JvmInline
internal value class HtmlDocument(val text: String) {
  companion object {
    fun read(file: File): HtmlDocument = HtmlDocument(file.readText())
  }
}
