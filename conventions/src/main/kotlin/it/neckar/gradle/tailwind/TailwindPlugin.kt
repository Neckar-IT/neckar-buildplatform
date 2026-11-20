package it.neckar.gradle.tailwind

import com.github.gradle.node.pnpm.task.PnpmInstallTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

/**
 * Convention plugin that builds CSS with the TailwindCSS CLI of the root pnpm workspace.
 *
 * Registers `tailwindCompile`, which runs the CLI on the input and output CSS of the [TailwindPluginExtension] (`tailwind {}`).
 */
class TailwindPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    val extension: TailwindPluginExtension = target.extensions.create<TailwindPluginExtension>(ExtensionName).apply {
      input.convention(target.layout.projectDirectory.file("src/jsMain/tailwind/tailwind.input.css"))
      output.convention(target.layout.projectDirectory.file("src/jsMain/resources/css/tailwind.css"))
    }

    target.tasks.register<TailwindCompileTask>(TailwindCompileTaskName) {
      group = TaskGroup
      description = "Compiles CSS with the TailwindCSS CLI."

      dependsOn(":${PnpmInstallTask.NAME}")

      executable.set(target.rootDir.resolve("node_modules/.bin/tailwindcss"))
      input = extension.input
      output = extension.output
      projectDirectory = target.layout.projectDirectory
    }
  }

  companion object {
    const val TaskGroup: String = "Tailwind"
    const val ExtensionName: String = "tailwind"
    const val TailwindCompileTaskName: String = "tailwindCompile"
  }
}
