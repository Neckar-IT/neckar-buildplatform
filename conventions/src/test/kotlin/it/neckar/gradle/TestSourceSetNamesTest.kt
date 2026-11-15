package it.neckar.gradle

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

class TestSourceSetNamesTest {
  @Test
  fun `test, testFixtures and every name ending in Test or Tests are test source sets`() {
    listOf("test", "testFixtures", "integrationTest", "jvmTest", "linuxX64Test", "chromeTests", "e2eTests").forEach {
      assertThat(TestSourceSetNames.isTestSourceSet(it), name = it).isTrue()
    }
  }

  @Test
  fun `names without the Test or Tests suffix are not test source sets`() {
    listOf("main", "jvmMain", "testData", "jmh", "pdfExamples").forEach {
      assertThat(TestSourceSetNames.isTestSourceSet(it), name = it).isFalse()
    }
  }

  @Test
  fun `a file path counts by the directory right below src`() {
    assertThat(TestSourceSetNames.isInTestSourceSet("internal/sample/sample-e2e-tests/src/e2eTests/kotlin/A.kt")).isTrue()
    assertThat(TestSourceSetNames.isInTestSourceSet("internal/sample/mongodb/src/testFixtures/kotlin/A.kt")).isTrue()
    assertThat(TestSourceSetNames.isInTestSourceSet("internal/open/foo/src/main/kotlin/it/neckar/test/A.kt")).isFalse()
    assertThat(TestSourceSetNames.isInTestSourceSet("internal/open/foo/src/fooTest")).isFalse()
  }
}
