package it.neckar.gradle.ksp.generating.types.list.collection

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.junit.jupiter.api.Test

/**
 * Guards the input/output contract of the types-list collector tasks.
 *
 * The tasks once declared `collectedTypeListsDirectory` as their only property and resolved the
 * sources' types-list JSON directories at execution time. With no inputs, Gradle kept them
 * UP-TO-DATE as long as the output directory was untouched, so a changed set of exported types in a
 * dependency/subproject never reached the merged collection: `dependsOn` orders execution, it does
 * not track content. `sourceDirectories` must stay a tracked `@InputFiles`.
 */
class AbstractCollectTypesListTaskTest {
  @Test
  fun `the source directories are a tracked input`() {
    val getter = AbstractCollectTypesListTask::class.java.getDeclaredMethod("getSourceDirectories")
    assertThat(getter.getAnnotation(InputFiles::class.java), "getSourceDirectories is @InputFiles").isNotNull()
  }

  @Test
  fun `the source directories are tracked by relative path`() {
    val pathSensitive = AbstractCollectTypesListTask::class.java
      .getDeclaredMethod("getSourceDirectories")
      .getAnnotation(PathSensitive::class.java)

    assertThat(pathSensitive, "getSourceDirectories declares @PathSensitive").isNotNull()
    assertThat(pathSensitive.value, "getSourceDirectories path sensitivity").isEqualTo(PathSensitivity.RELATIVE)
  }

  @Test
  fun `the collected directory is the output`() {
    val getter = AbstractCollectTypesListTask::class.java.getDeclaredMethod("getCollectedTypeListsDirectory")
    assertThat(getter.getAnnotation(OutputDirectory::class.java), "getCollectedTypeListsDirectory is @OutputDirectory").isNotNull()
  }
}
