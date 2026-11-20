package it.neckar.gradle.fence

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ForbidRuleTest {
  @TempDir
  lateinit var projectDir: File

  private lateinit var root: Project
  private lateinit var openModule: Project
  private lateinit var openModuleRule: ForbidRule

  @BeforeEach
  fun createProjects() {
    root = ProjectBuilder.builder().withProjectDir(projectDir).build()
    // The project producing `it.neckar.db:closed-library`, which the root build substitutes the coordinate by.
    module("closed-library", "it.neckar.db")
    openModule = module("open-module", "it.neckar.open")
    openModuleRule = ForbidRule(openModule).apply {
      reason = "open modules publish to Maven Central"
      inConfigurations("implementation")
      forbidProjectsMatching(Regex("""^:closed-library$"""))
    }
  }

  /** The root build substitutes the coordinate by the closed project, so the published module would name a closed artifact. */
  @Test
  fun `a coordinate a forbidden project produces is a violation`() {
    openModule.dependencies.add("implementation", "it.neckar.db:closed-library:1.0.0")

    assertThat(openModuleRule.findViolations().map { it.dependencyDisplay }).containsExactly("\"it.neckar.db:closed-library:1.0.0\" (project :closed-library)")
  }

  @Test
  fun `a third-party coordinate passes the project rules`() {
    openModule.dependencies.add("implementation", "org.apache.pdfbox:pdfbox:3.0.8")

    assertThat(openModuleRule.findViolations()).isEmpty()
  }

  @Test
  fun `a coordinate a path-forbidden project produces is an exact match`() {
    val rule = ForbidRule(openModule).apply {
      reason = "open modules publish to Maven Central"
      inConfigurations("implementation")
      forbidProjectPaths(":closed-library")
    }
    openModule.dependencies.add("implementation", "it.neckar.db:closed-library:1.0.0")

    assertThat(rule.findViolations().map { it.matchedBy }).containsExactly("exact match (forbidProjects / forbidProjectPaths)")
  }

  @Test
  fun `a forbidden project leaves the compile classpaths of the rule's scope`() {
    val rule = ForbidRule(openModule).apply {
      reason = "a migration writes the stored shape of a business module as raw BSON"
      forbidProjectPaths(":closed-library")
      alsoInTestConfigurations()
    }

    rule.excludeFromCompileClasspaths()

    listOf("compileClasspath", "testCompileClasspath").forEach { name ->
      assertThat(openModule.configurations.getByName(name).excludeRules.map { "${it.group}:${it.module}" }, name).containsExactly("it.neckar.db:closed-library")
    }
    assertThat(openModule.configurations.getByName("runtimeClasspath").excludeRules).isEmpty()
  }

  @Test
  fun `a pattern-forbidden project leaves the main compile classpath only`() {
    openModuleRule.excludeFromCompileClasspaths()

    assertThat(openModule.configurations.getByName("compileClasspath").excludeRules.map { "${it.group}:${it.module}" }).containsExactly("it.neckar.db:closed-library")
    assertThat(openModule.configurations.getByName("testCompileClasspath").excludeRules).isEmpty()
  }

  @Test
  fun `a pattern matching the fenced project itself excludes only the other projects`() {
    val rule = ForbidRule(openModule).apply {
      reason = "annotations must not pull in any other internal project"
      forbidProjectsMatching(Regex("""^:.+$"""))
    }

    rule.excludeFromCompileClasspaths()

    assertThat(openModule.configurations.getByName("compileClasspath").excludeRules.map { "${it.group}:${it.module}" }).containsExactly("it.neckar.db:closed-library")
  }

  private fun module(name: String, group: String): Project = ProjectBuilder.builder().withName(name).withParent(root).build().also { module ->
    module.group = group
    module.pluginManager.apply("java-library")
  }
}
