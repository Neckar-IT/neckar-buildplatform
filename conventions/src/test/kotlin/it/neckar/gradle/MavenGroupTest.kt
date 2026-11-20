package it.neckar.gradle

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class MavenGroupTest {
  @Test
  fun `the monorepo and the build rooted at internal open name the same group`() {
    assertThat(mavenGroupOf(emptyList(), ":internal:open:commons:kotlin-lang")).isEqualTo("it.neckar.open")
    assertThat(mavenGroupOf(listOf("open"), ":commons:kotlin-lang")).isEqualTo("it.neckar.open")
    assertThat(mavenGroupOf(emptyList(), ":internal:open:rest:ktor-server")).isEqualTo("it.neckar.open.rest")
    assertThat(mavenGroupOf(listOf("open"), ":rest:ktor-server")).isEqualTo("it.neckar.open.rest")
  }

  @Test
  fun `the root project of a build is its root segments alone`() {
    assertThat(mavenGroupOf(listOf("open"), ":")).isEqualTo("it.neckar.open")
    assertThat(mavenGroupOf(emptyList(), ":internal:open")).isEqualTo("it.neckar")
  }

  @Test
  fun `a segment repeating its parent and the closed boundary are dropped, every other directory stays`() {
    assertThat(mavenGroupOf(emptyList(), ":internal:open:meistercharts:meistercharts-api:meistercharts-api-core")).isEqualTo("it.neckar.open.meistercharts")
    assertThat(mavenGroupOf(emptyList(), ":internal:infrastructure:hosts:localhost:mongodb")).isEqualTo("it.neckar.infrastructure.hosts.localhost")
    assertThat(mavenGroupOf(emptyList(), ":internal:closed:db:mongodb")).isEqualTo("it.neckar.db")
  }
}
