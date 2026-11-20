package it.neckar.projects

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.messageContains
import org.junit.jupiter.api.Test

class GradleProjectPathTest {

  @Test
  fun `task appends the task name to the project path`() {
    assertThat(GradleProjectPath(":internal:patterns:ktor-backend").task("build"))
      .isEqualTo(GradleTaskPath(":internal:patterns:ktor-backend:build"))
  }

  @Test
  fun `task of the root project yields the single-colon form Gradle expects`() {
    assertThat(GradleProjectPath(":").task("build")).isEqualTo(GradleTaskPath(":build"))
  }

  @Test
  fun `filePath turns the gradle path into a relative directory path`() {
    assertThat(GradleProjectPath(":internal:patterns:ktor-backend").filePath).isEqualTo("internal/patterns/ktor-backend")
  }

  @Test
  fun `filePath of a top level project is the bare name`() {
    assertThat(GradleProjectPath(":internal").filePath).isEqualTo("internal")
  }

  /**
   * The Gradle root project's path is `":"`. The registry lookups wrap every project path — including
   * the root's — in a [GradleProjectPath], so the bare colon must stay constructible.
   */
  @Test
  fun `the root path is accepted and maps to an empty file path`() {
    assertThat(GradleProjectPath(":").filePath).isEqualTo("")
  }

  @Test
  fun `a path without a leading colon is rejected`() {
    assertFailure { GradleProjectPath("internal:patterns:ktor-backend") }
      .messageContains("must start with ':'")
  }

  @Test
  fun `an empty path is rejected`() {
    assertFailure { GradleProjectPath("") }
      .messageContains("must start with ':'")
  }

  @Test
  fun `toString returns the raw path`() {
    assertThat(GradleProjectPath(":internal:open:commons").toString()).isEqualTo(":internal:open:commons")
  }

  @Test
  fun `resolve appends a relative path to its root`() {
    assertThat(GradleProjectPath(":internal:open").resolve(GradleProjectPath(":commons:kotlin-lang")))
      .isEqualTo(GradleProjectPath(":internal:open:commons:kotlin-lang"))
  }

  @Test
  fun `resolve against the root project keeps the relative path`() {
    assertThat(GradleProjectPath.Root.resolve(GradleProjectPath(":commons:kotlin-lang")))
      .isEqualTo(GradleProjectPath(":commons:kotlin-lang"))
  }

  @Test
  fun `resolve of the relative root is the root itself`() {
    assertThat(GradleProjectPath(":internal:open").resolve(GradleProjectPath.Root)).isEqualTo(GradleProjectPath(":internal:open"))
  }

  @Test
  fun `relativizeOrNull strips the root`() {
    assertThat(GradleProjectPath(":internal:open").relativizeOrNull(GradleProjectPath(":internal:open:commons:kotlin-lang")))
      .isEqualTo(GradleProjectPath(":commons:kotlin-lang"))
    assertThat(GradleProjectPath(":internal:open").relativizeOrNull(GradleProjectPath(":internal:open")))
      .isEqualTo(GradleProjectPath.Root)
    assertThat(GradleProjectPath.Root.relativizeOrNull(GradleProjectPath(":commons:kotlin-lang")))
      .isEqualTo(GradleProjectPath(":commons:kotlin-lang"))
  }

  /**
   * `:internal:openx` shares the prefix `:internal:open` as a string but is a sibling, not a child.
   */
  @Test
  fun `relativizeOrNull returns null outside the root`() {
    assertThat(GradleProjectPath(":internal:open").relativizeOrNull(GradleProjectPath(":internal:patterns:ktor-backend"))).isNull()
    assertThat(GradleProjectPath(":internal:open").relativizeOrNull(GradleProjectPath(":internal:openx"))).isNull()
  }
}
