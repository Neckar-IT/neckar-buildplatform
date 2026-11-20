package it.neckar.gradle.html

/**
 * The digests a Content Security Policy hash source may name.
 */
internal enum class HashAlgorithm(
  /** The prefix of the source, as in `'sha256-…'`. */
  val sourcePrefix: String,

  /** The name [java.security.MessageDigest] knows it by. */
  val javaName: String,
) {
  Sha256("sha256-", "SHA-256"),
  Sha384("sha384-", "SHA-384"),
  Sha512("sha512-", "SHA-512"),
}
