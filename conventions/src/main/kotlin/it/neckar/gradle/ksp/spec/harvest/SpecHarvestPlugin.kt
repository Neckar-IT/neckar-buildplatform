package it.neckar.gradle.ksp.spec.harvest

import it.neckar.projects.common.MavenCoordinate
import com.google.devtools.ksp.gradle.KspExtension
import it.neckar.gradle.isMultiplatform
import it.neckar.gradle.ksp.BaseKspPlugin
import it.neckar.gradle.ksp.dependOnKotlinCompileTasks
import it.neckar.gradle.ksp.dependOnKotlinCompileTestTasks
import kotlinx.serialization.ExperimentalSerializationApi
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import it.neckar.gradle.pathAsBaseFileName


@ExperimentalSerializationApi
class SpecHarvestPlugin : BaseKspPlugin() {
  override val kspProcessor: MavenCoordinate = MavenCoordinate("it.neckar.open.ksp", "spec-harvest")

  override fun applyCustom(target: Project, kspExtension: KspExtension, configurations: List<Configuration>) {
    super.applyCustom(target, kspExtension, configurations)

    target.extensions.create<SpecHarvestPluginExtension>(ExtensionName)

    kspExtension.arg {
      listOf(
      )
    }

    val harvestedSpecDirSingleResults: Provider<Directory> = if (target.isMultiplatform()) {
      target.layout.buildDirectory.dir("generated/ksp/jvm/jvmMain/resources/harvested-specs/")
    } else {
      target.layout.buildDirectory.dir("generated/ksp/main/resources/harvested-specs/")
    }

    val mergedSpecDir = target.layout.buildDirectory.dir("generated/harvested-spec/main/resources")


    val harvestedSpecDirSingelResultsTest: Provider<Directory> = if (target.isMultiplatform()) {
      target.layout.buildDirectory.dir("generated/ksp/jvm/jvmTest/resources/harvested-specs/")
    } else {
      target.layout.buildDirectory.dir("generated/ksp/test/resources/harvested-specs/")
    }

    val mergedSpecDirTest = target.layout.buildDirectory.dir("generated/harvested-spec/test/resources")


    val mergeSpecHarvestResultsTask = target.tasks.register<MergeSpecHarvestResultTask>(MergeSpecHarvestResultsTaskName) {
      group = "ksp"
      description = "Merges the harvested specification files from all KSP runs into a single file"

      singleResultsHarvestFiles.from(target.objects.fileCollection().from(harvestedSpecDirSingleResults))
      mergedResultsFile = mergedSpecDir.map { it.file("META-INF/harvested-specs/${project.pathAsBaseFileName()}.json") }

    }.also {
      it.dependOnKotlinCompileTasks(target)

      target.tasks.findByName("processResources")?.dependsOn(it)
      target.tasks.findByName("jvmProcessResources")?.dependsOn(it)
    }

    val mergeSpecHarvestResultsTestsTask = target.tasks.register<MergeSpecHarvestResultTask>(MergeSpecHarvestResultsTestsTaskName) {
      group = "ksp"
      description = "Merges the harvested specification files from all KSP runs into a single file"

      singleResultsHarvestFiles.from(target.objects.fileCollection().from(harvestedSpecDirSingelResultsTest))
      mergedResultsFile = mergedSpecDirTest.map { it.file("META-INF/harvested-specs/${project.pathAsBaseFileName()}-test.json") }
    }.also {
      it.dependOnKotlinCompileTestTasks(target)

      target.tasks.findByName("processTestResources")?.dependsOn(it)
      target.tasks.findByName("jvmTestProcessResources")?.dependsOn(it)
    }

    target.tasks.getByName("build").dependsOn(mergeSpecHarvestResultsTask, mergeSpecHarvestResultsTestsTask)


    //Add source dir
    val sourceSetJvm = target.kotlinExtension.sourceSets.findByName("jvmMain")
      ?: target.kotlinExtension.sourceSets.findByName("main")
      ?: throw IllegalStateException("Could not find source set 'jvmMain' or 'main'")
    sourceSetJvm.resources.srcDir(mergedSpecDir)

    val sourceSetJvmTest = target.kotlinExtension.sourceSets.findByName("jvmTest")
      ?: target.kotlinExtension.sourceSets.findByName("test")
      ?: throw IllegalStateException("Could not find source set 'jvmTest' or 'test'")
    sourceSetJvmTest.resources.srcDir(mergedSpecDirTest)
  }

  companion object {
    const val ExtensionName: String = "specHarvest"

    const val MergeSpecHarvestResultsTaskName: String = "mergeSpecHarvestResults"
    const val MergeSpecHarvestResultsTestsTaskName: String = "mergeSpecHarvestResultsTests"

    fun harvestedSpecificationFileName(project: Project): String = "${project.pathAsBaseFileName()}-harvested-specifications.json"
  }
}
