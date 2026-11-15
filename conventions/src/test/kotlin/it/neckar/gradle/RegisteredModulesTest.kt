package it.neckar.gradle

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import assertk.assertions.rootCause
import it.neckar.projects.ConfiguredProject
import it.neckar.projects.KotlinTarget
import it.neckar.projects.ProjectRole
import it.neckar.projects.ProjectRoot
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.kotlin.dsl.extra
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The export of `internal/open` configures its Kotlin modules through [configureRegisteredKotlinModules] alone; a type that loses its
 * conventions there fails no build in the monorepo.
 */
class RegisteredModulesTest {

  @TempDir
  lateinit var rootDir: File

  private val root: Project by lazy {
    ProjectBuilder.builder().withProjectDir(rootDir).build().also { it.extra[NeckarOpenVersionProperty] = "10.1.0" }
  }

  private fun module(name: String): Project {
    return ProjectBuilder.builder().withName(name).withParent(root).withProjectDir(rootDir.resolve(name)).build()
  }

  private fun configure(projects: List<ConfiguredProject>) {
    configureRegisteredKotlinModules(
      baseProject = root,
      projects = projects,
      kspProcessorPathPrefix = ":ksp",
      allowedKspDependencyPaths = emptySet(),
      registrationOf = { ProjectRegistration(file = "Registry.kt", accessor = "Projects.${it.path.path.removePrefix(":")}") },
    )
  }

  @Test
  fun `each Kotlin module gets the conventions of its type`() {
    val multiplatform = module("multiplatform")
    val jvm = module("jvm")

    configure(listOf(TestRegistry.multiplatform, TestRegistry.jvm))

    assertThat(multiplatform.plugins.hasPlugin(BuildplatformPlugins.KotlinMultiPlatform)).isTrue()
    assertThat(jvm.plugins.hasPlugin(BuildplatformPlugins.KotlinJvm)).isTrue()
  }

  @Test
  fun `a KSP processor alone gets the dependency check of a processor`() {
    val jvm = module("jvm")
    val processor = module("processor")
    val outside = module("outside")

    configure(listOf(TestRegistry.jvm, TestRegistry.processor))
    jvm.dependencies.add("api", outside)
    processor.dependencies.add("api", jvm)

    (jvm as ProjectInternal).evaluate()
    assertFailure { (processor as ProjectInternal).evaluate() }
      .rootCause()
      .messageContains("Project dependencies not allowed for KSP processor project [:processor]: but has [:outside, :jvm]")
  }

  @Test
  fun `a pnpm module fails, since it has no Kotlin conventions`() {
    module("package")

    assertFailure { configure(listOf(TestRegistry.pnpmPackage)) }
      .isInstanceOf<IllegalArgumentException>()
      .messageContains(":package is of type Library, which has no Kotlin module conventions")
  }
}

private object TestRegistry : ProjectRoot() {
  val multiplatform: ConfiguredProject = multiplatform("multiplatform", ProjectRole.Library, KotlinTarget.Jvm)
  val jvm: ConfiguredProject = jvm("jvm", ProjectRole.Library)
  val processor: ConfiguredProject = kspProcessor("processor", ProjectRole.Library)
  val pnpmPackage: ConfiguredProject = pnpm("package", ProjectRole.Library)
}
