package it.neckar.gradle.modulegroup

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class ModuleGroupPrefixTest {
  @Test
  fun `a plain directory name is the prefix`() {
    assertThat(ModuleGroupPrefix.fromDirectoryName("turboangebot")).isEqualTo(ModuleGroupPrefix("turboangebot"))
  }

  @Test
  fun `the domain a service deploys under is dropped`() {
    assertThat(ModuleGroupPrefix.fromDirectoryName("auto-merge.neckar.it")).isEqualTo(ModuleGroupPrefix("auto-merge"))
    assertThat(ModuleGroupPrefix.fromDirectoryName("klk-steuerberatung.de")).isEqualTo(ModuleGroupPrefix("klk-steuerberatung"))
    assertThat(ModuleGroupPrefix.fromDirectoryName("git.neckar.it")).isEqualTo(ModuleGroupPrefix("git"))
  }

  @Test
  fun `the visibility suffix is dropped`() {
    assertThat(ModuleGroupPrefix.fromDirectoryName("meistercharts-closed")).isEqualTo(ModuleGroupPrefix("meistercharts"))
  }

  /** A nested group is read from its own directory name, not from the product above it. */
  @Test
  fun `a nested group uses its own name`() {
    assertThat(ModuleGroupPrefix.fromDirectoryName("meistercharts-closed-demos")).isEqualTo(ModuleGroupPrefix("meistercharts-closed-demos"))
    assertThat(ModuleGroupPrefix.fromDirectoryName("spec-editor")).isEqualTo(ModuleGroupPrefix("spec-editor"))
  }
}
