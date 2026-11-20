package it.neckar.gradle.html

import assertk.assertThat
import assertk.assertions.contains
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.MessageDigest
import java.util.Base64

/**
 * The case this guards against ships silently: a document answering under `script-src 'self'` has
 * its inline `<script>` parsed into the DOM and never run, and nothing reports it.
 */
class VerifyNoInlineScriptsTaskTest {

  @TempDir
  lateinit var projectDir: File

  @Test
  fun `a document loading its script from a file passes`() {
    writeDocuments("index.html" to """<html><head><script src="/assets/theme-init.js"></script></head></html>""")

    verify()
  }

  @Test
  fun `a document carrying script code fails, naming its path and line`() {
    writeDocuments(
      "index.html" to """
        <html>
          <head>
            <script>console.log("lost")</script>
          </head>
        </html>
      """.trimIndent(),
    )

    val failure = assertThrows<GradleException> { verify() }

    assertThat(failure.message.orEmpty()).contains("dist/index.html:3")
  }

  @Test
  fun `a framework block whose hash the document's meta policy names passes`() {
    val islandRuntime = "(self.Astro||(self.Astro={})).load=e=>e();"
    val hash = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(islandRuntime.toByteArray(Charsets.UTF_8)))
    writeDocuments(
      "index.html" to """<html><head><meta http-equiv="content-security-policy" content="script-src 'self' 'sha256-$hash';"><script>$islandRuntime</script></head></html>""",
    )

    verify()
  }

  @Test
  fun `an excluded document is not judged`() {
    writeDocuments("doc/scalar.html" to """<html><body><script>third party</script></body></html>""")

    verify(excludes = setOf("doc/**/*.html"))
  }

  @Test
  fun `an exclude that matches no document fails`() {
    writeDocuments("index.html" to """<html><head><script src="/assets/index.js"></script></head></html>""")

    val failure = assertThrows<GradleException> { verify(excludes = setOf("doc/**/*.html")) }

    assertThat(failure.message.orEmpty()).contains("doc/**/*.html")
  }

  @Test
  fun `no document at all fails`() {
    projectDir.resolve("dist").mkdirs()

    val failure = assertThrows<IllegalStateException> { verify() }

    assertThat(failure.message.orEmpty()).contains("names no HTML document to judge")
  }

  private fun verify(excludes: Set<String> = emptySet()) {
    val project = ProjectBuilder.builder().withProjectDir(projectDir).build()

    val task = project.tasks
      .register("verifyNoInlineScripts", VerifyNoInlineScriptsTask::class.java).get()
    task.documents.setFrom(project.fileTree(projectDir.resolve("dist")).matching { include("**/*.html") })
    task.excludes.set(excludes)
    task.projectPath.set(":test:frontend")
    task.projectDirectory.set(projectDir)
    task.marker.set(projectDir.resolve("build/verifyNoInlineScripts/marker.txt"))

    task.check()
  }

  private fun writeDocuments(vararg documents: Pair<String, String>) {
    documents.forEach { (relativePath, content) ->
      projectDir.resolve("dist").resolve(relativePath).also { it.parentFile.mkdirs() }.writeText(content)
    }
  }
}
