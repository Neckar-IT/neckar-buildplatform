package it.neckar.gradle.pnpm.dependency

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.message
import it.neckar.projects.GradleProjectPath
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PnpmWorkspaceDependencyResolverTest {

  @TempDir
  lateinit var repositoryDirectory: File

  private val resolver = PnpmWorkspaceDependencyResolver(
    PackageNameRegistry(
      mapOf(
        NpmPackageName("consumer") to GradleProjectPath(":consumer"),
        NpmPackageName("dep-a") to GradleProjectPath(":dep-a"),
        NpmPackageName("dep-b") to GradleProjectPath(":dep-b"),
        NpmPackageName("without-project") to GradleProjectPath(":without-project"),
      ),
    ),
  )

  /** The projects `consumer`, `dep-a` and `dep-b` under one root, `consumer` carrying [consumerManifestBody] in its manifest. */
  private fun projectWithManifest(consumerManifestBody: String): Project {
    val root = ProjectBuilder.builder().withProjectDir(repositoryDirectory).build()

    writeManifest("dep-a", """{ "name": "dep-a" }""")
    writeManifest("dep-b", """{ "name": "dep-b" }""")
    writeManifest("consumer", """{ "name": "consumer", $consumerManifestBody }""")

    listOf("dep-a", "dep-b").forEach { name ->
      ProjectBuilder.builder().withName(name).withParent(root).withProjectDir(File(repositoryDirectory, name)).build()
    }
    return ProjectBuilder.builder().withName("consumer").withParent(root).withProjectDir(File(repositoryDirectory, "consumer")).build()
  }

  private fun writeManifest(projectName: String, manifest: String) {
    File(repositoryDirectory, projectName).mkdirs()
    File(repositoryDirectory, "$projectName/package.json").writeText(manifest)
  }

  private fun Project.dependencyProjectPaths(): List<String> = resolver.resolveWorkspaceDependencyProjects(this).projects.map { it.path }

  @Test
  fun `dependencies and devDependencies are one list without repeats`() {
    val consumer = projectWithManifest(""""dependencies": { "dep-a": "workspace:*" }, "devDependencies": { "dep-a": "workspace:*", "dep-b": "workspace:*" }""")

    assertThat(consumer.dependencyProjectPaths()).containsExactly(":dep-a", ":dep-b")
  }

  @Test
  fun `a dependency that is not a workspace package is no project`() {
    val consumer = projectWithManifest(""""dependencies": { "dep-a": "^1.0.0" }""")

    assertThat(consumer.dependencyProjectPaths()).isEmpty()
  }

  @Test
  fun `the package itself is no dependency project`() {
    val consumer = projectWithManifest(""""dependencies": { "consumer": "workspace:*", "dep-a": "workspace:*" }""")

    assertThat(consumer.dependencyProjectPaths()).containsExactly(":dep-a")
  }

  @Test
  fun `a registered package without a Gradle project fails naming both paths`() {
    val consumer = projectWithManifest(""""dependencies": { "without-project": "workspace:*" }""")

    assertFailure { consumer.dependencyProjectPaths() }
      .isInstanceOf(GradleException::class)
      .message().isNotNull().all {
        contains(":consumer")
        contains(":without-project")
      }
  }
}
