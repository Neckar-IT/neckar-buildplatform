package it.neckar.gradle.modulegroup

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import it.neckar.gradle.CheckTaskName
import it.neckar.projects.GradleProjectPath
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.named
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ModuleGroupPluginTest {
  @TempDir
  lateinit var repositoryRoot: File

  @Test
  fun `a module carrying the prefix passes`() {
    assertThat(moduleGroupProblems(GradleProjectPath(":internal:closed:elektromeister"), ModuleGroupPrefix("elektromeister"), listOf("elektromeister-service", "elektromeister-ui"))).isEmpty()
  }

  @Test
  fun `a module without the prefix fails`() {
    assertThat(moduleGroupProblems(GradleProjectPath(":internal:closed:elektromeister"), ModuleGroupPrefix("elektromeister"), listOf("elektromeister-service", "pdf-layout")).map { it.substringBefore(" lacks") })
      .containsExactly(":internal:closed:elektromeister:pdf-layout")
  }

  /** The service rule of `internal/closed/CLAUDE.md` names a deployed module after its domain. */
  @Test
  fun `a module named after its domain is exempt`() {
    assertThat(moduleGroupProblems(GradleProjectPath(":internal:closed:auto-merge.neckar.it"), ModuleGroupPrefix("auto-merge"), listOf("auto-merge-service", "auto-merge.dev"))).isEmpty()
  }

  @Test
  fun `a group without any prefixed module fails`() {
    assertThat(moduleGroupProblems(GradleProjectPath(":internal:closed:auto-merge.neckar.it"), ModuleGroupPrefix("auto-merge"), listOf("auto-merge.dev")))
      .containsExactly("no module of :internal:closed:auto-merge.neckar.it carries the prefix 'auto-merge-'")
  }

  @Test
  fun `the task reads the prefix from the directory name and the direct modules of the group`() {
    val group = groupProject("klk-steuerberatung.de", "klk-steuerberatung-ui", "klk-steuerberatung-service")
    val task = group.tasks.named<VerifyModuleGroupTask>(ModuleGroupPlugin.VerifyTaskName).get()

    assertThat(task.prefix.get()).isEqualTo("klk-steuerberatung")
    assertThat(task.moduleNames.get()).containsExactly("klk-steuerberatung-service", "klk-steuerberatung-ui")
  }

  @Test
  fun `check depends on verifyModuleGroup`() {
    val group = groupProject("elektromeister", "elektromeister-service")
    val check = group.tasks.getByName(CheckTaskName)

    assertThat(check.taskDependencies.getDependencies(check).map { it.name }.filter { it == ModuleGroupPlugin.VerifyTaskName }).containsExactly(ModuleGroupPlugin.VerifyTaskName)
  }

  private fun groupProject(directoryName: String, vararg moduleNames: String): Project {
    val root = ProjectBuilder.builder().withProjectDir(repositoryRoot).build()
    val group = ProjectBuilder.builder().withName(directoryName).withParent(root).withProjectDir(repositoryRoot.resolve(directoryName)).build()
    moduleNames.forEach { name -> ProjectBuilder.builder().withName(name).withParent(group).withProjectDir(repositoryRoot.resolve("$directoryName/$name")).build() }
    group.apply(plugin = "it.neckar.module-group")
    return group
  }
}
