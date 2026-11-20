package it.neckar.gradle.html

import java.security.MessageDigest
import java.util.Base64

/**
 * One `<script>` element that carries its code in the document.
 */
internal data class InlineScript(
  /** 1-based line of the opening tag. */
  val lineNumber: Int,

  /** Where the opening tag stands, so a policy declared after it is known not to govern it. */
  val offset: DocumentOffset,

  /** The text between the opening and the closing tag, as written in the file. */
  val body: String,

  /** The attributes of the opening tag, as written in the file. */
  val openingTagAttributes: String,

  /** The value of the `type` attribute, trimmed and lowercase; empty for a classic script without one. */
  val type: String,

  /** The names of the opening tag's attributes, lowercase. */
  val attributeNames: Set<String>,

  /** Where the element ends: the index after its closing tag, or the end of the document without one. */
  val end: DocumentOffset,
) {
  /** True for a classic script as [isClassicScriptType] counts it. */
  val isClassic: Boolean
    get() = isClassicScriptType(type)

  /** The base64 digest of [body] under [algorithm], as a policy's `'sha256-…'` source names it. */
  fun digest(algorithm: HashAlgorithm): String {
    // The HTML parser normalizes CRLF and CR to LF before the browser hashes the element.
    val normalizedBody: String = body.replace("\r\n", "\n").replace('\r', '\n')

    return Base64.getEncoder().encodeToString(MessageDigest.getInstance(algorithm.javaName).digest(normalizedBody.toByteArray(Charsets.UTF_8)))
  }
}

/**
 * True for no [type] or a [type] naming JavaScript or ECMAScript, trimmed and lowercase: the scripts the scanner
 * counts as classic. Broader than the browser, which also needs the MIME type to be exactly a JavaScript one.
 */
internal fun isClassicScriptType(type: String): Boolean = type.isEmpty() || type.contains("javascript") || type.contains("ecmascript")
