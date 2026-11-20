package it.neckar.projects

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ConfiguredProjectTest {
  @TempDir
  lateinit var projectDir: File

  @Test
  fun `task builds a fully qualified task path`() {
    assertThat(ModuleRegistry().group.module.task("koverHtmlReport")).isEqualTo(":test:module:koverHtmlReport")
  }

  @Test
  fun `buildTask is the build task of this project`() {
    assertThat(ModuleRegistry().group.module.buildTask).isEqualTo(":test:module:build")
  }

  @Test
  fun `toString returns the gradle path`() {
    assertThat(ModuleRegistry().group.module.toString()).isEqualTo(":test:module")
  }

  @Test
  fun `the path joins the relative paths of every ancestor`() {
    val module = ModuleRegistry().group.module

    assertThat(module.relativePath).isEqualTo(GradleProjectPath(":module"))
    assertThat(module.path).isEqualTo(GradleProjectPath(":test:module"))
  }

  @Test
  fun `task of a project at the root path has a single colon`() {
    val rootGroup = RootGroupRegistry().group

    assertThat(rootGroup.task("build")).isEqualTo(":build")
    assertThat(rootGroup.buildTask).isEqualTo(":build")
  }

  @Test
  fun `getProject resolves the gradle project through the given resolver`() {
    val root = ProjectBuilder.builder().withName("root").withProjectDir(projectDir).build()
    val intermediate = ProjectBuilder.builder().withName("test").withParent(root).build()
    ProjectBuilder.builder().withName("module").withParent(intermediate).build()

    assertThat(ModuleRegistry().group.module.getProject(root).path).isEqualTo(":test:module")
  }
}

private class ModuleRegistry : ProjectRoot() {
  val group: Group = Group(this, GradleProjectPath(":test"))
}

private class RootGroupRegistry : ProjectRoot() {
  val group: Group = Group(this, GradleProjectPath.Root)
}

private class Group(parent: ConfiguredProject, relativePath: GradleProjectPath) : ConfiguredProject(parent, relativePath, ProjectType.Intermediate, ProjectRole.Container) {
  val module: ConfiguredProject = jvm("module", ProjectRole.Library)
}
