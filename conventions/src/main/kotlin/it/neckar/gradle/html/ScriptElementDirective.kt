package it.neckar.gradle.html

/**
 * The directives that govern an inline `<script>` element, in the browser's fallback order.
 */
internal enum class ScriptElementDirective(val directiveName: String) {
  ScriptSrcElem("script-src-elem"),
  ScriptSrc("script-src"),
  DefaultSrc("default-src"),
}
