package it.neckar.projects.common

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import org.junit.jupiter.api.Test

class MavenCoordinateTest {
  @Test
  fun `the notation is group, name and the version when it carries one`() {
    assertThat(MavenCoordinate("it.neckar.open", "app").toString()).isEqualTo("it.neckar.open:app")
    assertThat(MavenCoordinate("it.neckar.open", "app", "10.1.0").toString()).isEqualTo("it.neckar.open:app:10.1.0")
    assertThat(MavenCoordinate("it.neckar.open", "app", "10.1.0").module).isEqualTo("it.neckar.open:app")
  }

  @Test
  fun `a default version fills in a missing one alone`() {
    assertThat(MavenCoordinate("it.neckar.open", "app").withDefaultVersion("10.1.0").version).isEqualTo("10.1.0")
    assertThat(MavenCoordinate("it.neckar.open", "app", "9.0.0").withDefaultVersion("10.1.0").version).isEqualTo("9.0.0")
  }

  @Test
  fun `a part with a colon or without text is refused`() {
    assertFailure { MavenCoordinate("it.neckar:open", "app") }.messageContains("group must be")
    assertFailure { MavenCoordinate("it.neckar.open", "") }.messageContains("name must be")
    assertFailure { MavenCoordinate("it.neckar.open", "app", " ") }.messageContains("version must be")
  }

  @Test
  fun `a group lies below a parent group by its dotted prefix`() {
    assertThat(MavenCoordinate.isGroupAtOrBelow("it.neckar.open.ksp", "it.neckar.open")).isTrue()
    assertThat(MavenCoordinate.isGroupAtOrBelow("it.neckar.open", "it.neckar.open")).isTrue()
    assertThat(MavenCoordinate.isGroupAtOrBelow("it.neckar.openapi", "it.neckar.open")).isFalse()
    assertThat(MavenCoordinate.isNeckarGroup("it.neckar")).isTrue()
    assertThat(MavenCoordinate.isNeckarGroup("org.apache.pdfbox")).isFalse()
  }
}
