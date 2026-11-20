package it.neckar.gradle.fence

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class OpenModulePluginTest {
  @TempDir
  lateinit var projectDir: File

  private val root: Project by lazy { ProjectBuilder.builder().withProjectDir(projectDir).build() }
  private val closedLibrary: Project by lazy { module("closed-library", "it.neckar.db") }
  private val openModule: Project by lazy { module("open-module", "it.neckar.open") }

  /** Build-logic artifacts reach the private package registry alone; a closed project never reaches Maven Central. */
  @Test
  fun `the open-module fence rejects a build-logic coordinate and a closed project`() {
    openModule.pluginManager.apply(OpenModulePlugin::class.java)
    openModule.dependencies.add("implementation", "it.neckar.build-logic:core:1.0.0")
    openModule.dependencies.add("implementation", openModule.dependencies.project(mapOf("path" to closedLibrary.path)))

    val violations = openModule.extensions.getByType(DependencyFenceExtension::class.java).findViolations()

    assertThat(violations.map { it.dependencyDisplay }).containsExactly("\"it.neckar.build-logic:core:1.0.0\"", "project(\":closed-library\")")
  }

  /** In the monorepo the open tree starts at `:internal:open`, the topmost project applying the plugin. */
  @Test
  fun `below the topmost open project a sibling passes and a project outside fails`() {
    val openTree = ProjectBuilder.builder().withName("open").withParent(root).build()
    openTree.pluginManager.apply(OpenModulePlugin::class.java)
    val sibling = ProjectBuilder.builder().withName("sibling").withParent(openTree).build().also { it.pluginManager.apply("java-library") }
    val module = ProjectBuilder.builder().withName("module").withParent(openTree).build().also { it.pluginManager.apply("java-library") }
    module.pluginManager.apply(OpenModulePlugin::class.java)
    module.dependencies.add("implementation", module.dependencies.project(mapOf("path" to sibling.path)))
    module.dependencies.add("implementation", module.dependencies.project(mapOf("path" to closedLibrary.path)))

    val violations = module.extensions.getByType(DependencyFenceExtension::class.java).findViolations()

    assertThat(violations.map { it.dependencyDisplay }).containsExactly("project(\":closed-library\")")
  }

  /** The export of `internal/open` is a build whose root project applies the plugin: every project of it is open. */
  @Test
  fun `in a build whose root project applies the plugin every project passes`() {
    root.pluginManager.apply(OpenModulePlugin::class.java)
    val module = module("module", "it.neckar.open")
    module.pluginManager.apply(OpenModulePlugin::class.java)
    module.dependencies.add("implementation", module.dependencies.project(mapOf("path" to closedLibrary.path)))

    val violations = module.extensions.getByType(DependencyFenceExtension::class.java).findViolations()

    assertThat(violations).isEmpty()
  }

  /** A subproject of [root] with [group] and `java-library`. */
  private fun module(name: String, group: String): Project = ProjectBuilder.builder().withName(name).withParent(root).build().also { module ->
    module.group = group
    module.pluginManager.apply("java-library")
  }
}
