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
) {
  /** The base64 digest of [body] under [algorithm], as a policy's `'sha256-…'` source names it. */
  fun digest(algorithm: HashAlgorithm): String {
    // The HTML parser normalizes CRLF and CR to LF before the browser hashes the element.
    val normalizedBody: String = body.replace("\r\n", "\n").replace('\r', '\n')

    return Base64.getEncoder().encodeToString(MessageDigest.getInstance(algorithm.javaName).digest(normalizedBody.toByteArray(Charsets.UTF_8)))
  }
}
