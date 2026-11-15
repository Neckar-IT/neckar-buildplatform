package it.neckar.projects

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.Test

class GradleTaskPathTest {

  @Test
  fun `derives task name and project path`() {
    val taskPath = GradleTaskPath(":internal:patterns:ktor-backend:openapiSpec")
    assertThat(taskPath.taskName).isEqualTo("openapiSpec")
    assertThat(taskPath.projectPath).isEqualTo(GradleProjectPath(":internal:patterns:ktor-backend"))
  }

  @Test
  fun `root project task maps to root path`() {
    val taskPath = GradleTaskPath(":build")
    assertThat(taskPath.taskName).isEqualTo("build")
    assertThat(taskPath.projectPath).isEqualTo(GradleProjectPath(":"))
  }

  @Test
  fun `rejects path without leading colon`() {
    assertFailure { GradleTaskPath("internal:build") }
      .isInstanceOf(IllegalArgumentException::class)
  }

  @Test
  fun `rejects path without task name`() {
    assertFailure { GradleTaskPath(":internal:patterns:") }
      .isInstanceOf(IllegalArgumentException::class)
  }
}
