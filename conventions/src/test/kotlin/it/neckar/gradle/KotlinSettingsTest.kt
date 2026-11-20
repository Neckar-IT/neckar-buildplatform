package it.neckar.gradle

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsNone
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.junit.jupiter.api.Test

/**
 * [KotlinSettings] is the single place where the compiler arguments of every Kotlin compilation in
 * the repository are assembled. The list is hand-maintained across Kotlin upgrades, so these tests
 * pin the invariants that a careless edit breaks silently: duplicated flags, an opt-in annotation
 * that never reaches the compiler, and a JVM target that drifts from the toolchain.
 */
class KotlinSettingsTest {

  @Test
  fun `language version and api version are reported consistently as strings`() {
    assertThat(KotlinSettings.languageVersionAsString).isEqualTo(KotlinSettings.languageVersion.version)
    assertThat(KotlinSettings.apiVersionAsString).isEqualTo(KotlinSettings.apiVersion.version)
  }

  /**
   * Compiling against a newer API than the language version accepts does not work, so the api
   * version must never be ahead of the language version.
   */
  @Test
  fun `the api version is not newer than the language version`() {
    assertThat(KotlinSettings.apiVersion <= KotlinSettings.languageVersion).isTrue()
  }

  @Test
  fun `every opted-in annotation reaches the compiler exactly once`() {
    KotlinSettings.optInExperimentalAnnotations.forEach { annotation ->
      assertThat(KotlinSettings.freeCompilerArgs.count { it == "-opt-in=$annotation" }).isEqualTo(1)
    }
  }

  @Test
  fun `the opt-in list has no duplicates`() {
    assertThat(duplicatesOf(KotlinSettings.optInExperimentalAnnotations)).isEmpty()
  }

  @Test
  fun `the common compiler args have no duplicates`() {
    assertThat(duplicatesOf(KotlinSettings.freeCompilerArgs)).isEmpty()
  }

  @Test
  fun `the js compiler args have no duplicates and do not repeat the common args`() {
    assertThat(duplicatesOf(KotlinSettings.additionalFreeCompilerArgsJS)).isEmpty()
    assertThat(KotlinSettings.additionalFreeCompilerArgsJS).containsNone(*KotlinSettings.freeCompilerArgs.toTypedArray())
  }

  @Test
  fun `the jvm compiler args have no duplicates and do not repeat the common args`() {
    val jvmArgs = KotlinSettings.additionalFreeCompilerArgsJVM(enhancedCoroutinesDebugging = false)

    assertThat(duplicatesOf(jvmArgs)).isEmpty()
    assertThat(jvmArgs).containsNone(*KotlinSettings.freeCompilerArgs.toTypedArray())
  }

  /**
   * `-Xjdk-release` also pins `-jvm-target`, so a JDK API newer than the pinned release is rejected
   * at compile time. It is the guard against an unnoticed build-JDK bump and must name a concrete
   * target, not a formatted string that happens to look like one.
   */
  @Test
  fun `the jvm args pin the jdk release to the configured jvm target`() {
    assertThat(KotlinSettings.additionalFreeCompilerArgsJVM(enhancedCoroutinesDebugging = false))
      .contains("-Xjdk-release=${JvmTarget.JVM_25.target}")
  }

  @Test
  fun `enhanced coroutines debugging is off by default and added on request`() {
    assertThat(KotlinSettings.additionalFreeCompilerArgsJVM(enhancedCoroutinesDebugging = false))
      .doesNotContain("-Xenhanced-coroutines-debugging")
    assertThat(KotlinSettings.additionalFreeCompilerArgsJVM(enhancedCoroutinesDebugging = true))
      .contains("-Xenhanced-coroutines-debugging")
  }

  /**
   * The two flags differ only in the toggle, so everything else has to stay identical — a flag
   * accidentally guarded by the toggle would silently disappear from every regular build.
   */
  @Test
  fun `the coroutines debugging toggle changes nothing but that one flag`() {
    val withToggle = KotlinSettings.additionalFreeCompilerArgsJVM(enhancedCoroutinesDebugging = true)
    val withoutToggle = KotlinSettings.additionalFreeCompilerArgsJVM(enhancedCoroutinesDebugging = false)

    assertThat(withToggle - "-Xenhanced-coroutines-debugging").isEqualTo(withoutToggle)
  }

  /**
   * `-Xir-dce-print-reachability-info` only produces output in combination with `-Xir-dce`; on its
   * own it is a no-op that reads like an active diagnostic. Both are permanent JS code-generation
   * policy, so they are pinned as a pair — dropping the diagnostic is a deliberate decision that
   * updates this test rather than a silent edit.
   */
  @Test
  fun `the js dead code elimination and its reachability report are configured as a pair`() {
    assertThat(KotlinSettings.additionalFreeCompilerArgsJS).contains("-Xir-dce")
    assertThat(KotlinSettings.additionalFreeCompilerArgsJS).contains("-Xir-dce-print-reachability-info")
  }

  @Test
  fun `every compiler arg is a flag, not an empty or unprefixed string`() {
    val allArgs = KotlinSettings.freeCompilerArgs +
      KotlinSettings.additionalFreeCompilerArgsJS +
      KotlinSettings.additionalFreeCompilerArgsJVM(enhancedCoroutinesDebugging = true)

    assertThat(allArgs.filter { it.startsWith("-").not() }).isEmpty()
    assertThat(allArgs.filter { it != it.trim() }).isEmpty()
  }

  /**
   * An inferred return type is style: `strict` would fail the build on valid code.
   */
  @Test
  fun `production compilations warn on inferred return types and test compilations stay silent`() {
    assertThat(KotlinSettings.explicitReturnTypesArg(KotlinSettings.CompilationScope.Production)).isEqualTo("-XXexplicit-return-types=warning")
    assertThat(KotlinSettings.explicitReturnTypesArg(KotlinSettings.CompilationScope.Test)).isEqualTo("-XXexplicit-return-types=disable")
  }

  @Test
  fun `compile tasks are told apart by the Test in their name`() {
    assertThat(KotlinSettings.CompilationScope.ofCompileTask("compileKotlinJvm")).isEqualTo(KotlinSettings.CompilationScope.Production)
    assertThat(KotlinSettings.CompilationScope.ofCompileTask("compileCommonMainKotlinMetadata")).isEqualTo(KotlinSettings.CompilationScope.Production)
    assertThat(KotlinSettings.CompilationScope.ofCompileTask("compileKotlinJs")).isEqualTo(KotlinSettings.CompilationScope.Production)
    assertThat(KotlinSettings.CompilationScope.ofCompileTask("compileProductionExecutableKotlinJs")).isEqualTo(KotlinSettings.CompilationScope.Production)
    assertThat(KotlinSettings.CompilationScope.ofCompileTask("compileTestDevelopmentExecutableKotlinJs")).isEqualTo(KotlinSettings.CompilationScope.Test)
    assertThat(KotlinSettings.CompilationScope.ofCompileTask("compileTestKotlinJvm")).isEqualTo(KotlinSettings.CompilationScope.Test)
    assertThat(KotlinSettings.CompilationScope.ofCompileTask("compileTestFixturesKotlin")).isEqualTo(KotlinSettings.CompilationScope.Test)
    assertThat(KotlinSettings.CompilationScope.ofCompileTask("compileFunctionalTestKotlin")).isEqualTo(KotlinSettings.CompilationScope.Test)
    assertThat(KotlinSettings.CompilationScope.ofCompileTask("compilePdfExamplesTestKotlin")).isEqualTo(KotlinSettings.CompilationScope.Test)
  }

  /**
   * A second value in a shared list would reach every compilation next to the per-compilation one.
   */
  @Test
  fun `the return type flag is set per compilation, never in a shared arg list`() {
    val sharedArgs = KotlinSettings.freeCompilerArgs +
      KotlinSettings.additionalFreeCompilerArgsJS +
      KotlinSettings.additionalFreeCompilerArgsJVM(enhancedCoroutinesDebugging = true)

    assertThat(sharedArgs.filter { it.startsWith("-XXexplicit-return-types") }).isEmpty()
  }

  @Test
  fun `the configured language version is a known Kotlin version`() {
    assertThat(KotlinVersion.entries).contains(KotlinSettings.languageVersion)
  }

  private fun duplicatesOf(values: List<String>): List<String> =
    values.groupBy { it }.filterValues { it.size > 1 }.keys.toList()
}
