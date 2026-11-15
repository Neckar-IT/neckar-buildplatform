package it.neckar.open.generated

/**
 * The comment syntax of the format a generator writes. A format with none — JSON, TSV, binaries —
 * gets no marker.
 */
enum class GeneratedFileCommentSyntax(
  val linePrefix: String,

  /** Empty where the comment runs to the end of the line. */
  val lineSuffix: String,
) {
  /** Kotlin, TypeScript, TSX, JavaScript, JSON5. */
  DoubleSlash("//", ""),

  /** Shell, YAML, env files, properties, plain text, `authorized_keys`. */
  NumberSign("#", ""),

  /** HTML, SVG, XML, Markdown. */
  XmlComment("<!--", " -->"),
  ;

  companion object {
    /**
     * The syntax for [fileName], a bare name without directory; null for every name outside the
     * table, JSON and TSV among them.
     */
    fun forFileName(fileName: String): GeneratedFileCommentSyntax? {
      return when {
        fileName == "authorized_keys" || fileName == ".nvmrc" -> NumberSign
        else -> when (fileName.substringAfterLast('.', missingDelimiterValue = "")) {
          "kt", "kts", "ts", "tsx", "js", "mjs", "json5" -> DoubleSlash
          "sh", "bash", "env", "yml", "yaml", "conf", "properties", "txt" -> NumberSign
          "html", "svg", "xml", "md" -> XmlComment
          else -> null
        }
      }
    }
  }
}
