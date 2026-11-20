package it.neckar.gradle.html

import assertk.assertThat
import assertk.assertions.containsExactly
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class NoInlineScriptsPluginTest {

  @TempDir
  lateinit var projectDir: File

  @Test
  fun `a project judges the documents it names`() {
    val project = projectWithDocuments("dist/index.html", "public/index.html")
    project.plugins.apply(NoInlineScriptsPlugin::class.java)
    project.extensions.getByType(NoInlineScriptsExtension::class.java).documents.from(project.file("public"))

    assertThat(judgedDocumentNames(project)).containsExactly("public/index.html")
  }

  private fun projectWithDocuments(vararg relativePaths: String): Project {
    relativePaths.forEach { relativePath ->
      projectDir.resolve(relativePath).also { it.parentFile.mkdirs() }.writeText("<html></html>")
    }
    return ProjectBuilder.builder().withProjectDir(projectDir).build()
  }

  private fun judgedDocumentNames(project: Project): List<String> =
    project.tasks.named(NoInlineScriptsPlugin.VerifyTaskName, VerifyNoInlineScriptsTask::class.java).get()
      .documents.files.map { it.relativeTo(projectDir).invariantSeparatorsPath }.sorted()
}
