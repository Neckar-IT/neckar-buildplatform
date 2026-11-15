package it.neckar.gradle

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsAtLeast
import assertk.assertions.doesNotContain
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.kotlin.dsl.apply
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LinuxX64TargetTest {
  @TempDir
  lateinit var projectDir: File

  @Test
  fun `assemble links debug and release without skipNativeReleaseLink`() {
    val project = projectWithExecutable(skipNativeReleaseLink = false)

    assertThat(project.assembleDependencyNames()).containsAtLeast(DebugLinkTaskName, ReleaseLinkTaskName)
  }

  @Test
  fun `assemble links only debug with skipNativeReleaseLink`() {
    val project = projectWithExecutable(skipNativeReleaseLink = true)

    val assembleDependencyNames = project.assembleDependencyNames()
    assertThat(assembleDependencyNames).contains(DebugLinkTaskName)
    assertThat(assembleDependencyNames).doesNotContain(ReleaseLinkTaskName)
  }

  /** A start script builds the binary through the release link task by name. */
  @Test
  fun `the release link task stays registered with skipNativeReleaseLink`() {
    val project = projectWithExecutable(skipNativeReleaseLink = true)

    assertThat(project.tasks.names).contains(ReleaseLinkTaskName)
  }

  private fun projectWithExecutable(skipNativeReleaseLink: Boolean): Project {
    val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
    if (skipNativeReleaseLink) {
      project.extensions.extraProperties[SkipNativeReleaseLinkProperty] = ""
    }
    project.apply(plugin = BuildplatformPlugins.KotlinMultiPlatform)

    val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
    kotlin.declareLinuxX64Target(project)
    kotlin.linuxX64 { binaries.executable() }
    // The native toolchain configuration, which resolving a link task's dependencies needs, exists only after evaluation.
    (project as ProjectInternal).evaluate()

    return project
  }

  /** Every task `assemble` reaches, so a release link pulled in through another task shows too. */
  private fun Project.assembleDependencyNames(): Set<String> {
    val reached = mutableSetOf<Task>()
    val pending = ArrayDeque(listOf(tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME)))
    while (pending.isNotEmpty()) {
      pending.removeFirst().taskDependencies.getDependencies(null).filter { reached.add(it) }.forEach { pending.addLast(it) }
    }
    return reached.map { it.name }.toSet()
  }

  companion object {
    private const val DebugLinkTaskName = "linkDebugExecutableLinuxX64"
    private const val ReleaseLinkTaskName = "linkReleaseExecutableLinuxX64"
  }
}
