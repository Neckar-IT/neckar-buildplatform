package it.neckar.gradle.html

/**
 * One source of a Content Security Policy directive.
 */
internal sealed interface CspSource {
  /** `'sha256-…'`, `'sha384-…'` or `'sha512-…'`. */
  data class Hash(val algorithm: HashAlgorithm, val digest: String) : CspSource

  /** Every other source — `'self'`, a host, `'unsafe-inline'` — which admits no inline block by name. */
  data class Other(val text: String) : CspSource

  companion object {
    /** The algorithm prefix of a hash source is case-insensitive, the base64 digest is not. */
    fun parse(token: String): CspSource {
      if (token.length < 2 || token.startsWith("'").not() || token.endsWith("'").not()) return Other(token)

      val quoted: String = token.substring(1, token.length - 1)
      val algorithm: HashAlgorithm = HashAlgorithm.entries.firstOrNull { quoted.startsWith(it.sourcePrefix, ignoreCase = true) } ?: return Other(token)

      return Hash(algorithm = algorithm, digest = quoted.substring(algorithm.sourcePrefix.length))
    }
  }
}
