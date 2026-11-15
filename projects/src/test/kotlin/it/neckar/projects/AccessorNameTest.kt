package it.neckar.projects

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class AccessorNameTest {
  @Test
  fun `the registry expression names the property of each directory`() {
    assertThat(projectsAccessor(GradleProjectPath(":meistercharts:meistercharts-history:meistercharts-history-core")))
      .isEqualTo("Projects.meistercharts.meisterchartsHistory.meisterchartsHistoryCore")
  }
}
