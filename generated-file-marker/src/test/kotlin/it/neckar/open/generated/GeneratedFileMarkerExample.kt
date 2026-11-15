package it.neckar.open.generated

/** One case of `generated-file-marker-examples.txt`; the file's head comment describes the format. */
internal data class GeneratedFileMarkerExample(
  val name: String,
  val fileName: String,
  val syntax: String,
  val by: String?,
  val source: String?,
  val regenerateHint: String?,
  val content: String?,
  val file: String,
) {
  /** The comment syntax of this case; null for `Annotation` and `None`. */
  val commentSyntax: GeneratedFileCommentSyntax?
    get() = GeneratedFileCommentSyntax.entries.find { it.name == syntax }

  override fun toString(): String = name

  companion object {
    private const val Absent: String = "<none>"

    /** The comment syntaxes plus `Annotation` (`@file:Generated`) and `None` (a hand-written file). */
    private val syntaxNames: List<String> = GeneratedFileCommentSyntax.entries.map { it.name } + listOf("Annotation", "None")

    fun readAll(): List<GeneratedFileMarkerExample> {
      val resource = GeneratedFileMarkerExample::class.java.getResource("/generated-file-marker-examples.txt")
      val text = requireNotNull(resource) { "generated-file-marker-examples.txt is not on the test classpath" }.readText()
      return text.lines().dropLastWhile { it.isEmpty() }
        .dropWhile { it.startsWith("=== ").not() }
        .chunkedAtCaseLines()
        .map { parse(it) }
    }

    private fun List<String>.chunkedAtCaseLines(): List<List<String>> {
      val cases = mutableListOf<MutableList<String>>()
      forEach { line ->
        if (line.startsWith("=== ")) {
          cases.add(mutableListOf())
        }
        cases.last().add(line)
      }
      return cases
    }

    private fun parse(lines: List<String>): GeneratedFileMarkerExample {
      val name = lines.first().removePrefix("=== ")
      val contentStart = lines.indexOf("--- content")
      val fileStart = lines.indexOf("--- file")
      require(fileStart > 0) { "case '$name' has no '--- file' section" }
      val headerEnd = if (contentStart > 0) contentStart else fileStart
      val header = lines.subList(1, headerEnd).map { it.substringBefore(": ") to it.substringAfter(": ") }

      fun value(key: String): String {
        return header.singleOrNull { it.first == key }?.second ?: throw IllegalArgumentException("case '$name' needs exactly one '$key:' line")
      }

      val syntax = value("syntax")
      require(syntax in syntaxNames) { "case '$name' names the syntax '$syntax', expected one of $syntaxNames" }
      val hintLines = header.filter { it.first == "hint" }.map { it.second }
      return GeneratedFileMarkerExample(
        name = name,
        fileName = value("file"),
        syntax = syntax,
        by = value("by").takeUnless { it == Absent },
        source = value("source").takeUnless { it == Absent },
        regenerateHint = hintLines.takeIf { it.isNotEmpty() }?.joinToString("\n"),
        content = if (contentStart > 0) lines.subList(contentStart + 1, fileStart).joinToString("") { "$it\n" } else null,
        file = lines.drop(fileStart + 1).joinToString("") { "$it\n" },
      )
    }
  }
}
