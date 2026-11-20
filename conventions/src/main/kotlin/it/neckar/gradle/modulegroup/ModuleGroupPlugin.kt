package it.neckar.gradle.modulegroup

import it.neckar.gradle.CheckTaskName
import it.neckar.gradle.VerificationGroup
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePlugin
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.register

/**
 * [VerifyTaskName] at `check`: every direct module carries [ModuleGroupPrefix.fromDirectoryName], so a module
 * read outside its directory (Gradle path, Maven coordinate, IDE module list) still names its product.
 */
class ModuleGroupPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.pluginManager.apply(BasePlugin::class.java)

    val verifyTask = target.tasks.register<VerifyModuleGroupTask>(VerifyTaskName) {
      group = VerificationGroup
      description = "Verify every direct module of ${target.path} carries the prefix of the directory name"
      groupPath = target.path
      prefix = ModuleGroupPrefix.fromDirectoryName(target.projectDir.name).value
      moduleNames = target.childProjects.keys.sorted()
      stamp = target.layout.buildDirectory.file("verify/module-group.stamp")
    }
    target.tasks.named(CheckTaskName).configure { dependsOn(verifyTask) }
  }

  companion object {
    const val VerifyTaskName: String = "verifyModuleGroup"
  }
}
