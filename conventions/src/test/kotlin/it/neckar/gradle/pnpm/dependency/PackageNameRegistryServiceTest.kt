package it.neckar.gradle.pnpm.dependency

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.message
import it.neckar.projects.GradleProjectPath
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PackageNameRegistryServiceTest {

  @TempDir
  lateinit var repositoryDirectory: File

  private fun writeManifest(relativeDirectory: String, manifest: String) {
    File(repositoryDirectory, relativeDirectory).mkdirs()
    File(repositoryDirectory, "$relativeDirectory/package.json").writeText(manifest)
  }

  private fun Project.registerService(vararg pnpmModules: String): Provider<PackageNameRegistryService> {
    return gradle.sharedServices.registerIfAbsent(PackageNameRegistryService.ServiceName, PackageNameRegistryService::class.java) {
      parameters.pnpmModules.set(pnpmModules.toList())
      parameters.rootDirectory.set(layout.projectDirectory)
    }
  }

  @Test
  fun `the registry maps the name of each module's package json to the module`() {
    writeManifest("libs/dep-a", """{ "name": "@scope/dep-a" }""")
    writeManifest("libs/dep-b", """{ "name": "dep-b" }""")
    val root = ProjectBuilder.builder().withProjectDir(repositoryDirectory).build()

    val registry = root.registerService(":libs:dep-a", ":libs:dep-b").get().registry

    assertThat(registry.findGradlePath(NpmPackageName("@scope/dep-a"))).isEqualTo(GradleProjectPath(":libs:dep-a"))
    assertThat(registry.findGradlePath(NpmPackageName("dep-b"))).isEqualTo(GradleProjectPath(":libs:dep-b"))
    assertThat(registry.size).isEqualTo(2)
  }

  @Test
  fun `every caller of one build receives the same registry`() {
    writeManifest("dep-a", """{ "name": "dep-a" }""")
    val root = ProjectBuilder.builder().withProjectDir(repositoryDirectory).build()
    val child = ProjectBuilder.builder().withName("dep-a").withParent(root).withProjectDir(File(repositoryDirectory, "dep-a")).build()

    val fromRoot = root.registerService(":dep-a").get().registry
    val fromChild = child.registerService(":other").get().registry

    assertThat(fromChild).isSameInstanceAs(fromRoot)
  }

  @Test
  fun `a module without package json fails naming module and expected path`() {
    val root = ProjectBuilder.builder().withProjectDir(repositoryDirectory).build()

    assertFailure { root.registerService(":missing").get().registry }
      .isInstanceOf(GradleException::class)
      .message().isNotNull().all {
        contains(":missing")
        contains(File(repositoryDirectory, "missing/package.json").absolutePath)
      }
  }

  @Test
  fun `two modules declaring one name fail naming both`() {
    writeManifest("dep-a", """{ "name": "shared" }""")
    writeManifest("dep-b", """{ "name": "shared" }""")
    val root = ProjectBuilder.builder().withProjectDir(repositoryDirectory).build()

    assertFailure { root.registerService(":dep-a", ":dep-b").get().registry }
      .isInstanceOf(GradleException::class)
      .message().isNotNull().all {
        contains(":dep-a")
        contains(":dep-b")
        contains("'shared'")
      }
  }
}
