package it.neckar.gradle.html

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.messageContains
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class InlineScriptExternalizerTest {

  @TempDir
  lateinit var siteDirectory: File

  private fun scriptFiles(): List<File> = siteDirectory.listFiles { file -> file.name.startsWith(InlineScriptFilePrefix) }.orEmpty().sortedBy { it.name }

  @Test
  fun `an inline script moves into a file beside the page, keeping its attributes`() {
    // A classic, an external and an inline module script, the three kinds `storybook build` 10.6 writes.
    File(siteDirectory, "iframe.html").writeText(
      """
      <head>
        <script>
          window.CONFIG_TYPE = 'PRODUCTION';
        </script>
        <script type="module" crossorigin src="./assets/iframe-CkpAmjdZ.js"></script>
        <script type="module">
          import './sb-manager/runtime.js';
        </script>
      </head>
      """.trimIndent(),
    )

    externalizeInlineScripts(siteDirectory)

    val page = File(siteDirectory, "iframe.html").readText()
    val (moduleScript, classicScript) = scriptFiles().partition { it.readText().contains("import") }
    assertThat(classicScript.single().readText().trim()).isEqualTo("window.CONFIG_TYPE = 'PRODUCTION';")
    assertThat(page).doesNotContain("CONFIG_TYPE")
    assertThat(page.lines().map { it.trim() }.filter { it.startsWith("<script") }).containsExactly(
      """<script src="./${classicScript.single().name}"></script>""",
      """<script type="module" crossorigin src="./assets/iframe-CkpAmjdZ.js"></script>""",
      """<script type="module" src="./${moduleScript.single().name}"></script>""",
    )
  }

  @Test
  fun `an import map stops the build`() {
    File(siteDirectory, "index.html").writeText("<head>\n<script type=\"importmap\">{ \"imports\": {} }</script>\n</head>")

    assertFailure { externalizeInlineScripts(siteDirectory) }.messageContains("index.html:2 holds an inline <script type=\"importmap\">")
  }

  @Test
  fun `a deferred classic script stops the build`() {
    File(siteDirectory, "index.html").writeText("<script defer>window.late = true;</script>")

    assertFailure { externalizeInlineScripts(siteDirectory) }.messageContains("index.html:1 holds an inline classic script with defer")
  }

  @Test
  fun `an async script of a JavaScript type in a subdirectory stops the build`() {
    File(siteDirectory, "docs").mkdirs()
    File(siteDirectory, "docs/index.html").writeText("<script type=\"text/javascript\" async>window.late = true;</script>")

    assertFailure { externalizeInlineScripts(siteDirectory) }.messageContains("docs/index.html:1 holds an inline classic script with async")
  }

  @Test
  fun `an async module script moves into a file`() {
    File(siteDirectory, "index.html").writeText("<script type=\"module\" async>import './runtime.js';</script>")

    externalizeInlineScripts(siteDirectory)

    assertThat(scriptFiles().map { it.readText() }).containsExactly("import './runtime.js';")
  }

  @Test
  fun `a classic script with an integrity hash stops the build`() {
    File(siteDirectory, "index.html").writeText("<script integrity=\"sha384-abc\">window.checked = true;</script>")

    assertFailure { externalizeInlineScripts(siteDirectory) }.messageContains("index.html:1 holds an inline classic script with integrity")
  }

  @Test
  fun `speculation rules stop the build`() {
    File(siteDirectory, "index.html").writeText("<script type=\"SpeculationRules\">{ \"prefetch\": [] }</script>")

    assertFailure { externalizeInlineScripts(siteDirectory) }.messageContains("index.html:1 holds an inline <script type=\"speculationrules\">")
  }

  @Test
  fun `a classic script with a data attribute naming an import map moves into a file`() {
    File(siteDirectory, "index.html").writeText("<script data-type=\"importmap\">window.ready = true;</script>")

    externalizeInlineScripts(siteDirectory)

    assertThat(scriptFiles().map { it.readText() }).containsExactly("window.ready = true;")
  }

  @Test
  fun `a page in a subdirectory gets its script file beside it`() {
    File(siteDirectory, "docs").mkdirs()
    File(siteDirectory, "docs/guide.html").writeText("<script>window.guide = true;</script>")

    externalizeInlineScripts(siteDirectory)

    val scriptFile = File(siteDirectory, "docs").listFiles { file -> file.name.startsWith(InlineScriptFilePrefix) }.orEmpty().single()
    assertThat(scriptFile.readText()).isEqualTo("window.guide = true;")
    assertThat(File(siteDirectory, "docs/guide.html").readText()).isEqualTo("""<script src="./${scriptFile.name}"></script>""")
  }

  @Test
  fun `two pages with the same script share one file`() {
    File(siteDirectory, "index.html").writeText("<script>window.LOGLEVEL = 'info';</script>")
    File(siteDirectory, "iframe.html").writeText("<script>window.LOGLEVEL = 'info';</script>")

    externalizeInlineScripts(siteDirectory)

    assertThat(scriptFiles().map { it.readText() }).containsExactly("window.LOGLEVEL = 'info';")
  }
}
