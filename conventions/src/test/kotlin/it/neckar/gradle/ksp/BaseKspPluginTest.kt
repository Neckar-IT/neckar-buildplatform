package it.neckar.gradle.ksp

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isInstanceOf
import assertk.assertions.messageContains
import it.neckar.gradle.NeckarOpenVersionProperty
import it.neckar.gradle.ksp.serialization.KspSerializationPlugin
import org.gradle.api.GradleException
import org.gradle.kotlin.dsl.apply
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BaseKspPluginTest {
  @Test
  fun `the processor is added in the version of neckarOpenVersion`(@TempDir projectDir: File) {
    val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
    project.extensions.extraProperties[NeckarOpenVersionProperty] = "10.1.0"
    project.plugins.apply("org.jetbrains.kotlin.jvm")
    project.apply<KspSerializationPlugin>()

    val notations = project.configurations.getByName("ksp").dependencies.map { "${it.group}:${it.name}:${it.version}" }
    assertThat(notations).contains("it.neckar.open.ksp:serialization-verifier:10.1.0")
  }

  @Test
  fun `a missing neckarOpenVersion names the property`(@TempDir projectDir: File) {
    val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
    project.plugins.apply("org.jetbrains.kotlin.jvm")

    assertFailure { project.apply<KspSerializationPlugin>() }
      .transform { generateSequence(it) { cause -> cause.cause }.last() }
      .isInstanceOf<GradleException>()
      .messageContains(NeckarOpenVersionProperty)
  }
}
