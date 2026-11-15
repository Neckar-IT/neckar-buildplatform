package it.neckar.gradle

/** Gradle wraps a configuration failure, so a plugin's own message sits further down the chain. */
internal fun Throwable.messageChain(): String {
  return generateSequence(this) { failure -> failure.cause }
    .joinToString(separator = "\n") { failure -> failure.message.orEmpty() }
}
