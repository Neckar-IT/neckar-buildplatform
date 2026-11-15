package it.neckar.gradle.dependencies

import assertk.assertThat
import assertk.assertions.contains
import it.neckar.gradle.BuildplatformPlugins
import it.neckar.gradle.messageChain
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository
import org.gradle.api.attributes.Usage
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CapabilityConflictPluginTest {

  @TempDir
  lateinit var projectDir: File

  @Test
  fun `an application with both bouncycastle providers on its runtime classpath fails to resolve`() {
    val project = projectWithBouncycastleRepository("application")
    declareBothBouncycastleProviders(project, "implementation")

    assertBouncycastleConflict(project, "runtimeClasspath")
  }

  @Test
  fun `a project without the java plugin resolving both bouncycastle providers fails to resolve`() {
    val project = projectWithBouncycastleRepository(BuildplatformPlugins.Base)
    project.configurations.create("extractionRuntime") {
      attributes.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
    }
    declareBothBouncycastleProviders(project, "extractionRuntime")

    assertBouncycastleConflict(project, "extractionRuntime")
  }

  private fun assertBouncycastleConflict(project: Project, configurationName: String) {
    val failure = assertThrows<Exception> { project.configurations.getByName(configurationName).files }

    assertThat(failure.messageChain()).contains("Both provide capability 'org.gradlex:bouncycastle-bcprov")
  }

  /** A project with [plugins] and a `flatDir` repository holding stub jars of `bcprov-jdk15to18` and `bcprov-jdk18on`. */
  private fun projectWithBouncycastleRepository(vararg plugins: String): Project {
    val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
    project.plugins.apply(CapabilityConflictPlugin::class.java)
    plugins.forEach { project.plugins.apply(it) }

    val repositoryDir = File(project.projectDir, "repo").apply { mkdirs() }
    listOf("bcprov-jdk15to18-1.85.jar", "bcprov-jdk18on-1.85.jar").forEach { File(repositoryDir, it).writeText("stub jar") }
    project.repositories.flatDir(Action<FlatDirectoryArtifactRepository> { dirs(repositoryDir) })
    return project
  }

  private fun declareBothBouncycastleProviders(project: Project, configurationName: String) {
    project.dependencies.add(configurationName, "org.bouncycastle:bcprov-jdk15to18:1.85")
    project.dependencies.add(configurationName, "org.bouncycastle:bcprov-jdk18on:1.85")
  }
}
