package it.neckar.gradle.npmbundle

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.messageContains
import it.neckar.gradle.BuildplatformPlugins
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CopyBundleContentTaskTest {

  @TempDir
  lateinit var projectDir: File

  private fun bundleProject(): Project {
    File(projectDir, "package.json").writeText("""{ "version": "0.0.0", "main": "easy-api.js", "dependencies": {} }""")
    val project = ProjectBuilder.builder().withName("easy-api").withProjectDir(projectDir).build()
    project.plugins.apply(BuildplatformPlugins.KotlinMultiPlatform)
    project.plugins.apply(NpmBundlePlugin::class.java)
    return project
  }

  private fun Project.runCopyTask(taskName: String) {
    (tasks.getByName(taskName) as CopyBundleContentTask).copyBundleContent()
  }

  private fun assertPackageJsonIn(directory: File, name: String, version: String) {
    val packageJson = Json.parseToJsonElement(directory.resolve("package.json").readText()).jsonObject
    assertThat(packageJson.getValue("name").jsonPrimitive.content).isEqualTo(name)
    assertThat(packageJson.getValue("version").jsonPrimitive.content).isEqualTo(version)
  }

  @Test
  fun `the production bundle takes name and version of its extension`() {
    val project = bundleProject()
    project.extensions.getByType(NpmBundleExtension::class.java).apply {
      moduleName.set("@meistercharts/meistercharts")
      version.set("1.39.0-SNAPSHOT")
    }

    project.runCopyTask(NpmBundlePlugin.npmCopyBundleContentTaskName)

    assertPackageJsonIn(project.layout.buildDirectory.dir(NpmBundlePlugin.WorkingDirName).get().asFile, "@meistercharts/meistercharts", "1.39.0-SNAPSHOT")
  }

  @Test
  fun `the development bundle takes name and version of its extension`() {
    val project = bundleProject()
    (project.extensions.getByName(NpmBundlePlugin.DevelopmentExtensionName) as NpmBundleExtension).apply {
      moduleName.set("@meistercharts-dev/meistercharts")
      version.set("1.39.0-SNAPSHOT")
    }

    project.runCopyTask(NpmBundlePlugin.npmCopyBundleContentTaskNameDevelopment)

    assertPackageJsonIn(project.layout.buildDirectory.dir("npmDevelopment-work").get().asFile, "@meistercharts-dev/meistercharts", "1.39.0-SNAPSHOT")
  }

  @Test
  fun `a package json from an earlier run does not hide that the spec stopped listing it`() {
    val project = bundleProject()
    project.runCopyTask(NpmBundlePlugin.npmCopyBundleContentTaskName)
    File(projectDir, "package.json").delete()

    assertFailure { project.runCopyTask(NpmBundlePlugin.npmCopyBundleContentTaskName) }
      .messageContains("filesToBundle of the extension that configures ${NpmBundlePlugin.npmCopyBundleContentTaskName}")
  }

  @Test
  fun `a name in the checked-in package json is rejected`() {
    assertFailure { packageJsonWithNameAndVersion("""{ "name": "easy-api", "version": "0.0.0" }""", "@scope/b", "1.0.0") }
      .messageContains("moduleName of the extension names the package")
  }

  @Test
  fun `name and version come first and every other field keeps its value and order`() {
    val updated = packageJsonWithNameAndVersion("""{ "version": "0.0.0", "main": "a.js", "dependencies": {} }""", "@scope/b", "1.0.0")

    assertThat(Json.parseToJsonElement(updated).jsonObject.keys.toList()).isEqualTo(listOf("name", "version", "main", "dependencies"))
    assertThat(updated).contains("\"main\": \"a.js\"")
  }
}
