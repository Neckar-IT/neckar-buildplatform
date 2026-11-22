package it.neckar.projects

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.messageContains
import it.neckar.gradle.BuildplatformPlugins
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.kotlin.dsl.apply
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class KotlinProjectDependenciesTest {
  @TempDir
  lateinit var projectDir: File

  @Test
  fun `a project of the build is a project dependency`() {
    val root = ProjectBuilder.builder().withProjectDir(projectDir).build()
    val annotations = ProjectBuilder.builder().withName("annotations").withParent(
      ProjectBuilder.builder().withName("open").withParent(ProjectBuilder.builder().withName("internal").withParent(root).build()).build()
    ).build()
    val consumer = ProjectBuilder.builder().withName("consumer").withParent(root).build()

    val dependency = consumer.commonMainDependencyOn(TestRegistry.annotations)

    assertThat(dependency).isInstanceOf(ProjectDependency::class)
    assertThat((dependency as ProjectDependency).path).isEqualTo(annotations.path)
  }

  @Test
  fun `a project missing from the build without the version property fails naming its path`() {
    val consumer = ProjectBuilder.builder().withProjectDir(projectDir).build()

    assertFailure { consumer.commonMainDependencyOn(TestRegistry.annotations) }
      .isInstanceOf(GradleException::class)
      .messageContains(":internal:open:annotations")
  }

  @Test
  fun `a missing project is the released coordinate of its monorepo path`() {
    assertThat(releasedCoordinateOf(GradleProjectPath(":internal:open:annotations"), "10.1.0")).isEqualTo("it.neckar.open:annotations:10.1.0")
    assertThat(releasedCoordinateOf(GradleProjectPath(":internal:open:commons:kotlin-lang"), "10.1.0")).isEqualTo("it.neckar.open:kotlin-lang:10.1.0")
    assertThat(releasedCoordinateOf(GradleProjectPath(":internal:open:rest:ktor-server"), "10.1.0")).isEqualTo("it.neckar.open.rest:ktor-server:10.1.0")
  }

  /**
   * The nesting of a build script: [Project] is the outer receiver, `KotlinDependencyHandler` the inner one. The declared type
   * [Dependency] only compiles when `project(ConfiguredProject)` of `KotlinDependencyHandler` wins over the one of [Project], which
   * returns a [Project].
   */
  private fun Project.commonMainDependencyOn(configuredProject: ConfiguredProject): Dependency {
    apply(plugin = BuildplatformPlugins.KotlinMultiPlatform)
    val kotlin = extensions.getByType(KotlinMultiplatformExtension::class.java)
    kotlin.jvm()
    lateinit var dependency: Dependency
    kotlin.sourceSets.getByName("commonMain").dependencies {
      dependency = project(configuredProject)
    }
    return dependency
  }
}

private object TestRegistry : ProjectRoot() {
  private val internal: ConfiguredProject = intermediate("internal")
  val annotations: ConfiguredProject = OpenGroup(internal).annotations

  private class OpenGroup(parent: ConfiguredProject) : ConfiguredProject(parent, GradleProjectPath(":open"), ProjectType.Intermediate, ProjectRole.Container) {
    val annotations: ConfiguredProject = multiplatform("annotations", ProjectRole.Library, KotlinTarget.Jvm)
  }
}
