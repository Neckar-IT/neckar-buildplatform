package it.neckar.gradle.html

import it.neckar.gradle.requireNotNull

/**
 * Finds the inline `<script>` elements and the head's policy `<meta>` elements [VerifyNoInlineScriptsTask] judges.
 * Scanned rather than parsed: no HTML parser is on the build classpath.
 */
internal object InlineScriptScanner {
  /** An opening `<script` up to its closing `>`. Quoted values are consumed whole: they may hold a `>`. */
  private val ScriptOpeningTag: Regex = Regex("""<script\b((?:[^>"']|"[^"]*"|'[^']*')*)>""", RegexOption.IGNORE_CASE)

  private val MetaTag: Regex = Regex("""<meta\b((?:[^>"']|"[^"]*"|'[^']*')*)>""", RegexOption.IGNORE_CASE)

  /**
   * One attribute of an opening tag: a name, optionally a quoted or bare value. Anchored on the
   * separator, so the remainder of a malformed value cannot be read as the next attribute's name.
   */
  private val Attribute: Regex = Regex("""(?:^|\s)([\w:.-]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?""")

  private val ClosingTag: Regex = Regex("""</script\s*>""", RegexOption.IGNORE_CASE)

  private const val CommentStart: String = "<!--"
  private const val CommentEnd: String = "-->"

  /** The `type` values besides the classic ones ([isClassicScriptType]) whose content the browser runs, and which `script-src` therefore governs. */
  private val ExecutedScriptTypes: Set<String> = setOf("module", "importmap", "speculationrules")

  /** The character references a policy in an attribute value can carry, matched in one pass so a decoded `&` forms no second reference. */
  private val CharacterReference: Regex = Regex("""&(#[xX][0-9a-fA-F]+|#[0-9]+|apos|quot|amp);""")

  /**
   * Where the head ends, since a browser ignores a `<meta http-equiv="content-security-policy">` outside it.
   * Only the explicit tags count, which the built pages write; a head the parser closes implicitly is not seen.
   */
  private val HeadEnd: Regex = Regex("""</head\s*>|<body\b""", RegexOption.IGNORE_CASE)

  /**
   * Comments, scripts, metas and the head end are taken in document order, because each can hold the others' opening
   * sequence: a `<!--` or `</head>` in a script string would cut off what follows, and a `<meta>` there declares nothing.
   */
  fun scan(document: HtmlDocument): ScannedHtmlDocument {
    val text = document.text
    val inlineScripts = mutableListOf<InlineScript>()
    val documentPolicies = mutableListOf<DocumentPolicy>()
    var headEnded = false
    var index = 0

    while (index < text.length) {
      val commentStart = text.indexOf(CommentStart, index).takeIf { it >= 0 }
      val scriptTag = ScriptOpeningTag.find(text, index)
      val metaTag = MetaTag.find(text, index)
      val headEnd = if (headEnded) null else HeadEnd.find(text, index)

      val next: Int = listOfNotNull(commentStart, scriptTag?.range?.first, metaTag?.range?.first, headEnd?.range?.first).minOrNull() ?: break

      when (next) {
        commentStart -> {
          index = text.indexOf(CommentEnd, next + CommentStart.length)
            .let { if (it < 0) text.length else it + CommentEnd.length }
        }

        headEnd?.range?.first -> {
          headEnded = true
          index = headEnd.range.last + 1
        }

        metaTag?.range?.first -> {
          if (headEnded.not()) {
            documentPolicyOf(metaTag.groupValues[1], offset = DocumentOffset(next))?.let { documentPolicies += it }
          }
          index = metaTag.range.last + 1
        }

        else -> {
          val openingTag = scriptTag.requireNotNull { "no construct starts at $next" }
          val bodyStart = openingTag.range.last + 1
          val closingTag = ClosingTag.find(text, bodyStart)
          val body = text.substring(bodyStart, closingTag?.range?.first ?: text.length)

          val end = closingTag?.range?.last?.plus(1) ?: text.length

          val attributes: Map<String, String> = attributesOf(openingTag.groupValues[1])
          val type = attributes["type"].orEmpty().trim().lowercase()

          if (carriesCode(attributes, type, body)) {
            inlineScripts += InlineScript(
              lineNumber = text.lineNumberAt(openingTag.range.first),
              offset = DocumentOffset(openingTag.range.first),
              body = body,
              openingTagAttributes = openingTag.groupValues[1],
              type = type,
              attributeNames = attributes.keys,
              end = DocumentOffset(end),
            )
          }

          index = end
        }
      }
    }

    return ScannedHtmlDocument(inlineScripts = inlineScripts, documentPolicies = documentPolicies)
  }

  /** True when the element holds script the browser is meant to run and `script-src` blocks. */
  private fun carriesCode(attributes: Map<String, String>, type: String, body: String): Boolean {
    if (body.isBlank()) return false
    if (attributes.containsKey("src")) return false

    return type in ExecutedScriptTypes || isClassicScriptType(type)
  }

  private fun documentPolicyOf(metaTagAttributes: String, offset: DocumentOffset): DocumentPolicy? {
    val attributes: Map<String, String> = attributesOf(metaTagAttributes)
    if (attributes["http-equiv"]?.trim().equals("content-security-policy", ignoreCase = true).not()) return null

    val content: String = attributes["content"].requireNotNull { "<meta http-equiv=\"content-security-policy\"> without a content attribute" }
    return DocumentPolicy.parse(content = decodeCharacterReferences(content), offset = offset)
  }

  private fun decodeCharacterReferences(attributeValue: String): String =
    CharacterReference.replace(attributeValue) { reference ->
      when (val name = reference.groupValues[1]) {
        "apos" -> "'"
        "quot" -> "\""
        "amp" -> "&"
        else -> decodeNumericCharacterReference(name)
      }
    }

  /** [name] is a decimal or `x`-prefixed hexadecimal reference. One naming no character decodes to U+FFFD, as in the browser, so its `;` cannot split a directive. */
  private fun decodeNumericCharacterReference(name: String): String {
    val codePoint: Int = when {
      name.startsWith("#x", ignoreCase = true) -> name.drop(2).toIntOrNull(16)
      else -> name.drop(1).toIntOrNull()
    } ?: return ReplacementCharacter

    return when {
      codePoint.isDecodableCharacter() -> String(Character.toChars(codePoint))
      else -> ReplacementCharacter
    }
  }

  /** The browser replaces zero, surrogates and everything above U+10FFFF instead of decoding them. */
  private fun Int.isDecodableCharacter(): Boolean =
    when (this) {
      0 -> false
      in SurrogateCodePoints -> false
      else -> Character.isValidCodePoint(this)
    }

  private val SurrogateCodePoints: IntRange = 0xD800..0xDFFF

  private val ReplacementCharacter: String = Char(0xFFFD).toString()

  /**
   * The attributes by lowercased name. Parsed rather than searched, so a `src=` or `type=` standing
   * inside another attribute's value is read as the value it is. A repeated name keeps its first
   * occurrence, which is the one the HTML tokenizer lets govern.
   */
  private fun attributesOf(openingTagAttributes: String): Map<String, String> =
    Attribute.findAll(openingTagAttributes)
      .map { attribute ->
        attribute.groupValues[1].lowercase() to
          listOf(2, 3, 4).firstNotNullOfOrNull { group -> attribute.groupValues[group].ifEmpty { null } }.orEmpty()
      }
      .groupBy({ it.first }, { it.second })
      .mapValues { (_, values) -> values.first() }

  private fun String.lineNumberAt(index: Int): Int = 1 + substring(0, index).count { it == '\n' }
}
