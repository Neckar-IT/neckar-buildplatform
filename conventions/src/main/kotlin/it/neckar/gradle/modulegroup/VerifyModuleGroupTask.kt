package it.neckar.gradle.modulegroup

import it.neckar.projects.GradleProjectPath
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Fails on a direct module without [prefix] and on a group no module of which carries it. A dotted module name
 * (`auto-merge.dev`) is exempt: it follows the domain naming of `internal/closed/CLAUDE.md`.
 */
@CacheableTask
abstract class VerifyModuleGroupTask : DefaultTask() {
  /**
   * The Gradle path of the group, e.g. `:internal:closed:turboangebot.de`.
   */
  @get:Input
  abstract val groupPath: Property<String>

  /**
   * See [ModuleGroupPrefix].
   */
  @get:Input
  abstract val prefix: Property<String>

  /**
   * The names of the direct modules of the group.
   */
  @get:Input
  abstract val moduleNames: ListProperty<String>

  @get:OutputFile
  abstract val stamp: RegularFileProperty

  @TaskAction
  fun verify() {
    val problems = moduleGroupProblems(GradleProjectPath(groupPath.get()), ModuleGroupPrefix(prefix.get()), moduleNames.get())
    if (problems.isNotEmpty()) {
      throw GradleException("Module group ${groupPath.get()} is inconsistent:\n" + problems.joinToString("\n") { "  - $it" })
    }
    stamp.get().asFile.writeText("verified ${moduleNames.get().size} modules\n")
  }
}

/**
 * One line per module of [moduleNames] in the group [groupPath] without the [prefix], plus one when no
 * module carries it; the group would then hold a rule nothing follows.
 */
internal fun moduleGroupProblems(groupPath: GradleProjectPath, prefix: ModuleGroupPrefix, moduleNames: List<String>): List<String> {
  val prefixed: (String) -> Boolean = { name -> name.startsWith("$prefix-") }
  return buildList {
    moduleNames.filter { name -> name.contains('.').not() && prefixed(name).not() }.sorted().forEach { name ->
      add("$groupPath:$name lacks the prefix '$prefix-': rename it to '$prefix-<what it is>', a role where the module has one (service, ui, rest-api, model, common, cli, deployment), otherwise its subject (pdf-layout, sidebar-plugin, canvas)")
    }
    if (moduleNames.none(prefixed)) {
      add("no module of $groupPath carries the prefix '$prefix-'")
    }
  }
}
