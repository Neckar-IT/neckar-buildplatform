package it.neckar.gradle

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

/**
 * Detekt only blocks a merge when its bundled Kotlin compiler matches the language version the
 * repository compiles with. Getting that decision wrong is invisible in both directions: too strict
 * fails every build on resolution artefacts, too lenient silently downgrades Detekt to
 * non-blocking and lets real findings through.
 */
class DetektStrictModeTest {

  @Test
  fun `a matching major minor enables strict mode`() {
    assertThat(
      detektStrictModeEnabled(detektVersion = "2.0.0", bundledKotlinVersion = "2.4.0", languageVersion = "2.4")
    ).isTrue()
  }

  @Test
  fun `a differing major minor disables strict mode`() {
    assertThat(
      detektStrictModeEnabled(detektVersion = "2.0.0", bundledKotlinVersion = "2.3.21", languageVersion = "2.4")
    ).isFalse()
  }

  @Test
  fun `a blocked detekt version disables strict mode despite a matching compiler`() {
    DetektStrictModeBlockedVersions.forEach { blockedVersion ->
      assertThat(
        detektStrictModeEnabled(detektVersion = blockedVersion, bundledKotlinVersion = "2.4.0", languageVersion = "2.4")
      ).isFalse()
    }
  }

  /**
   * Both detections return `"unknown"` when the `detekt` configuration cannot be resolved, so the
   * undetected case must fall back to non-blocking rather than to strict.
   */
  @Test
  fun `an undetected version disables strict mode`() {
    assertThat(
      detektStrictModeEnabled(detektVersion = "unknown", bundledKotlinVersion = "unknown", languageVersion = "2.4")
    ).isFalse()
    assertThat(
      detektStrictModeEnabled(detektVersion = null, bundledKotlinVersion = null, languageVersion = "2.4")
    ).isFalse()
  }

  @Test
  fun `isMajorMinorEqual compares major and minor only`() {
    assertThat(isMajorMinorEqual("2.3.21", "2.3")).isTrue()
    assertThat(isMajorMinorEqual("2.3", "2.3.21")).isTrue()
    assertThat(isMajorMinorEqual("2.3.21", "2.3.99")).isTrue()
    assertThat(isMajorMinorEqual("2.3.21", "2.4")).isFalse()
    assertThat(isMajorMinorEqual("2.3.21", "3.3")).isFalse()
  }

  @Test
  fun `isMajorMinorEqual rejects null and malformed versions`() {
    assertThat(isMajorMinorEqual(null, "2.4")).isFalse()
    assertThat(isMajorMinorEqual("2.4", null)).isFalse()
    assertThat(isMajorMinorEqual("unknown", "2.4")).isFalse()
    assertThat(isMajorMinorEqual("2", "2.4")).isFalse()
    assertThat(isMajorMinorEqual("2.x", "2.4")).isFalse()
    assertThat(isMajorMinorEqual("", "")).isFalse()
  }

  @Test
  fun `the kotlin compiler jar pattern extracts the version`() {
    assertThat(versionOf(KotlinCompilerJarPattern, "kotlin-compiler-2.4.0.jar")).isEqualTo("2.4.0")
    assertThat(versionOf(KotlinCompilerJarPattern, "kotlin-compiler-2.4.jar")).isEqualTo("2.4")
  }

  @Test
  fun `the kotlin compiler jar pattern ignores neighbouring artifacts`() {
    assertThat(versionOf(KotlinCompilerJarPattern, "kotlin-compiler-embeddable-2.4.0.jar")).isNull()
    assertThat(versionOf(KotlinCompilerJarPattern, "kotlin-compiler-runner-2.4.0.jar")).isNull()
    assertThat(versionOf(KotlinCompilerJarPattern, "kotlin-compiler-2.4.0-sources.jar")).isNull()
  }

  @Test
  fun `the detekt api jar pattern extracts the plain version including a pre-release suffix`() {
    assertThat(versionOf(DetektApiJarPattern, "detekt-api-2.0.0-alpha.5.jar")).isEqualTo("2.0.0-alpha.5")
    assertThat(versionOf(DetektApiJarPattern, "detekt-api-1.23.8.jar")).isEqualTo("1.23.8")
  }

  /**
   * `detekt-api-test-fixtures-<version>.jar` shares the `detekt-api-` prefix; matching it would
   * report a version string starting with `test-fixtures-`.
   */
  @Test
  fun `the detekt api jar pattern ignores the test fixtures artifact`() {
    assertThat(versionOf(DetektApiJarPattern, "detekt-api-test-fixtures-2.0.0.jar")).isNull()
  }

  private fun versionOf(pattern: Regex, fileName: String): String? =
    pattern.matchEntire(fileName)?.groupValues?.get(1)
}
