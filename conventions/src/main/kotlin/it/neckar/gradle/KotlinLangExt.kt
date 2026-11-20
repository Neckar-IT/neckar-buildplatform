package it.neckar.gradle

/**
 * Executes the block if this is null
 */
fun <T> T?.ifNull(block: () -> Unit): T? {
  if (this == null) {
    block()
  }

  return this
}


fun <T> T?.requireNotNull(lazyMessage: () -> Any): T {
  requireNotNull(this, lazyMessage)
  return this
}

fun <T> T?.requireNotNull(): T {
  requireNotNull(this)
  return this
}
