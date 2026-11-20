package it.neckar.gradle.html

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import org.junit.jupiter.api.Test

class InlineScriptScannerTest {

  @Test
  fun `a script carrying code is a finding on its opening line`() {
    val document = """
      <!doctype html>
      <html>
        <head>
          <title>Test</title>
          <script>
            window.theme = "dark";
          </script>
        </head>
      </html>
    """.trimIndent()

    assertThat(inlineScriptLines(HtmlDocument(document))).containsExactly(5)
  }

  @Test
  fun `a script loaded from a file passes`() {
    val document = """
      <head>
        <script src="/theme-init.js"></script>
        <script type="module" src="/src/main.tsx"></script>
      </head>
    """.trimIndent()

    assertThat(inlineScriptLines(HtmlDocument(document))).isEmpty()
  }

  @Test
  fun `every inline script of a document is reported`() {
    val document = """
      <head>
        <script>a()</script>
        <script src="/b.js"></script>
        <script>c()</script>
      </head>
    """.trimIndent()

    assertThat(inlineScriptLines(HtmlDocument(document))).containsExactly(2, 4)
  }

  // The frontends write `src` unquoted nowhere, but an attribute before it must not hide it.
  @Test
  fun `a src behind other attributes passes`() {
    val document = """<script defer crossorigin src="/theme-init.js"></script>"""

    assertThat(inlineScriptLines(HtmlDocument(document))).isEmpty()
  }

  // A body next to the src, so the rule is decided on the attribute rather than on the empty body.
  @Test
  fun `a src passes even when the element is not empty`() {
    val document = """<script src="/a.js">// fallback</script>"""

    assertThat(inlineScriptLines(HtmlDocument(document))).isEmpty()
  }

  @Test
  fun `a repeated attribute is governed by its first occurrence, as in the browser`() {
    val document = """<script type="module" type="text/plain">alert(1)</script>"""

    assertThat(inlineScriptLines(HtmlDocument(document))).containsExactly(1)
  }

  @Test
  fun `a malformed value does not leave a src behind`() {
    val document = """<script data-x=a=src=b>alert(1)</script>"""

    assertThat(inlineScriptLines(HtmlDocument(document))).containsExactly(1)
  }

  // `-` ends a word, so a `src` anchored on a word boundary alone would read `data-src` as a src.
  @Test
  fun `an attribute merely ending in src is no src`() {
    val document = """<script data-src="/loader.js">alert(1)</script>"""

    assertThat(inlineScriptLines(HtmlDocument(document))).containsExactly(1)
  }

  @Test
  fun `a quoted attribute value may hold the tag's closing character`() {
    val document = """<script onload="if (a > b) c()" src="/a.js"></script>"""

    assertThat(inlineScriptLines(HtmlDocument(document))).isEmpty()
  }

  @Test
  fun `an opening tag spanning several lines is reported where it starts`() {
    val document = """
      <head>
        <script
          type="module"
          src="/src/main.tsx"
        ></script>
        <script
        >a()</script>
      </head>
    """.trimIndent()

    assertThat(inlineScriptLines(HtmlDocument(document))).containsExactly(6)
  }

  // script-src governs what the browser runs; a data block is content, and no Vite plugin can emit it.
  @Test
  fun `a data block is no script`() {
    val document = """
      <head>
        <script type="application/ld+json">{ "@type": "Organization" }</script>
        <script type="text/template"><p></p></script>
      </head>
    """.trimIndent()

    assertThat(inlineScriptLines(HtmlDocument(document))).isEmpty()
  }

  // script-src governs these two as well, though the browser runs neither as a classic script.
  @Test
  fun `an import map and speculation rules are script`() {
    val document = """
      <head>
        <script type="importmap">{ "imports": {} }</script>
        <script type="speculationrules">{ "prerender": [] }</script>
      </head>
    """.trimIndent()

    assertThat(inlineScriptLines(HtmlDocument(document))).containsExactly(2, 3)
  }

  @Test
  fun `a javascript type still carries script`() {
    val document = """
      <head>
        <script type="text/javascript">a()</script>
        <script type="module">b()</script>
      </head>
    """.trimIndent()

    assertThat(inlineScriptLines(HtmlDocument(document))).containsExactly(2, 3)
  }

  @Test
  fun `an empty script carries no code`() {
    assertThat(inlineScriptLines(HtmlDocument("<script></script>"))).isEmpty()
  }

  // Both sequences can stand inside the other's content, so neither may be blanked out up front.
  @Test
  fun `a comment opening inside a script body hides nothing behind it`() {
    val document = """
      <script>
        var s = "<!--";
      </script>
      <script>evil()</script>
      <!-- a real comment -->
    """.trimIndent()

    assertThat(inlineScriptLines(HtmlDocument(document))).containsExactly(1, 4)
  }

  @Test
  fun `a src or type standing inside another attribute's value is that value`() {
    val document = """
      <head>
        <script data-config="a src=b">alert(1)</script>
        <script data-config="a type=application/ld+json">alert(2)</script>
      </head>
    """.trimIndent()

    assertThat(inlineScriptLines(HtmlDocument(document))).containsExactly(2, 3)
  }

  @Test
  fun `single quotes delimit a value like double ones`() {
    val document = """<script onload='if (a > b) c()' src='/a.js'></script>"""

    assertThat(inlineScriptLines(HtmlDocument(document))).isEmpty()
  }

  @Test
  fun `the tag and its attributes are matched regardless of case`() {
    val document = """
      <head>
        <SCRIPT SRC="/a.js">// fallback</SCRIPT>
        <Script Type="application/ld+json">{ }</Script>
        <Script Type="Module">a()</Script>
      </head>
    """.trimIndent()

    assertThat(inlineScriptLines(HtmlDocument(document))).containsExactly(4)
  }

  @Test
  fun `a commented-out script is no finding, and the lines after it keep their numbers`() {
    val document = """
      <head>
        <!-- <script>
          window.theme = "dark";
        </script> -->
        <script>a()</script>
      </head>
    """.trimIndent()

    assertThat(inlineScriptLines(HtmlDocument(document))).containsExactly(5)
  }

  private fun inlineScriptLines(document: HtmlDocument): List<Int> =
    InlineScriptScanner.scan(document).inlineScripts.map { it.lineNumber }
}
