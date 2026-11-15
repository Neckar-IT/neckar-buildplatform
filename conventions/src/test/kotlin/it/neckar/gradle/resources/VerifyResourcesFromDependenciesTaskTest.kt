package it.neckar.gradle.resources

import assertk.assertThat
import assertk.assertions.contains
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The case this guards against produced a green build: `planner-ui` shipped none of its 24 `solar`
 * files because the collection did not see the dependency, and only the browser noticed.
 */
class VerifyResourcesFromDependenciesTaskTest {

  @TempDir
  lateinit var projectDir: File

  @Test
  fun `a resource of a dependency passes the check`() {
    val ownResources = writeFiles("src/jsMain/resources", "fireworks.css" to "the module's own")
    val resolvedResources = writeFiles(
      "build/resolved",
      "fireworks.css" to "the module's own",
      "solar/panel-vertical.png" to "from a dependency",
    )

    verify(resolvedResources, ownResources)
  }

  @Test
  fun `nothing but the module's own resources fails the check`() {
    val ownResources = writeFiles("src/jsMain/resources", "fireworks.css" to "the module's own")
    val resolvedResources = writeFiles("build/resolved", "fireworks.css" to "the module's own")

    val failure = assertThrows<GradleException> { verify(resolvedResources, ownResources) }

    assertThat(failure.message.orEmpty()).contains("[:planner-ui] applies webResourcesFromDependencies, but not one file of its dependencies")
  }

  private fun verify(resolvedResources: File, ownResources: File) {
    val project = ProjectBuilder.builder().withProjectDir(projectDir).build()

    val task = project.tasks
      .register("verifyJsResourcesFromDependencies", VerifyResourcesFromDependenciesTask::class.java).get()
    task.resolvedResourcesDirectory.set(resolvedResources)
    task.ownResourceDirectories.from(ownResources)
    task.projectPath.set(":planner-ui")

    task.verifyResourcesFromDependenciesArrived()
  }

  private fun writeFiles(relativeDirectory: String, vararg files: Pair<String, String>): File {
    val directory = projectDir.resolve(relativeDirectory)

    files.forEach { (relativePath, content) ->
      directory.resolve(relativePath).also { file -> file.parentFile.mkdirs() }.writeText(content)
    }

    return directory
  }
}
