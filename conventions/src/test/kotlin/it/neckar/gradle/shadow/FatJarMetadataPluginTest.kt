package it.neckar.gradle.shadow

import assertk.assertThat
import assertk.assertions.contains
import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import it.neckar.gradle.messageChain
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Copy
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * A fat jar is packed from the module's resources output directory, and Gradle deletes nothing a
 * second task leaves there. The plugin therefore has to reject that second task while the build is
 * being configured — afterwards nothing distinguishes the leftover from a resource the module ships.
 */
class FatJarMetadataPluginTest {

  @TempDir
  lateinit var projectDir: File

  @Test
  fun `a second task writing into the resources output is rejected`() {
    val project = fatJarProject()
    project.tasks.register("copyFrontendDistribution", Copy::class.java).configure {
      from(project.projectDir)
      into(project.layout.buildDirectory.dir("resources/main/assets"))
    }

    val failure = assertThrows<Exception> { (project as ProjectInternal).evaluate() }

    assertThat(failure.messageChain()).contains("copyFrontendDistribution")
  }

  @Test
  fun `a task writing into a directory of its own is fine`() {
    val project = fatJarProject()
    project.tasks.register("copyFrontendDistribution", Copy::class.java).configure {
      from(project.projectDir)
      into(project.layout.buildDirectory.dir("frontend-distribution"))
    }

    (project as ProjectInternal).evaluate()
  }

  private fun fatJarProject(): Project {
    return ProjectBuilder.builder().withProjectDir(projectDir).build().also { project ->
      project.plugins.apply(JavaPlugin::class.java)
      project.plugins.apply(ShadowPlugin::class.java)
      project.plugins.apply(FatJarMetadataPlugin::class.java)
    }
  }
}
