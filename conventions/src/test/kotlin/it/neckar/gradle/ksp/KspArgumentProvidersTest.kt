package it.neckar.gradle.ksp

import assertk.assertThat
import assertk.assertions.containsAtLeast
import assertk.assertions.isEmpty
import com.google.devtools.ksp.gradle.KspExtension
import it.neckar.gradle.NeckarOpenVersionProperty
import it.neckar.gradle.ksp.generating.ts.generateTypeScriptDefinitions
import it.neckar.gradle.ksp.generating.ts.TypescriptDefinitionGenerationPlugin
import it.neckar.gradle.ksp.serialization.KspSerializationPlugin
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The configuration cache serializes every KSP argument provider into each KSP task; a captured [Project] fails the store.
 */
class KspArgumentProvidersTest {
  @Test
  fun `the common arguments capture no Project`(@TempDir projectDir: File) {
    val project = projectWithNeckarOpenVersion(projectDir)
    project.plugins.apply("org.jetbrains.kotlin.jvm")
    project.apply<KspSerializationPlugin>()

    val providers = project.kspArgumentProviders()

    assertThat(providers.flatMap { it.asArguments() }).containsAtLeast("projectPath=${project.path}", "projectDir=${projectDir.absolutePath}")
    assertThat(providers.flatMap { it.capturedProjectFields() }).isEmpty()
  }

  @Test
  fun `the TypeScript definition arguments capture no Project and read the file name set after apply`(@TempDir projectDir: File) {
    val project = projectWithNeckarOpenVersion(projectDir)
    project.plugins.apply("org.jetbrains.kotlin.multiplatform")
    project.extensions.configure<KotlinMultiplatformExtension> { js { browser() } }
    project.apply<TypescriptDefinitionGenerationPlugin>()
    project.generateTypeScriptDefinitions { targetTypescriptDefinitionFileName = "index.d.ts" }

    val providers = project.kspArgumentProviders()

    assertThat(providers.flatMap { it.asArguments() }).containsAtLeast(
      "projectPath=${project.path}",
      "typeScriptDefinitionFileName=index.d.ts",
      "typesListForProjectCollectionFilePath=${projectDir.resolve("build/types-lists/modules/types-collection-from-modules.json").absolutePath}",
    )
    assertThat(providers.flatMap { it.capturedProjectFields() }).isEmpty()
  }

  private fun projectWithNeckarOpenVersion(projectDir: File): Project {
    return ProjectBuilder.builder().withProjectDir(projectDir).build().also {
      it.extensions.extraProperties[NeckarOpenVersionProperty] = "10.1.0"
    }
  }

  /** `KspExtension.commandLineArgumentProviders` is internal to the KSP plugin. */
  private fun Project.kspArgumentProviders(): List<CommandLineArgumentProvider> {
    val field = KspExtension::class.java.getDeclaredField("commandLineArgumentProviders").apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST")
    return (field.get(extensions.getByType<KspExtension>()) as ListProperty<CommandLineArgumentProvider>).get()
  }

  private fun CommandLineArgumentProvider.capturedProjectFields(): List<String> {
    return javaClass.declaredFields
      .filter { Project::class.java.isAssignableFrom(it.type) }
      .map { "${javaClass.name}.${it.name}" }
  }
}
