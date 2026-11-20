package it.neckar.gradle.fence

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isFalse
import it.neckar.gradle.messageChain
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.kotlin.dsl.configure
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FrontendProjectDependencyFenceTest {
  @TempDir
  lateinit var projectDir: File

  private lateinit var root: Project
  private lateinit var frontend: Project
  private lateinit var otherFrontend: Project
  private lateinit var frontendProjectPaths: List<String>

  @BeforeEach
  fun createProjects() {
    root = ProjectBuilder.builder().withProjectDir(projectDir).build()
    frontend = ProjectBuilder.builder().withName("frontend").withParent(root).build()
    otherFrontend = ProjectBuilder.builder().withName("other-frontend").withParent(root).build()
    frontendProjectPaths = listOf(frontend.path, otherFrontend.path)
  }

  private fun fencedJvmProject(project: Project): Project {
    project.plugins.apply("org.jetbrains.kotlin.jvm")
    project.forbidDependenciesOnFrontendProjects(frontendProjectPaths)
    return project
  }

  private fun fencedJvmConsumer(): Project = fencedJvmProject(ProjectBuilder.builder().withName("consumer").withParent(root).build())

  private fun Project.dependOn(configurationName: String, target: Project) {
    dependencies.add(configurationName, dependencies.project(mapOf("path" to target.path)))
  }

  @Test
  fun `a Kotlin dependency on a frontend project fails the configuration naming consumer and frontend project`() {
    val consumer = fencedJvmConsumer()
    consumer.dependOn("implementation", frontend)

    val failure = assertThrows<Exception> { (consumer as ProjectInternal).evaluate() }

    assertThat(failure.messageChain()).contains("dependencyFence violation in :consumer")
    assertThat(failure.messageChain()).contains("implementation(project(\":frontend\"))")
    assertThat(failure.messageChain()).contains("move the shared code into a library")
  }

  @Test
  fun `a test dependency on a frontend project fails the configuration`() {
    val consumer = fencedJvmConsumer()
    consumer.dependOn("testImplementation", frontend)

    val failure = assertThrows<Exception> { (consumer as ProjectInternal).evaluate() }

    assertThat(failure.messageChain()).contains("testImplementation(project(\":frontend\"))")
  }

  private fun fencedMultiplatformConsumer(declareTargets: KotlinMultiplatformExtension.() -> Unit = { jvm() }): Project {
    val consumer = ProjectBuilder.builder().withName("consumer").withParent(root).build()
    consumer.plugins.apply("org.jetbrains.kotlin.multiplatform")
    consumer.extensions.configure<KotlinMultiplatformExtension>(declareTargets)
    consumer.forbidDependenciesOnFrontendProjects(frontendProjectPaths)
    return consumer
  }

  @Test
  fun `a multiplatform main source set depending on a frontend project fails the configuration`() {
    val consumer = fencedMultiplatformConsumer()
    consumer.dependOn("commonMainImplementation", frontend)

    val failure = assertThrows<Exception> { (consumer as ProjectInternal).evaluate() }

    assertThat(failure.messageChain()).contains("commonMainImplementation(project(\":frontend\"))")
  }

  @Test
  fun `a multiplatform test source set depending on a frontend project fails the configuration`() {
    val consumer = fencedMultiplatformConsumer()
    consumer.dependOn("jvmTestImplementation", frontend)

    val failure = assertThrows<Exception> { (consumer as ProjectInternal).evaluate() }

    assertThat(failure.messageChain()).contains("jvmTestImplementation(project(\":frontend\"))")
  }

  @Test
  fun `a multiplatform web source set depending on a frontend project fails the configuration`() {
    val consumer = fencedMultiplatformConsumer {
      js { browser() }
      sourceSets.maybeCreate("webMain")
    }
    consumer.dependOn("webMainImplementation", frontend)

    val failure = assertThrows<Exception> { (consumer as ProjectInternal).evaluate() }

    assertThat(failure.messageChain()).contains("webMainImplementation(project(\":frontend\"))")
  }

  @Test
  fun `a dependency on a library passes the fence`() {
    val library = ProjectBuilder.builder().withName("library").withParent(root).build()
    val consumer = fencedJvmConsumer()
    consumer.dependOn("implementation", library)

    assertDoesNotThrow { (consumer as ProjectInternal).evaluate() }
  }

  @Test
  fun `a configuration outside the Kotlin source sets may consume a frontend project`() {
    val consumer = fencedJvmConsumer()
    consumer.configurations.create("siteContent")
    consumer.dependOn("siteContent", frontend)

    assertDoesNotThrow { (consumer as ProjectInternal).evaluate() }
  }

  @Test
  fun `a frontend project's own test suite may depend on it`() {
    fencedJvmProject(frontend).dependOn("testImplementation", frontend)

    assertDoesNotThrow { (frontend as ProjectInternal).evaluate() }
  }

  @Test
  fun `the only frontend project gets no fence`() {
    val onlyFrontend = ProjectBuilder.builder().withName("only-frontend").withParent(root).build()

    onlyFrontend.forbidDependenciesOnFrontendProjects(listOf(onlyFrontend.path))

    assertThat(onlyFrontend.plugins.hasPlugin(DependencyFencePlugin::class.java)).isFalse()
  }

  @Test
  fun `a frontend project depending on another frontend project fails the configuration`() {
    fencedJvmProject(frontend).dependOn("implementation", otherFrontend)

    val failure = assertThrows<Exception> { (frontend as ProjectInternal).evaluate() }

    assertThat(failure.messageChain()).contains("implementation(project(\":other-frontend\"))")
  }
}
