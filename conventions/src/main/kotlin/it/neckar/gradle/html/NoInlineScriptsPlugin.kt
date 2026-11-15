package it.neckar.gradle.html

import it.neckar.gradle.CheckTaskName
import it.neckar.gradle.BuildplatformPlugins
import it.neckar.gradle.VerificationGroup
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import java.io.File

/**
 * Configures [VerifyNoInlineScriptsTask] for the project the plugin is applied to.
 */
interface NoInlineScriptsExtension {
  /**
   * The HTML documents that keep the rule: a directory, or a task whose output holds them and whose dependency comes along.
   * A frontend project sets it through `frontendProject { output }`, which also brings the output of `pnpmRunBuild`.
   */
  val documents: ConfigurableFileCollection

  /**
   * Ant patterns of documents the rule does not judge, relative to what [documents] names. For
   * markup this project hosts but does not author; the reason belongs in a comment above the entry.
   */
  val excludes: SetProperty<String>
}

/**
 * Registers `verifyNoInlineScripts`: under `script-src 'self'` an inline `<script>` never runs, and nothing reports it.
 * A block passes only when the document's own `<meta>` policy names its hash, as Astro writes it for the blocks it emits.
 *
 * Applied through `frontendProject`, which names the documents the project deploys; which of them
 * it authors stays the project's call in [NoInlineScriptsExtension.excludes].
 */
class NoInlineScriptsPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.plugins.apply(BuildplatformPlugins.Base)

    val extension = target.extensions.create<NoInlineScriptsExtension>(ExtensionName)

    target.tasks.register<VerifyNoInlineScriptsTask>(VerifyTaskName) {
      group = VerificationGroup
      description = "Every HTML document this project deploys loads its scripts from a file, or names the hash of an inline block in its own policy"

      // asFileTree keeps the build dependency of a task output, so pointing `documents` at a
      // bundler task is enough to have the built documents judged rather than the sources.
      documents.setFrom(extension.documents.asFileTree.matching { include(htmlPattern) })
      excludes = extension.excludes
      projectPath = target.path
      projectDirectory = target.layout.projectDirectory
      marker = target.layout.buildDirectory.file("$VerifyTaskName/marker.txt")
    }

    target.tasks.named(CheckTaskName) {
      dependsOn(VerifyTaskName)
    }
  }

  companion object {
    const val ExtensionName: String = "noInlineScripts"
    const val VerifyTaskName: String = "verifyNoInlineScripts"
  }
}

/** See [NoInlineScriptsPlugin]. */
abstract class VerifyNoInlineScriptsTask : DefaultTask() {
  /** The project's HTML documents, before [excludes] is applied. */
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val documents: ConfigurableFileCollection

  @get:Input
  abstract val excludes: SetProperty<String>

  /** Names the project in every message — read here rather than from `project`, which the task action must not touch. */
  @get:Internal
  abstract val projectPath: Property<String>

  /** What the reported paths are relative to. */
  @get:Internal
  abstract val projectDirectory: DirectoryProperty

  @get:OutputFile
  abstract val marker: RegularFileProperty

  @TaskAction
  fun check() {
    val excludePatterns: Set<String> = excludes.get()
    val allDocuments = documents.asFileTree

    // An empty set would report the project clean without having read a document — a `documents`
    // pointing at the wrong directory, or a bundler task that produced nothing.
    check(allDocuments.isEmpty.not()) {
      "${projectPath.get()}: ${NoInlineScriptsPlugin.ExtensionName}.documents names no HTML document to judge"
    }

    // A pattern that matches nothing outlives the document it was written for and silently covers
    // whichever one is added under that path next.
    val staleExcludes: List<String> = excludePatterns.filter { pattern ->
      allDocuments.matching { include(pattern) }.isEmpty
    }
    if (staleExcludes.isNotEmpty()) {
      throw GradleException(
        buildString {
          appendLine("${projectPath.get()}: ${NoInlineScriptsPlugin.ExtensionName}.excludes names patterns that match no document:")
          appendLine()
          staleExcludes.sorted().forEach { appendLine("  $it") }
          appendLine()
          appendLine("Remove them, or correct them to the document they were written for.")
        },
      )
    }

    val judgedDocuments: List<File> = allDocuments.matching { exclude(excludePatterns) }.files.sorted()

    val problems: List<String> = judgedDocuments.flatMap { document ->
      InlineScriptScanner.scan(HtmlDocument.read(document)).blockedInlineScripts()
        .map { script -> "  ${document.relativeToProject()}:${script.lineNumber}: <script> carrying code instead of a src" }
    }

    if (problems.isNotEmpty()) {
      throw GradleException(
        buildString {
          appendLine("${projectPath.get()}: these deployed documents carry script code:")
          appendLine()
          problems.forEach { appendLine(it) }
          appendLine()
          appendLine("Load it from a file instead — a sibling in public/ for a public/ document,")
          appendLine("otherwise an asset from a Vite plugin. One that emits such an asset:")
          appendLine("internal/open/config/typescript/vite-config/src/theme-init/theme-init-plugin.ts")
          appendLine("A block a framework emits and the project cannot move passes when the document's")
          appendLine("<meta http-equiv=\"content-security-policy\"> names its hash, as Astro's security.csp writes it.")
          appendLine("Markup this project hosts but does not author belongs in ${NoInlineScriptsPlugin.ExtensionName}.excludes.")
        },
      )
    }

    marker.get().asFile.also { it.parentFile.mkdirs() }.writeText("${judgedDocuments.size}\n")
    logger.lifecycle("inline script check complete: ${judgedDocuments.size} document(s) scanned")
  }

  private fun File.relativeToProject(): String = relativeToOrSelf(projectDirectory.get().asFile).path
}

/** Only HTML carries `<script>`; whatever else a bundler emits beside it is none of the rule's business. */
private const val htmlPattern: String = "**/*.html"
