package it.neckar.gradle

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

/**
 * The injection coordinates are a wire contract: deployed compose files, HTML templates, and the
 * runtime GitProperty enum generated into version-info all reference these names. Renaming a
 * constant here silently breaks resolution in already-deployed artifacts — this test makes such a
 * rename an explicit decision.
 */
class GitPropertyTest {
  @Test
  fun `injection coordinates are a wire contract`() {
    assertThat(GitProperty.Hash.envVar).isEqualTo("APP_GIT_HASH")
    assertThat(GitProperty.Hash.systemProperty).isEqualTo("app.git.hash")
    assertThat(GitProperty.Hash.propertyKey).isEqualTo("gitHash")

    assertThat(GitProperty.CommitDateTime.envVar).isEqualTo("APP_GIT_COMMIT_DATETIME")
    assertThat(GitProperty.CommitDateTime.systemProperty).isEqualTo("app.git.commitDateTime")
    assertThat(GitProperty.CommitDateTime.propertyKey).isEqualTo("gitCommitDateTime")
  }

  @Test
  fun `only per-commit deterministic properties are injected`() {
    //Build date (build-process metadata) and branch (not a function of the commit) must not
    //appear here — an artifact is a pure function of the commit
    assertThat(GitProperty.entries).containsExactly(GitProperty.Hash, GitProperty.CommitDateTime)
  }
}
