package it.neckar.gradle.html

/**
 * What one policy says about inline `<script>` elements.
 */
internal sealed interface ScriptRestriction {
  /** None of the [ScriptElementDirective]s is declared, so the policy leaves scripts alone. */
  data object Unrestricted : ScriptRestriction

  /** The sources of the first declared [ScriptElementDirective]. */
  data class Restricted(val sources: List<CspSource>) : ScriptRestriction {
    /** `'unsafe-inline'` does not count: it admits every block, the injected ones included, and a browser ignores it beside any hash. */
    fun admitsByHash(script: InlineScript): Boolean =
      sources.any { source -> source is CspSource.Hash && source.digest == script.digest(source.algorithm) }
  }
}
