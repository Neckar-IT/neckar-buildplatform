package it.neckar.gradle

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.kotlin.dsl.apply
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DependenciesTest {
  @TempDir
  lateinit var projectDir: File

  /**
   * The configuration a dependency lands in is what decides whether it is visible to consumers:
   * `api` exports it transitively, `testImplementation` keeps it inside the test compilation. A
   * swapped mapping would export every test dependency of every module.
   */
  @Test
  fun `the scope decides the target configuration`() {
    assertThat(Scope.Main.configurationName()).isEqualTo("api")
    assertThat(Scope.Test.configurationName()).isEqualTo("testImplementation")
  }

  @Test
  fun `there is a scope for the main and for the test source set`() {
    assertThat(Scope.entries).containsExactly(Scope.Main, Scope.Test)
  }

  /**
   * `hasJsTarget` gates every JS source-set configuration: without the check, a JVM-only
   * multiplatform project would get `jsMain`/`jsTest` source sets that have no target, which Gradle
   * reports as "Source Set Used Without a Corresponding Target".
   */
  @Test
  fun `hasJsTarget reflects whether a js target is registered`() {
    val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
    project.apply(plugin = "org.jetbrains.kotlin.multiplatform")

    val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)

    assertThat(kotlin.hasJsTarget).isFalse()

    kotlin.js()

    assertThat(kotlin.hasJsTarget).isTrue()
  }
}
