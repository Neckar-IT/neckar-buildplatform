package it.neckar.open.generated

import java.io.File

/** Writes [content] with [marker] placed by [GeneratedFileMarker.prependTo]. */
fun File.writeGeneratedFile(content: String, marker: GeneratedFileMarker, syntax: GeneratedFileCommentSyntax) {
  writeText(marker.prependTo(content, syntax))
}
