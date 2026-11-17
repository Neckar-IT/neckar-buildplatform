package it.neckar.gradle.html

import java.io.File
import java.security.MessageDigest

/** The script types a browser reads only inline: it ignores a `src` on an import map and on speculation rules. */
private val InlineOnlyScriptTypes: Set<String> = setOf("importmap", "speculationrules")

/** The attributes a browser ignores on an inline classic script and obeys once the script has a `src`: they change when or whether it runs. */
private val SourceOnlyAttributes: Set<String> = setOf("async", "defer", "integrity")

/** Prefix of every file [externalizeInlineScripts] writes. */
internal const val InlineScriptFilePrefix: String = "inline-script-"

/**
 * Moves every inline script [InlineScriptScanner] finds in the HTML files below [siteDirectory] into
 * `inline-script-<sha256>.js` beside its page, so module imports resolve and classic scripts run in the same order.
 * An import map, speculation rules and a classic script with `async`, `defer` or `integrity` stop the build.
 */
fun externalizeInlineScripts(siteDirectory: File) {
  require(siteDirectory.isDirectory) { "$siteDirectory is no directory" }
  val htmlFiles = siteDirectory.walkTopDown().filter { it.isFile && it.extension == "html" }.toList()

  htmlFiles.forEach { htmlFile ->
    val page = htmlFile.relativeTo(siteDirectory).invariantSeparatorsPath
    val text = htmlFile.readText()
    val rewritten = StringBuilder(text)
    // From the last element to the first, so the offsets of the earlier ones stay valid.
    InlineScriptScanner.scan(HtmlDocument(text)).inlineScripts.asReversed().forEach { inlineScript ->
      if (inlineScript.type in InlineOnlyScriptTypes) {
        error("$page:${inlineScript.lineNumber} holds an inline <script type=\"${inlineScript.type}\">, which a browser reads only inline")
      }
      val sourceOnlyAttributes = inlineScript.attributeNames.intersect(SourceOnlyAttributes)
      if (inlineScript.isClassic && sourceOnlyAttributes.isNotEmpty()) {
        error("$page:${inlineScript.lineNumber} holds an inline classic script with ${sourceOnlyAttributes.joinToString()}, which would change when or whether it runs from a file")
      }
      val fileName = "$InlineScriptFilePrefix${sha256Hex(inlineScript.body)}.js"
      htmlFile.resolveSibling(fileName).writeText(inlineScript.body)
      rewritten.replace(inlineScript.offset.index, inlineScript.end.index, """<script${inlineScript.openingTagAttributes} src="./$fileName"></script>""")
    }
    htmlFile.writeText(rewritten.toString())
  }
}

private fun sha256Hex(text: String): String {
  return MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
