package it.neckar.gradle.html

/**
 * A character position in an HTML document.
 */
@JvmInline
internal value class DocumentOffset(val index: Int) : Comparable<DocumentOffset> {
  init {
    require(index >= 0) { "a document offset is not negative but was $index" }
  }

  override fun compareTo(other: DocumentOffset): Int = index.compareTo(other.index)
}
