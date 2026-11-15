package it.neckar.gradle.ksp.generating.ts

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.junit.jupiter.api.Test

/**
 * Guards the input/output contract of [CollectTypescriptDeclarationFilesFromDependenciesTask].
 *
 * The task once declared `targetDirectory` as its only property and resolved the dependencies'
 * declaration files at execution time. With no inputs, Gradle kept it UP-TO-DATE as long as the
 * output directory was untouched, so a changed `.d.ts` in a dependency (meistercharts) never reached
 * the consuming project: `dependsOn` orders execution, it does not track content.
 */
class CollectTypescriptDeclarationFilesFromDependenciesTaskTest {
  @Test
  fun `the dependency declaration files are a tracked input`() {
    val getter = CollectTypescriptDeclarationFilesFromDependenciesTask::class.java
      .getDeclaredMethod("getSourceDirectories")

    assertThat(getter.getAnnotation(InputFiles::class.java), "getSourceDirectories is @InputFiles").isNotNull()
  }

  /**
   * The task is not cacheable, so this is about the up-to-date fingerprint, not about cache entries:
   * declaring the normalization explicitly keeps it independent of the checkout location, and keeps
   * the contract correct should the task ever become cacheable.
   */
  @Test
  fun `the dependency declaration files are tracked by relative path`() {
    val pathSensitive = CollectTypescriptDeclarationFilesFromDependenciesTask::class.java
      .getDeclaredMethod("getSourceDirectories")
      .getAnnotation(PathSensitive::class.java)

    assertThat(pathSensitive, "getSourceDirectories declares @PathSensitive").isNotNull()
    assertThat(pathSensitive.value, "getSourceDirectories path sensitivity").isEqualTo(PathSensitivity.RELATIVE)
  }

  @Test
  fun `the target directory is the output`() {
    val getter = CollectTypescriptDeclarationFilesFromDependenciesTask::class.java
      .getDeclaredMethod("getTargetDirectory")

    assertThat(getter.getAnnotation(OutputDirectory::class.java), "getTargetDirectory is @OutputDirectory").isNotNull()
  }
}
