package it.neckar.gradle.npmbundle

import assertk.assertThat
import assertk.assertions.contains
import it.neckar.projects.GradleProjectPath
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DeclaresNpmBundleTest {

  @TempDir
  lateinit var rootDir: File

  @Test
  fun `the task runs after npmBundle and reads the package directory`() {
    val root = ProjectBuilder.builder().withProjectDir(rootDir).build()
    val bundleProject = ProjectBuilder.builder().withName("bundle").withParent(root).withProjectDir(rootDir.resolve("bundle")).build()
    val consumerProject = ProjectBuilder.builder().withName("consumer").withParent(root).withProjectDir(rootDir.resolve("consumer")).build()
    val consumerTask = consumerProject.tasks.register("pnpmRunBuild").get()
    val packageJson = bundleProject.layout.buildDirectory.file("npm-work/package.json").get().asFile
    packageJson.parentFile.mkdirs()
    packageJson.writeText("{}")

    consumerTask.declaresNpmBundle(GradleProjectPath(":bundle"))

    assertThat(consumerTask.dependsOn).contains(":bundle:npmBundle")
    assertThat(consumerTask.inputs.files.files).contains(packageJson)
  }
}
