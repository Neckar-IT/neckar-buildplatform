package it.neckar.gradle

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.messageContains
import it.neckar.projects.common.MavenCoordinate
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ProducingProjectTest {
  @TempDir
  lateinit var projectDir: File

  private lateinit var root: Project
  private lateinit var library: Project

  @BeforeEach
  fun createProjects() {
    root = ProjectBuilder.builder().withName("root").withProjectDir(projectDir).build()
    library = module(root, "library", "it.neckar.db")
  }

  @Test
  fun `a coordinate resolves to the project producing it`() {
    assertThat(root.projectProducing(MavenCoordinate("it.neckar.db", "library"))).isEqualTo(library)
    assertThat(library.mavenModule).isEqualTo("it.neckar.db:library")
  }

  @Test
  fun `a coordinate no project produces names no project`() {
    assertThat(root.projectProducing(MavenCoordinate("it.neckar.db", "other"))).isNull()
  }

  @Test
  fun `a dependency of a neckar group names its project and a third-party one none`() {
    assertThat(root.projectProducing(externalDependency("it.neckar.db:library:1.0.0"))).isEqualTo(library)
    assertThat(root.projectProducing(externalDependency("org.apache.pdfbox:pdfbox:3.0.8"))).isNull()
  }

  /** Gradle substitutes the projects of the included builds itself; a dependency without a group names nothing a build produces. */
  @Test
  fun `a dependency of an included build names no project and one without a group fails`() {
    assertThat(root.projectProducing(externalDependency("it.neckar.build-logic:core:1.0.0"))).isNull()
    assertThat(root.projectProducing(externalDependency("it.neckar.buildplatform:projects:1.0.0"))).isNull()
    assertFailure { root.projectProducing(root.dependencies.create(mapOf("name" to "lonely")) as ExternalModuleDependency) }
      .messageContains("declares null:lonely: without a group")
  }

  @Test
  fun `two projects producing one module fail the lookup`() {
    val other = ProjectBuilder.builder().withName("other").withParent(root).build()
    module(other, "library", "it.neckar.db")

    assertFailure { root.projectProducing(MavenCoordinate("it.neckar.db", "library")) }
      .messageContains("names the projects [:library, :other:library]")
  }

  /** The substitution rule asks per dependency, so the lookup reads the projects once after they are evaluated. */
  @Test
  fun `the projects are indexed once every project is evaluated`() {
    val late = ProjectBuilder.builder().withName("late").withParent(root).build()
    root.projectProducing(MavenCoordinate("it.neckar.db", "library"))
    late.group = "it.neckar.late"
    assertThat(root.projectProducing(MavenCoordinate("it.neckar.late", "late"))).isEqualTo(late)

    root.allprojects.forEach { (it as ProjectInternal).evaluate() }
    root.projectProducing(MavenCoordinate("it.neckar.late", "late"))
    val afterwards = ProjectBuilder.builder().withName("afterwards").withParent(root).build().also { it.group = "it.neckar.late" }

    assertThat(root.projectProducing(MavenCoordinate("it.neckar.late", "afterwards"))).isNull()
    assertThat(afterwards.mavenModule).isEqualTo("it.neckar.late:afterwards")
  }

  private fun module(parent: Project, name: String, group: String): Project {
    return ProjectBuilder.builder().withName(name).withParent(parent).build().also { it.group = group }
  }

  private fun externalDependency(notation: String): ExternalModuleDependency = root.dependencies.create(notation) as ExternalModuleDependency
}
