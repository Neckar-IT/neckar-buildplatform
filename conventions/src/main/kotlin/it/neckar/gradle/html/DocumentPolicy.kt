package it.neckar.gradle.html

/**
 * The Content Security Policy one `<meta http-equiv="content-security-policy">` of a document declares.
 */
internal class DocumentPolicy(
  val scriptRestriction: ScriptRestriction,

  /** Where the `<meta>` element stands; a browser applies the policy only to what follows it. */
  val offset: DocumentOffset,
) {
  companion object {
    private val Whitespace: Regex = Regex("""\s+""")

    /** [content] is the `content` attribute with its character references decoded. */
    fun parse(content: String, offset: DocumentOffset): DocumentPolicy {
      val declarations: List<List<String>> = content.split(';')
        .map { declaration -> declaration.trim().split(Whitespace) }
        .filter { tokens -> tokens.first().isNotEmpty() }

      val sources: List<String>? = ScriptElementDirective.entries.firstNotNullOfOrNull { directive ->
        declarations.firstOrNull { tokens -> tokens.first().equals(directive.directiveName, ignoreCase = true) }?.drop(1)
      }

      val scriptRestriction: ScriptRestriction = when (sources) {
        null -> ScriptRestriction.Unrestricted
        else -> ScriptRestriction.Restricted(sources.map { token -> CspSource.parse(token) })
      }

      return DocumentPolicy(scriptRestriction = scriptRestriction, offset = offset)
    }
  }
}
