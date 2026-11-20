package it.neckar.gradle.pnpm.dependency

import assertk.*
import assertk.assertions.*
import it.neckar.projects.GradleProjectPath
import org.gradle.api.GradleException
import org.junit.jupiter.api.Test

class PackageNameRegistryTest {

  private val typescriptUtils = NpmPackageName("@neckar-hub/typescript-utils")
  private val typescriptUtilsPath = GradleProjectPath(":internal:open:commons:typescript:typescript-utils")

  private val registry = PackageNameRegistry(mapOf(typescriptUtils to typescriptUtilsPath))

  @Test
  fun `findGradlePath returns the registered path`() {
    assertThat(registry.findGradlePath(typescriptUtils)).isEqualTo(typescriptUtilsPath)
  }

  @Test
  fun `findGradlePath throws for an unregistered package`() {
    assertFailure { registry.findGradlePath(NpmPackageName("@neckar-hub/not-registered")) }
      .isInstanceOf(GradleException::class)
      .message().isNotNull().all {
        contains("@neckar-hub/not-registered")
        contains("settings.gradle.kts")
        contains("Registered packages: 1")
      }
  }

  @Test
  fun `findGradlePathOrNull returns null for an unregistered package`() {
    assertThat(registry.findGradlePathOrNull(NpmPackageName("@neckar-hub/not-registered"))).isNull()
  }

  @Test
  fun `size counts the registered packages`() {
    assertThat(registry.size).isEqualTo(1)
    assertThat(PackageNameRegistry(emptyMap()).size).isEqualTo(0)
  }
}
