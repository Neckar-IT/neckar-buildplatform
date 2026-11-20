package it.neckar.gradle.html

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.Base64

/**
 * Astro emits its island runtime inline and admits it through a hash in the page's own meta policy;
 * only a block that policy names may pass.
 */
class ScannedHtmlDocumentTest {

  @Test
  fun `a block whose hash the meta policy names passes`() {
    val document = """
      <head>
        <meta http-equiv="content-security-policy" content="script-src 'self' '${sha256Source(IslandRuntime)}';">
        <script>$IslandRuntime</script>
      </head>
    """.trimIndent()

    assertThat(blockedLines(document)).isEmpty()
  }

  @Test
  fun `a block beside a hashed one fails`() {
    val document = """
      <head>
        <meta http-equiv="content-security-policy" content="script-src 'self' '${sha256Source(IslandRuntime)}';">
        <script>$IslandRuntime</script>
        <script>authored()</script>
      </head>
    """.trimIndent()

    assertThat(blockedLines(document)).containsExactly(4)
  }

  // The browser hashes the body as written, so a hash of the trimmed body admits nothing.
  @Test
  fun `a hash of a body differing by whitespace fails`() {
    val document = """
      <meta http-equiv="content-security-policy" content="script-src '${sha256Source(IslandRuntime)}'">
      <script> $IslandRuntime</script>
    """.trimIndent()

    assertThat(blockedLines(document)).containsExactly(2)
  }

  @Test
  fun `a document without a meta policy admits no block`() {
    assertThat(blockedLines("<script>$IslandRuntime</script>")).containsExactly(1)
  }

  @Test
  fun `a meta policy inside a comment declares nothing`() {
    val document = """
      <!-- <meta http-equiv="content-security-policy" content="script-src '${sha256Source(IslandRuntime)}'"> -->
      <script>$IslandRuntime</script>
    """.trimIndent()

    assertThat(blockedLines(document)).containsExactly(2)
  }

  @Test
  fun `unsafe-inline admits no block`() {
    val document = """
      <meta http-equiv="content-security-policy" content="script-src 'self' 'unsafe-inline'">
      <script>$IslandRuntime</script>
    """.trimIndent()

    assertThat(blockedLines(document)).containsExactly(2)
  }

  // The browser enforces every policy on its own; one naming the hash does not outvote another.
  @Test
  fun `a block passes only when every restricting policy names its hash`() {
    val document = """
      <meta http-equiv="content-security-policy" content="script-src '${sha256Source(IslandRuntime)}'">
      <meta http-equiv="content-security-policy" content="default-src 'self'">
      <meta http-equiv="content-security-policy" content="img-src 'self'">
      <script>$IslandRuntime</script>
    """.trimIndent()

    assertThat(blockedLines(document)).containsExactly(4)
  }

  @Test
  fun `script-src-elem governs over script-src`() {
    val document = """
      <meta http-equiv="content-security-policy" content="script-src '${sha256Source(IslandRuntime)}'; script-src-elem 'self'">
      <script>$IslandRuntime</script>
    """.trimIndent()

    assertThat(blockedLines(document)).containsExactly(2)
  }

  @Test
  fun `quotes written as character references still name the hash`() {
    val document = """
      <meta content="default-src 'self'; script-src &#x27;${sha256Source(IslandRuntime)}&#x27;" http-equiv="Content-Security-Policy">
      <script>$IslandRuntime</script>
    """.trimIndent()

    assertThat(blockedLines(document)).isEmpty()
  }

  @Test
  fun `an uppercase hexadecimal reference still names the hash`() {
    val document = """
      <meta http-equiv="content-security-policy" content="script-src &#X27;${sha256Source(IslandRuntime)}&#X27;">
      <script>$IslandRuntime</script>
    """.trimIndent()

    assertThat(blockedLines(document)).isEmpty()
  }

  // The HTML parser turns CRLF into LF before the browser hashes the element.
  @Test
  fun `a body written with CRLF is hashed as LF`() {
    val bodyWithLf = "\n  window.theme = \"dark\";\n"
    val document = "<meta http-equiv=\"content-security-policy\" content=\"script-src '${sha256Source(bodyWithLf)}'\">\r\n<script>${bodyWithLf.replace("\n", "\r\n")}</script>"

    assertThat(blockedLines(document)).isEmpty()
  }

  @Test
  fun `a meta policy in the body declares nothing`() {
    val document = """
      <head></head>
      <body>
        <meta http-equiv="content-security-policy" content="script-src '${sha256Source(IslandRuntime)}'">
        <script>$IslandRuntime</script>
      </body>
    """.trimIndent()

    assertThat(blockedLines(document)).containsExactly(4)
  }

  @Test
  fun `the algorithm prefix matches regardless of case`() {
    val document = """
      <meta http-equiv="content-security-policy" content="script-src 'SHA256-${sha256Source(IslandRuntime).removePrefix("sha256-")}'">
      <script>$IslandRuntime</script>
    """.trimIndent()

    assertThat(blockedLines(document)).isEmpty()
  }

  @Test
  fun `a body without a closing head ends the head`() {
    val document = """
      <body>
      <meta http-equiv="content-security-policy" content="script-src '${sha256Source(IslandRuntime)}'">
      <script>$IslandRuntime</script>
    """.trimIndent()

    assertThat(blockedLines(document)).containsExactly(3)
  }

  @Test
  fun `a closing head inside a comment does not end the head`() {
    val document = """
      <!-- </head> -->
      <meta http-equiv="content-security-policy" content="script-src '${sha256Source(IslandRuntime)}'">
      <script>$IslandRuntime</script>
    """.trimIndent()

    assertThat(blockedLines(document)).isEmpty()
  }

  // Counted, the meta would admit the second block; its quotes are character references, so its hash source is valid.
  @Test
  fun `a meta inside a script body declares nothing`() {
    val document = """
      <script>var markup = `<meta http-equiv="content-security-policy" content="script-src &#x27;${sha256Source(IslandRuntime)}&#x27;">`;</script>
      <script>$IslandRuntime</script>
    """.trimIndent()

    assertThat(blockedLines(document)).containsExactly(1, 2)
  }

  @Test
  fun `a closing head inside a script string does not end the head`() {
    val document = """
      <script>document.write("</head>")</script>
      <meta http-equiv="content-security-policy" content="script-src '${sha256Source(IslandRuntime)}'">
      <script>$IslandRuntime</script>
    """.trimIndent()

    assertThat(blockedLines(document)).containsExactly(1)
  }

  // A browser applies a meta policy only to the elements that follow it.
  @Test
  fun `a policy declared after a block does not govern it`() {
    val document = """
      <meta http-equiv="content-security-policy" content="script-src '${sha256Source(IslandRuntime)}'">
      <script>$IslandRuntime</script>
      <meta http-equiv="content-security-policy" content="script-src 'self'">
    """.trimIndent()

    assertThat(blockedLines(document)).isEmpty()
  }

  // Starlight writes its theme provider into the head ahead of the policy Astro adds.
  @Test
  fun `a block ahead of the only policy passes when that policy names its hash`() {
    val document = """
      <script>$IslandRuntime</script>
      <meta http-equiv="content-security-policy" content="script-src '${sha256Source(IslandRuntime)}'">
    """.trimIndent()

    assertThat(blockedLines(document)).isEmpty()
  }

  @Test
  fun `a block ahead of the only policy fails when that policy does not name its hash`() {
    val document = """
      <script>$IslandRuntime</script>
      <meta http-equiv="content-security-policy" content="script-src 'self'">
    """.trimIndent()

    assertThat(blockedLines(document)).containsExactly(1)
  }

  @Test
  fun `a sha384 and a sha512 source admit a block`() {
    val document = """
      <meta http-equiv="content-security-policy" content="script-src '${hashSource("SHA-384", "sha384-", IslandRuntime)}'">
      <meta http-equiv="content-security-policy" content="script-src '${hashSource("SHA-512", "sha512-", IslandRuntime)}'">
      <script>$IslandRuntime</script>
    """.trimIndent()

    assertThat(blockedLines(document)).isEmpty()
  }

  @Test
  fun `a hash in default-src alone admits a block`() {
    val document = """
      <meta http-equiv="content-security-policy" content="default-src 'self' '${sha256Source(IslandRuntime)}'">
      <script>$IslandRuntime</script>
    """.trimIndent()

    assertThat(blockedLines(document)).isEmpty()
  }

  // U+110000 fits an Int but is no code point; the browser decodes it to U+FFFD, and its `;` splits nothing.
  @Test
  fun `a numeric reference naming no code point decodes to the replacement character and admits nothing`() {
    val document = """
      <meta http-equiv="content-security-policy" content="script-src &#x110000;${sha256Source(IslandRuntime)}&#x110000;">
      <script>$IslandRuntime</script>
    """.trimIndent()
    val replacementCharacter: String = Char(0xFFFD).toString()

    val scanned: ScannedHtmlDocument = InlineScriptScanner.scan(HtmlDocument(document))

    assertThat(scanned.documentPolicies.single().scriptRestriction)
      .isEqualTo(ScriptRestriction.Restricted(listOf(CspSource.Other(replacementCharacter + sha256Source(IslandRuntime) + replacementCharacter))))
    assertThat(scanned.blockedInlineScripts().map { it.lineNumber }).containsExactly(2)
  }

  @Test
  fun `an escaped reference is not decoded twice`() {
    val document = """
      <meta http-equiv="content-security-policy" content="script-src &amp;#x27;${sha256Source(IslandRuntime)}&amp;#x27;">
      <script>$IslandRuntime</script>
    """.trimIndent()

    assertThat(blockedLines(document)).containsExactly(2)
  }

  @Test
  fun `a content security policy meta without content fails`() {
    val document = """<meta http-equiv="content-security-policy"><script>$IslandRuntime</script>"""

    assertFailure { InlineScriptScanner.scan(HtmlDocument(document)) }.isInstanceOf(IllegalArgumentException::class)
  }

  @Test
  fun `a meta element of another kind declares no policy`() {
    val document = """
      <meta name="description" content="script-src '${sha256Source(IslandRuntime)}'">
      <script>$IslandRuntime</script>
    """.trimIndent()

    assertThat(blockedLines(document)).containsExactly(2)
  }

  private fun blockedLines(document: String): List<Int> =
    InlineScriptScanner.scan(HtmlDocument(document)).blockedInlineScripts().map { it.lineNumber }

  private fun sha256Source(body: String): String = hashSource("SHA-256", "sha256-", body)

  private fun hashSource(javaAlgorithmName: String, sourcePrefix: String, body: String): String =
    sourcePrefix + Base64.getEncoder().encodeToString(MessageDigest.getInstance(javaAlgorithmName).digest(body.toByteArray(Charsets.UTF_8)))

  private companion object {
    const val IslandRuntime: String = "(self.Astro||(self.Astro={})).load=async e=>{await(await e())()};"
  }
}
