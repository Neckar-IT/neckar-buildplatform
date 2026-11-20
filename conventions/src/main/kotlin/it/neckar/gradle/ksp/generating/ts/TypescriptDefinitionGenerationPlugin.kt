package it.neckar.gradle.ksp.generating.ts

import com.google.devtools.ksp.gradle.KspExtension
import it.neckar.gradle.BuildplatformPlugins
import it.neckar.gradle.findAllProjectDependencies
import it.neckar.gradle.ksp.generating.types.list.collection.TypesListCollectorPlugin
import it.neckar.gradle.ksp.options.KspPluginOptions
import it.neckar.gradle.neckarOpenNotation
import it.neckar.gradle.pathAsBaseFileName
import it.neckar.gradle.requireDirExists
import it.neckar.projects.common.MavenCoordinate
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.register

/**
 * Registers the dependency to the TypeScript Definition Generator
 *
 * Attention: This plugin automatically applies the plugin `it.neckar.gradle.ksp.generating.types.list.TypesListGeneratorPlugin`
 *
 * Use [it.neckar.gradle.pnpm.interop.PnpmKotlinInteropPlugin] in pnpm projects to use Kotlin files from TypeScript
 */
class TypescriptDefinitionGenerationPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    val extension = target.extensions.create<GenerateTypeScriptDefinitionsExtension>(ExtensionName).apply {
      //The base file name for the TypeScript definition file - without the extension
      val baseFileName = target.pathAsBaseFileName()

      targetTypescriptDefinitionFileName.convention("$baseFileName.d.ts")

      projectTypeScriptFilesDirectory.convention(target.layout.buildDirectory.dir("typescript-definition-generation/jsMain"))

      collectedTypescriptDeclarationFilesFromDependenciesDirectory.convention(target.layout.buildDirectory.dir("typescript-definition-generation/modules/collected"))
      predefinedDeclarationFilesDirectory.convention(target.layout.buildDirectory.dir("typescript-definition-generation/predefined"))
    }

    //
    //
    // KSP Configuration
    //
    //

    //Ensure that the it.neckar.gradle.ksp.generating.types.list.TypesListGeneratorPlugin is applied
    //This plugin creates the list of types that are used in the project
    target.plugins.apply(BuildplatformPlugins.GenerateTypesList)
    //Collects the types-list from all dependencies
    target.plugins.apply(BuildplatformPlugins.TypesListCollector)

    //Apply the KSP plugin
    target.plugins.apply(BuildplatformPlugins.Ksp)

    val kspJsConfigurations = target.configurations
      .filter { it.name == "kspJs" }

    if (kspJsConfigurations.isEmpty()) {
      throw IllegalStateException("No [kspJs] configuration found for ${target.name}.")
    }

    kspJsConfigurations
      .forEach {
        val dependencies = target.dependencies
        dependencies.add(it.name, target.neckarOpenNotation(TsDeclarationGenerator))
      }

    target.extensions.findByType<KspExtension>()?.let { kspExtension ->
      //The common arguments come from the TypesListGeneratorPlugin applied above (a BaseKspPlugin)
      val typeScriptDefinitionFileName: Property<String> = extension.targetTypescriptDefinitionFileName
      val typesCollectionJson: Provider<RegularFile> = target.layout.buildDirectory.file(TypesListCollectorPlugin.Paths.TypesCollectionJson)

      kspExtension.arg {
        listOf(
          //The file name for the TypeScript definition (usually `index.d.ts`)
          //Is retrieved by [it.neckar.ksp.ts.TypeScriptDefinitionGenerationProcessor]
          KspPluginOptions.TypeScriptDefinitionGeneration.TypeScriptDefinitionFileName.withValue(typeScriptDefinitionFileName.get()),
          KspPluginOptions.TypeScriptDefinitionGeneration.TypesListForProjectCollectionFilePath.withValue(typesCollectionJson.get().asFile.absolutePath),
        )
      }
    }


    //The KSP tasks are generated late
    target.tasks.whenTaskAdded {
      if (name.startsWith("kspKotlin")) {
        dependsOn(TypesListCollectorPlugin.MergeTypesListTaskName)
      }
    }
    target.tasks.filter { it.name.startsWith("kspKotlin") }.forEach {
      it.dependsOn(TypesListCollectorPlugin.MergeTypesListTaskName)
    }


    //
    //
    // Tasks
    //
    //
    val kspGeneratedParentDirProvider = target.provider { target.layout.buildDirectory.dir("generated/ksp/js/jsMain/resources/").get() }

    val validateKspGeneratesSomethingTask = target.tasks.register(ValidateKspGeneratesSomethingTaskName) {
      dependsOn("jsMainClasses")

      doLast {
        val dir = kspGeneratedParentDirProvider.get().asFile

        dir.requireDirExists { file ->
          "The KSP generated directory is not a directory: ${file?.absolutePath}. Make sure there are source files that are marked with @TsExport."
        }
      }
    }

    target.tasks.register<ProvideTypescriptDeclarationFilesTask>(ProvideTypescriptDeclarationFileTaskName) {
      group = "TypeScript"
      description = "Provides the TypeScript and TypeScript declaration file"

      kspGeneratedParentDir.set(
        kspGeneratedParentDirProvider
      )

      targetDirectory = extension.projectTypeScriptFilesDirectory

      dependsOn(validateKspGeneratesSomethingTask, "jsMainClasses") //KSP plugin generates the file on compilation
    }

    /**
     * Copy all the TypeScript declaration files of all dependencies to a target directory.
     *
     * Registered without a lifecycle hook. The only consumer, [it.neckar.gradle.pnpm.interop.PnpmKotlinInteropPlugin],
     * declares its own dependency on this task, so it runs on demand only for projects whose declaration files are
     * actually consumed - projects without a consumer no longer copy these files on every `build`.
     */
    target.tasks.register<CollectTypescriptDeclarationFilesFromDependenciesTask>(CollectTypescriptDeclarationFilesFromDependenciesTaskTaskName) {
      group = "TypeScript"
      description = "Copy all the TypeScript declaration files of dependencies to a target directory"

      targetDirectory = extension.collectedTypescriptDeclarationFilesFromDependenciesDirectory

      // Declared as an input - not merely as a `dependsOn` - so a changed declaration file in a
      // dependency actually invalidates this task instead of leaving it UP-TO-DATE with a stale copy.
      // Each directory is the `@OutputDirectory` of the dependency's task, which carries the task
      // dependency along with it.
      sourceDirectories.from(
        project.provider {
          target.findAllProjectDependencies(listOf("jsRuntimeClasspath")).mapNotNull { dependency ->
            val dependencyProvideTask = dependency.tasks.findByName(ProvideTypescriptDeclarationFileTaskName) as? ProvideTypescriptDeclarationFilesTask

            return@mapNotNull if (dependencyProvideTask != null) {
              this.logger.info("Added files from: ${dependencyProvideTask.targetDirectory.get().asFile}")
              dependencyProvideTask.targetDirectory
            } else {
              this.logger.info("No task found in dependency ${dependency.path}")
              null
            }
          }
        }
      )
    }

    target.tasks.register<ProvidePredefinedTypeScriptFilesTask>(ProvidePredefinedTypeScriptDeclarationFilesTaskName) {
      group = "TypeScript"
      description = "Provides the predefined TypeScript declaration files"

      targetDirectory = extension.predefinedDeclarationFilesDirectory
    }
  }

  companion object {
    const val ExtensionName: String = "createTypeScriptDefinitionsExtension"

    const val ValidateKspGeneratesSomethingTaskName: String = "validateKspGeneratesSomething"
    const val ProvideTypescriptDeclarationFileTaskName: String = "provideTypescriptDeclarationFiles"
    const val CollectTypescriptDeclarationFilesFromDependenciesTaskTaskName: String = "collectTypescriptDeclarationFilesFromDependencies"
    const val ProvidePredefinedTypeScriptDeclarationFilesTaskName: String = "providePredefinedTypeScriptDeclarationFiles"
  }
}

/**
 * The extension
 */
interface GenerateTypeScriptDefinitionsExtension {
  /**
   * The file name for the TypeScript definition that is generated - for this project.
   * Will be placed automatically in the KSP generation directory (`build/generated/ksp/js/jsMain/resources`) and copied to the target directory
   */
  val targetTypescriptDefinitionFileName: Property<String>

  /**
   * Where to save the d.ts files for further usage.
   *
   * The target directory will contain files generated by the KSP processor:
   * - The TypeScript definition file
   * - Tye TypeScript files (e.g., for objects)
   */
  val projectTypeScriptFilesDirectory: DirectoryProperty

  /**
   * Where to place the predefined declaration files
   */
  val predefinedDeclarationFilesDirectory: DirectoryProperty

  /**
   * Where to copy the TypeScript files.
   * They are placed in this directory before merging them into a single file
   */
  val collectedTypescriptDeclarationFilesFromDependenciesDirectory: DirectoryProperty
}


/**
 * Configure the TypeScript definitions plugin
 */
fun Project.generateTypeScriptDefinitions(configure: GenerateTypeScriptDefinitionsExtension.() -> Unit): Unit =
  (this as org.gradle.api.plugins.ExtensionAware).extensions.configure(TypescriptDefinitionGenerationPlugin.ExtensionName, configure)

/** The KSP processor writing the TypeScript declarations; the root build resolves it to its project. */
private val TsDeclarationGenerator: MavenCoordinate = MavenCoordinate("it.neckar.open.ksp", "ts-declaration-generator")
