package it.neckar.gradle.pnpm

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import it.neckar.projects.GradleProjectPath
import org.junit.jupiter.api.Test

class NodeToolchainLayoutTest {

  @Test
  fun `the root project owns the single toolchain installation`() {
    assertThat(NodeToolchainLayout.ownsInstallation(GradleProjectPath(":"))).isTrue()
  }

  @Test
  fun `every other project consumes the root installation instead of creating its own`() {
    assertThat(NodeToolchainLayout.ownsInstallation(GradleProjectPath(":internal:patterns:admin-ui"))).isFalse()
    assertThat(NodeToolchainLayout.ownsInstallation(GradleProjectPath(":internal:open:commons:typescript:typescript-react-ui"))).isFalse()
    assertThat(NodeToolchainLayout.ownsInstallation(GradleProjectPath(":internal:closed:elektromeister:elektromeister-ui"))).isFalse()
  }

  /**
   * The node distribution directory is duplicated in the CI cache configuration — `gitlab-ci.d/mr.yml`
   * and `gitlab-ci.d/main.yml` both list `.gradle/node-gradle` under `cache.paths`, and
   * `gitlab-ci.d/mr.yml` reports its size in `after_script`. Changing the constant without changing
   * those three places turns every CI job into a cache miss that silently re-downloads Node, so the
   * value is pinned here.
   */
  @Test
  fun `the node distribution lives in the CI-cached gradle node-gradle directory`() {
    assertThat(NodeToolchainLayout.NodeDistributionPath).isEqualTo(".gradle/node-gradle/nodejs")
  }

  /**
   * The pnpm, npm and yarn installs sit under the owner's build directory, so `clean` removes them
   * while the CI-cached node distribution under `.gradle/` survives.
   */
  @Test
  fun `the package manager installs live under the owner build directory`() {
    assertThat(NodeToolchainLayout.pnpmInstallPath).isEqualTo("node/pnpm")
    assertThat(NodeToolchainLayout.npmInstallPath).isEqualTo("node/npm")
    assertThat(NodeToolchainLayout.YarnInstallPath).isEqualTo("node/yarn")
  }

  @Test
  fun `clean of the owner keeps the Gradle home of CI with the node distribution`() {
    assertThat(NodeToolchainLayout.cleanPaths(GradleProjectPath(":"))).isEqualTo(listOf("dist", "build", "node_modules", ".astro"))
  }

  @Test
  fun `clean of a pnpm module deletes its own gradle directory`() {
    assertThat(NodeToolchainLayout.cleanPaths(GradleProjectPath(":internal:patterns:admin-ui")))
      .isEqualTo(listOf("dist", "build", "node_modules", ".astro", ".gradle"))
  }
}
