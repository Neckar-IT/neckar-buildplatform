package it.neckar.gradle.html

/**
 * What [InlineScriptScanner] reads from one document: its inline scripts and the policies its
 * `<meta>` elements declare.
 */
internal data class ScannedHtmlDocument(
  val inlineScripts: List<InlineScript>,
  val documentPolicies: List<DocumentPolicy>,
) {
  /**
   * The blocks the browser would not run: each restricting policy declared before a block must name its hash.
   * A block ahead of every policy (Starlight's theme provider) must be named by all of them; without any policy it is blocked.
   */
  fun blockedInlineScripts(): List<InlineScript> {
    val restrictingPolicies: List<Pair<DocumentOffset, ScriptRestriction.Restricted>> = documentPolicies.mapNotNull { policy ->
      when (val restriction = policy.scriptRestriction) {
        is ScriptRestriction.Restricted -> policy.offset to restriction
        ScriptRestriction.Unrestricted -> null
      }
    }

    return inlineScripts.filter { script ->
      val precedingRestrictions: List<ScriptRestriction.Restricted> = restrictingPolicies.filter { (offset, _) -> offset < script.offset }.map { it.second }
      val governingRestrictions: List<ScriptRestriction.Restricted> = precedingRestrictions.ifEmpty { restrictingPolicies.map { it.second } }
      governingRestrictions.isEmpty() || governingRestrictions.any { restriction -> restriction.admitsByHash(script).not() }
    }
  }
}
